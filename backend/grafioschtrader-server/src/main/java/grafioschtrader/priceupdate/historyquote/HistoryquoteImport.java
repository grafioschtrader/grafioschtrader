package grafioschtrader.priceupdate.historyquote;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.common.CSVImportHelper;
import grafiosch.common.FieldColumnMapping;
import grafiosch.common.ValueFormatConverter;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.DateBusinessHelper;
import grafioschtrader.dto.SupportedCSVFormat;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository.SecurityCurrencyIdAndDate;
import grafioschtrader.types.HistoryquoteCreateType;
import jakarta.validation.Validator;

/**
 * Imports end-of-day (EOD) historical quotes from delimited CSV files into the database.
 *
 * <p>This class provides functionality to upload and process CSV files containing historical
 * price data for securities. The CSV format requires:
 * <ul>
 *   <li>First row must be column headers matching {@link Historyquote} field names</li>
 *   <li>Semicolon (;) as field separator</li>
 *   <li>Required fields marked with {@link grafiosch.common.ImportDataRequired}</li>
 * </ul>
 *
 * <p>Example CSV format:
 * <pre>
 * date;close;open;high;low;volume
 * 2024-01-15;150.25;148.50;151.00;148.00;1000000
 * 2024-01-16;152.00;150.50;153.00;150.00;1200000
 * </pre>
 *
 * <p>Processing features:
 * <ul>
 *   <li>Automatic header-to-field mapping using entity annotations</li>
 *   <li>Configurable date and number formats via {@link SupportedCSVFormat}</li>
 *   <li>Duplicate detection (skips dates already in database)</li>
 *   <li>Date range validation against security active period</li>
 *   <li>Bean validation for imported records</li>
 * </ul>
 *
 * <p>The class uses {@link CSVImportHelper} for header-to-field mapping, which inspects
 * the entity class for fields annotated with {@link grafiosch.common.PropertyAlwaysUpdatable}
 * or {@link grafiosch.common.PropertyOnlyCreation}.
 *
 * @see Historyquote
 * @see CSVImportHelper
 * @see SupportedCSVFormat
 */
public class HistoryquoteImport {

  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final Validator validator;

  /**
   * Creates a new history quote importer.
   *
   * @param historyquoteJpaRepository repository for accessing and persisting history quotes
   * @param validator Jakarta Bean Validation validator for validating imported records
   */
  public HistoryquoteImport(HistoryquoteJpaRepository historyquoteJpaRepository, Validator validator) {
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.validator = validator;
  }

  /**
   * Uploads and processes a CSV file containing historical EOD quotes for a security.
   *
   * <p>This method performs the following steps:
   * <ol>
   *   <li>Verifies user access to the target security</li>
   *   <li>Parses the CSV file using configurable format settings</li>
   *   <li>Maps CSV columns to {@link Historyquote} fields based on header names</li>
   *   <li>Validates each record and checks for duplicates</li>
   *   <li>Persists valid records to the database</li>
   * </ol>
   *
   * @param idSecuritycurrency the ID of the security to import quotes for
   * @param uploadFiles array of uploaded CSV files (only the first file is processed)
   * @param supportedCSVFormat format configuration specifying decimal separator, thousand
   *        separator, and date format to use when parsing values
   * @return statistics about the import operation including success count, validation errors,
   *         duplicates skipped, and out-of-range records
   * @throws SecurityException if the user doesn't have access to the specified security
   * @throws DataViolationException if required columns are missing from the CSV header
   * @throws Exception if file reading or processing fails
   */
  @Transactional
  @Modifying
  public UploadHistoryquotesSuccess uploadHistoryquotes(Integer idSecuritycurrency, MultipartFile[] uploadFiles,
      SupportedCSVFormat supportedCSVFormat) throws Exception {
    final UserAuditable userAuditable = historyquoteJpaRepository.getUserAndCheckSecurityAccess(idSecuritycurrency);

    UploadHistoryquotesSuccess uploadHistoryquotesSuccess = new UploadHistoryquotesSuccess();

    if (!uploadFiles[0].isEmpty()) {

      Collection<Historyquote> historyquotes = new ArrayList<>();

      ValueFormatConverter valueFormatConverter = new ValueFormatConverter(supportedCSVFormat.decimalSeparator,
          supportedCSVFormat.dateFormat, supportedCSVFormat.thousandSeparator);
      try (InputStream is = uploadFiles[0].getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        historyquotes = readFile(idSecuritycurrency, userAuditable, reader, uploadHistoryquotesSuccess,
            valueFormatConverter);
      }
      historyquoteJpaRepository.saveAll(historyquotes);
    }
    return uploadHistoryquotesSuccess;
  }

