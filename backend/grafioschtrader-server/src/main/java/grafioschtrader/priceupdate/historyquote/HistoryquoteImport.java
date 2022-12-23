package grafioschtrader.priceupdate.historyquote;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.FieldColumnMapping;
import grafioschtrader.common.ImportDataRequired;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.dto.SupportedCSVFormat;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository.SecurityCurrencyIdAndDate;
import grafioschtrader.types.HistoryquoteCreateType;
import jakarta.validation.Validator;

/**
 * Import delimited EOD into history quote. First row must contain the delimited
 * column names. This column names correspondent to property names of history
 * quote.
 *
 */
public class HistoryquoteImport {

  private final String CSV_FIELD_SEPARATOR = ";";

  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final Validator validator;

  public HistoryquoteImport(HistoryquoteJpaRepository historyquoteJpaRepository, Validator validator) {
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.validator = validator;
  }

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
      } catch (Exception e) {
        // TODO not nice
        throw e;
      }
      historyquoteJpaRepository.saveAll(historyquotes);
    }
    return uploadHistoryquotesSuccess;
  }

  private Collection<Historyquote> readFile(Integer idSecuritycurrency, final UserAuditable userAuditable,
      BufferedReader reader, UploadHistoryquotesSuccess uploadHistoryquotesSuccess,
      ValueFormatConverter valueFormatConverter) throws Exception {
    Map<Date, Historyquote> historyquoteNewMap = new HashMap<>();
    Set<Date> existingHistoryquoteDateSet = this.getExistingHistoryquotes(idSecuritycurrency);
    Date oldestDate = DateHelper.getOldestTradingDay();
    List<FieldColumnMapping> fieldColumnMappings = null;
    int lineCounter = 0;
    while (reader.ready()) {
      lineCounter++;
      String line = reader.readLine();
      if (lineCounter == 1) {
        // Header line
        fieldColumnMappings = this.getHeaderFieldNameMapping(line, userAuditable.user);
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

  private Set<Date> getExistingHistoryquotes(Integer idSecuritycurrency) {
    Set<Date> existingHistoryquotesMap = new HashSet<>();
    List<SecurityCurrencyIdAndDate> existingHistoryquotesStream = historyquoteJpaRepository
        .findByIdSecuritycurrency(idSecuritycurrency);
    existingHistoryquotesStream.forEach(scd -> existingHistoryquotesMap.add(scd.getDate()));
    return existingHistoryquotesMap;
  }

  private List<FieldColumnMapping> getHeaderFieldNameMapping(String line, final User user) {
    List<Field> fields = DataHelper.getFieldByPropertiesAnnotation(Historyquote.class,
        Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
    final String[] columnHeader = line.split(";");
    List<FieldColumnMapping> fieldColumnMappings = new ArrayList<>();
    for (int i = 0; i < columnHeader.length; i++) {
      final int k = i;
      fields.stream().filter(field -> field.getName().endsWith(columnHeader[k].trim())).findFirst()
          .ifPresent(field -> fieldColumnMappings.add(new FieldColumnMapping(k, field)));
    }
    final List<Field> fieldsRequired = FieldUtils.getFieldsListWithAnnotation(Historyquote.class,
        ImportDataRequired.class);
    for (Field fieldRequired : fieldsRequired) {
      Optional<FieldColumnMapping> fcmOpt = fieldColumnMappings.stream()
          .filter(fcm -> fcm.field.getName().equals(fieldRequired.getName())).findFirst();
      if (fcmOpt.isEmpty()) {
        throw new DataViolationException(fieldRequired.getName(), "import.field.missing", fieldRequired.getName(),
            user.getLocaleStr());
      } else {
        fcmOpt.get().required = true;
      }

    }

    return fieldColumnMappings;
  }

  private Historyquote readLineAndCreateHistoryquote(Integer idSecuritycurrency,
      ValueFormatConverter valueFormatConverter, String line, int lineCounter, final User user,
      List<FieldColumnMapping> fieldColumnMappings) throws Exception {
    final String[] data = line.split(CSV_FIELD_SEPARATOR, -1);
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
