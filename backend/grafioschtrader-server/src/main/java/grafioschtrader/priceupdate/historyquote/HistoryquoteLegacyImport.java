package grafioschtrader.priceupdate.historyquote;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.common.CSVImportHelper;
import grafiosch.common.ValueFormatConverter;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.DateBusinessHelper;
import grafioschtrader.dto.SupportedCSVFormat;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.HistoryquoteLegacy;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.HistoryquoteLegacyJpaRepository;
import grafioschtrader.types.HistoryquoteCreateType;

/**
 * Round-trip counterpart of the legacy view's CSV export: imports rows into
 * {@code historyquote_legacy} from a CSV file produced by the legacy view (or hand-written
 * with the same column shape).
 *
 * <p>Expected CSV (first line is the header, separator is {@code ;}, columns may be in any
 * order; case-insensitive matching):
 * <pre>
 *   date;transferDate;close;open;high;low;volume
 *   2020-01-15;2024-03-10;150.25;148.50;151.00;148.00;1000000
 *   2020-01-16;2024-03-10;152.00;150.50;153.00;150.00;1200000
 * </pre>
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code date} (REQUIRED) — the trading date the quote belongs to.</li>
 *   <li>{@code close} (REQUIRED) — the closing price.</li>
 *   <li>{@code open}, {@code high}, {@code low}, {@code volume} — optional; missing values
 *       are stored as NULL.</li>
 *   <li>{@code transferDate} — the archival batch date; defaults to {@link LocalDate#now()}
 *       when the column is absent or the cell is blank. Determines the post-archival split
 *       factor applied at supplement time.</li>
 * </ul>
 *
 * <p>Provenance: imported rows always land in legacy with {@code create_type =
 * MANUAL_IMPORTED}. When later supplemented back into live, that origin label is preserved
 * (per {@link HistoryquoteLegacyJpaRepository#insertLegacyIntoLive insertLegacyIntoLive}).
 *
 * <p>Conflict handling: insertion is {@code INSERT IGNORE} on the unique key
 * {@code (id_securitycurrency, date)}. A row already present in the shadow keeps its
 * existing {@code transfer_date} and {@code create_type}; the importer counts that case as
 * {@code notOverridden}.
 */
public class HistoryquoteLegacyImport {

  private final HistoryquoteLegacyJpaRepository historyquoteLegacyJpaRepository;

  public HistoryquoteLegacyImport(HistoryquoteLegacyJpaRepository historyquoteLegacyJpaRepository) {
    this.historyquoteLegacyJpaRepository = historyquoteLegacyJpaRepository;
  }