  /**
   * Reads and processes all lines from the CSV file.
   *
   * <p>The first line is treated as headers and used to create field mappings.
   * Subsequent lines are parsed as data records. Each record is validated and
   * checked for duplicates before being added to the result collection.
   *
   * @param idSecuritycurrency the security ID to associate quotes with
   * @param userAuditable contains user info and the auditable security entity
   * @param reader buffered reader for the CSV input stream
   * @param uploadHistoryquotesSuccess statistics collector for tracking import results
   * @param valueFormatConverter converter for parsing dates and numbers
   * @return collection of valid history quotes to be persisted
   * @throws Exception if parsing fails
   */
  private Collection<Historyquote> readFile(Integer idSecuritycurrency, final UserAuditable userAuditable,
      BufferedReader reader, UploadHistoryquotesSuccess uploadHistoryquotesSuccess,
      ValueFormatConverter valueFormatConverter) throws Exception {
    Map<Date, Historyquote> historyquoteNewMap = new HashMap<>();
    Set<Date> existingHistoryquoteDateSet = this.getExistingHistoryquotes(idSecuritycurrency);
    Date oldestDate = DateBusinessHelper.getOldestTradingDay();
    List<FieldColumnMapping> fieldColumnMappings = null;
    int lineCounter = 0;
    while (reader.ready()) {
      lineCounter++;
      String line = reader.readLine();
      if (lineCounter == 1) {
        // Header line - create field mappings using shared helper
        fieldColumnMappings = CSVImportHelper.getHeaderFieldNameMapping(line, Historyquote.class, userAuditable.user);
      } else {
        // Data lines
        Historyquote historyquote = readLineAndCreateHistoryquote(idSecuritycurrency, valueFormatConverter, line,
            lineCounter, userAuditable.user, fieldColumnMappings);

        if (!validator.validate(historyquote).isEmpty()) {
          uploadHistoryquotesSuccess.validationErrors++;
        } else if (!existingHistoryquoteDateSet.contains(historyquote.getDate())) {
          if ((userAuditable.auditable instanceof Security
              && (historyquote.getDate().before(((Security) userAuditable.auditable).getActiveFromDate())
                  || historyquote.getDate().after(((Security) userAuditable.auditable).getActiveToDate())))
              || historyquote.getDate().before(oldestDate)) {
            uploadHistoryquotesSuccess.outOfDateRange++;
            continue;
          } else if (historyquoteNewMap.get(historyquote.getDate()) != null) {
            uploadHistoryquotesSuccess.duplicatedInImport++;
          }
          historyquoteNewMap.put(historyquote.getDate(), historyquote);
          uploadHistoryquotesSuccess.success++;
        } else {
          uploadHistoryquotesSuccess.notOverridden++;
        }
      }
    }
    return historyquoteNewMap.values();
  }

  /**
   * Retrieves the set of dates for which history quotes already exist.
   *
   * @param idSecuritycurrency the security ID to check
   * @return set of existing quote dates
   */
  private Set<Date> getExistingHistoryquotes(Integer idSecuritycurrency) {
    Set<Date> existingHistoryquotesMap = new HashSet<>();
    List<SecurityCurrencyIdAndDate> existingHistoryquotesStream = historyquoteJpaRepository
        .findByIdSecuritycurrency(idSecuritycurrency);
    existingHistoryquotesStream.forEach(scd -> existingHistoryquotesMap.add(scd.getDate()));
    return existingHistoryquotesMap;
  }

  /**
   * Parses a single CSV data line and creates a history quote entity.
   *
   * <p>Uses the provided field mappings to extract values from the CSV line
   * and populate the corresponding fields in the history quote. Values are
   * converted using the provided format converter.
   *
   * @param idSecuritycurrency the security ID to associate the quote with
   * @param valueFormatConverter converter for parsing dates and numbers
   * @param line the CSV data line to parse
   * @param lineCounter the line number (for error reporting)
   * @param user the current user (for locale in error messages)
   * @param fieldColumnMappings mappings from CSV columns to entity fields
   * @return a populated history quote entity
   * @throws DataViolationException if a value cannot be parsed
   */
  private Historyquote readLineAndCreateHistoryquote(Integer idSecuritycurrency,
      ValueFormatConverter valueFormatConverter, String line, int lineCounter, final User user,
      List<FieldColumnMapping> fieldColumnMappings) throws Exception {
    final String[] data = CSVImportHelper.splitCSVLine(line);
    Historyquote historyquote = new Historyquote(idSecuritycurrency, HistoryquoteCreateType.MANUAL_IMPORTED);
    for (FieldColumnMapping fieldColumnMapping : fieldColumnMappings) {
      try {
        String value = data[fieldColumnMapping.col];
        if (value != null && !value.isBlank() || fieldColumnMapping.required) {
          if (fieldColumnMapping.field.getType() == Date.class) {
            value = value.startsWith("\"") ? value.substring(1, value.length() - 1)
                : value.substring(0, value.length());
          }

          valueFormatConverter.convertAndSetValue(historyquote, fieldColumnMapping.field.getName(), value,
              fieldColumnMapping.field.getType(), true);
        }
      } catch (Exception e) {
        throw new DataViolationException(fieldColumnMapping.field.getName(), "import.filed.format", lineCounter,
            user.getLocaleStr());
      }
    }
    return historyquote;
  }

}