  @Transactional
  @Modifying
  public UploadHistoryquotesSuccess uploadHistoryquotes(Integer idSecuritycurrency, MultipartFile[] uploadFiles,
      SupportedCSVFormat supportedCSVFormat) throws Exception {
    final UserAuditable userAuditable = historyquoteLegacyJpaRepository
        .getUserAndCheckSecurityAccess(idSecuritycurrency);
    final UploadHistoryquotesSuccess stats = new UploadHistoryquotesSuccess();

    if (uploadFiles.length == 0 || uploadFiles[0].isEmpty()) {
      return stats;
    }

    final ValueFormatConverter converter = new ValueFormatConverter(supportedCSVFormat.decimalSeparator,
        supportedCSVFormat.dateFormat, supportedCSVFormat.thousandSeparator);
    final LocalDate today = LocalDate.now();
    final LocalDate oldestTradingDay = DateBusinessHelper.getOldestTradingDayAsLocalDate();
    final Set<LocalDate> seenInImport = new HashSet<>();
    final Set<LocalDate> existingDates = new HashSet<>(
        historyquoteLegacyJpaRepository.findDatesByIdSecuritycurrency(idSecuritycurrency));
    final List<HistoryquoteLegacy> toInsert = new ArrayList<>();

    try (InputStream is = uploadFiles[0].getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return stats;
      }
      ColumnIndex idx = ColumnIndex.fromHeader(headerLine, userAuditable.user);

      int lineNumber = 1;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        importLine(idSecuritycurrency, userAuditable, converter, today, oldestTradingDay, seenInImport, existingDates,
            toInsert, idx, lineNumber, line, stats);
      }
    }

    if (!toInsert.isEmpty()) {
      historyquoteLegacyJpaRepository.saveAll(toInsert);
    }
    return stats;
  }

  private void importLine(Integer idSecuritycurrency, UserAuditable userAuditable, ValueFormatConverter converter,
      LocalDate today, LocalDate oldestTradingDay, Set<LocalDate> seenInImport, Set<LocalDate> existingDates,
      List<HistoryquoteLegacy> toInsert, ColumnIndex idx, int lineNumber, String line,
      UploadHistoryquotesSuccess stats) throws Exception {
    final String[] data = CSVImportHelper.splitCSVLine(line);
    LegacyImportRow row = new LegacyImportRow();

    try {
      assignCell(converter, row, "date", data, idx.date, LocalDate.class, lineNumber, userAuditable.user, true);
      assignCell(converter, row, "close", data, idx.close, Double.class, lineNumber, userAuditable.user, true);
      assignCell(converter, row, "open", data, idx.open, Double.class, lineNumber, userAuditable.user, false);
      assignCell(converter, row, "high", data, idx.high, Double.class, lineNumber, userAuditable.user, false);
      assignCell(converter, row, "low", data, idx.low, Double.class, lineNumber, userAuditable.user, false);
      assignCell(converter, row, "volume", data, idx.volume, Long.class, lineNumber, userAuditable.user, false);
      assignCell(converter, row, "transferDate", data, idx.transferDate, LocalDate.class, lineNumber, userAuditable.user,
          false);
    } catch (DataViolationException e) {
      stats.validationErrors++;
      return;
    }

    if (row.getDate() == null || row.getClose() == null) {
      stats.validationErrors++;
      return;
    }
    if (row.getDate().isBefore(oldestTradingDay)
        || (userAuditable.auditable instanceof Security s
            && (row.getDate().isBefore(s.getActiveFromDate()) || row.getDate().isAfter(s.getActiveToDate())))) {
      stats.outOfDateRange++;
      return;
    }
    if (!seenInImport.add(row.getDate())) {
      stats.duplicatedInImport++;
      return;
    }
    if (existingDates.contains(row.getDate())) {
      stats.notOverridden++;
      return;
    }

    HistoryquoteLegacy entity = new HistoryquoteLegacy();
    entity.setIdSecuritycurrency(idSecuritycurrency);
    entity.setDate(row.getDate());
    entity.setClose(row.getClose());
    entity.setOpen(row.getOpen());
    entity.setHigh(row.getHigh());
    entity.setLow(row.getLow());
    entity.setVolume(row.getVolume());
    entity.setTransferDate(row.getTransferDate() == null ? today : row.getTransferDate());
    entity.setCreateType(HistoryquoteCreateType.MANUAL_IMPORTED);
    toInsert.add(entity);
    stats.success++;
  }

  private static void assignCell(ValueFormatConverter converter, LegacyImportRow row, String fieldName, String[] data,
      int colIdx, Class<?> type, int lineNumber, User user, boolean required) {
    if (colIdx < 0 || colIdx >= data.length) {
      if (required) {
        throw new DataViolationException(fieldName, "import.filed.format", lineNumber, user.getLocaleStr());
      }
      return;
    }
    String raw = data[colIdx].trim();
    if (raw.isEmpty()) {
      if (required) {
        throw new DataViolationException(fieldName, "import.filed.format", lineNumber, user.getLocaleStr());
      }
      return;
    }
    if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
      raw = raw.substring(1, raw.length() - 1);
    }
    try {
      converter.convertAndSetValue(row, fieldName, raw, type, true);
    } catch (Exception e) {
      throw new DataViolationException(fieldName, "import.filed.format", lineNumber, user.getLocaleStr());
    }
  }

  /** Column-position lookup built once from the CSV header line. -1 = column absent. */
  private static final class ColumnIndex {
    int date = -1;
    int close = -1;
    int open = -1;
    int high = -1;
    int low = -1;
    int volume = -1;
    int transferDate = -1;

    static ColumnIndex fromHeader(String headerLine, User user) {
      String[] headers = headerLine.split(CSVImportHelper.CSV_FIELD_SEPARATOR);
      ColumnIndex idx = new ColumnIndex();
      for (int i = 0; i < headers.length; i++) {
        String name = headers[i].trim();
        if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
          name = name.substring(1, name.length() - 1);
        }
        switch (name.toLowerCase()) {
          case "date" -> idx.date = i;
          case "close" -> idx.close = i;
          case "open" -> idx.open = i;
          case "high" -> idx.high = i;
          case "low" -> idx.low = i;
          case "volume" -> idx.volume = i;
          case "transferdate" -> idx.transferDate = i;
          default -> { /* unknown column ignored */ }
        }
      }
      if (idx.date < 0) {
        throw new DataViolationException("date", "import.field.missing", "date", user.getLocaleStr());
      }
      if (idx.close < 0) {
        throw new DataViolationException("close", "import.field.missing", "close", user.getLocaleStr());
      }
      return idx;
    }
  }

  /**
   * Mutable JavaBean used as the target of {@link ValueFormatConverter#convertAndSetValue},
   * which delegates to Apache Commons {@code PropertyUtils.setSimpleProperty} — that helper
   * resolves properties via JavaBean introspection (getters/setters), NOT via public fields,
   * so the bean must expose proper accessors.
   */
  public static final class LegacyImportRow {
    private LocalDate date;
    private Double close;
    private Double open;
    private Double high;
    private Double low;
    private Long volume;
    private LocalDate transferDate;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getClose() { return close; }
    public void setClose(Double close) { this.close = close; }

    public Double getOpen() { return open; }
    public void setOpen(Double open) { this.open = open; }

    public Double getHigh() { return high; }
    public void setHigh(Double high) { this.high = high; }

    public Double getLow() { return low; }
    public void setLow(Double low) { this.low = low; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
  }
}
