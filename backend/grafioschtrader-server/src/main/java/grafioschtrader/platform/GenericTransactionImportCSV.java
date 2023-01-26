package grafioschtrader.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.common.DateHelper;
import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.platformimport.csv.ImportTransactionHelperCsv;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Import a single csv file, which can produce one or more import transactions.
 */
public class GenericTransactionImportCSV extends GenericTransactionImportCsvPdfBase {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public static final String ORDER_NOTHING = "0";
  private static final Pattern ignoreOrderPattern = Pattern.compile("^" + ORDER_NOTHING + "+$");
  private final MultipartFile uploadFile;
  private String encoding;

  public GenericTransactionImportCSV(final ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
    this.uploadFile = uploadFile;
    detectEncodingOfFile();
  }

  public void importCSV(ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer templateId) throws IOException {

    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperCsv
        .readTemplates(importTransactionTemplateList, userLocale);
    parseCsv(templateScannedMap, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, templateId);
  }

  private void parseCsv(Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository,
      Integer idTransactionImportTemplate) throws IOException {

    if (!uploadFile.isEmpty()) {
      List<Cashaccount> cashaccountList = importTransactionHead.getSecurityaccount().getPortfolio()
          .getCashaccountList();

      TemplateConfigurationAndStateCsv template = null;
      ValueFormatConverter valueFormatConverter = null;
      List<ImportProperties> importPropertiesDuringDay = new ArrayList<>();
      int lineCounter = 0;

      try (InputStream is = uploadFile.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, encoding))) {
        while (reader.ready()) {
          String line = reader.readLine();
          lineCounter++;
          switch (lineCounter) {
          case 1:
            // Header line
            template = checkAndGetTemplate(templateScannedMap, line.replaceAll("\\uFEFF", ""),
                idTransactionImportTemplate);
            valueFormatConverter = new ValueFormatConverter(template.getDateFormat(), template.getTimeFormat(),
                template.getThousandSeparators(), template.getThousandSeparatorsPattern(),
                template.getDecimalSeparator(), template.getLocale());
            break;
          default:
            parseSingleDataLine(templateScannedMap, template, line, lineCounter, valueFormatConverter,
                importPropertiesDuringDay, cashaccountList, importTransactionPosJpaRepository, securityJpaRepository,
                importTransactionPosFailedJpaRepository);
          }
        }
      }
      if (!importPropertiesDuringDay.isEmpty()) {
        transferToImportTransactionPosForOneDay(importPropertiesDuringDay, uploadFile.getOriginalFilename(),
            templateScannedMap.get(template), template, securityJpaRepository, cashaccountList,
            importTransactionPosJpaRepository);
      }
    }
  }

  protected void parseSingleDataLine(
      Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap,
      TemplateConfigurationAndStateCsv template, String line, int lineCounter,
      ValueFormatConverter valueFormatConverter, List<ImportProperties> importPropertiesDuringDay,
      List<Cashaccount> cashaccountList, ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {

    // Data lines
    String[] values = StringUtils.splitByWholeSeparatorPreserveAllTokens(line, template.getDelimiterField());
    if (!ignoreLineByFieldValueCheck(values, template)) {
      ParseLineSuccessError parseLineSuccessError = parseDataLine(lineCounter, values, template, valueFormatConverter);

      if (parseLineSuccessError.hasSuccess()) {
        if (!importPropertiesDuringDay.isEmpty()
            && !DateHelper.isSameDay(importPropertiesDuringDay.get(0).getDatetime(),
                parseLineSuccessError.importProperties.getDatetime())) {
          // Day of transaction has changed

          transferToImportTransactionPosForOneDay(importPropertiesDuringDay, uploadFile.getOriginalFilename(),
              templateScannedMap.get(template), template, securityJpaRepository, cashaccountList,
              importTransactionPosJpaRepository);
          importPropertiesDuringDay.clear();
        }
        importPropertiesDuringDay.add(parseLineSuccessError.importProperties);

      } else {
        if (!parseLineSuccessError.isEmpty()) {
          failedReadLine(importTransactionPosFailedJpaRepository, importTransactionPosJpaRepository,
              parseLineSuccessError, template.getImportTransactionTemplate().getIdTransactionImportTemplate(),
              uploadFile.getOriginalFilename(), lineCounter);
        }
      }
    }
  }

  private boolean ignoreLineByFieldValueCheck(String[] values, TemplateConfigurationAndStateCsv template) {
    if (!template.getIgnoreLineByFieldValueMap().isEmpty()) {
      for (Map.Entry<String, String> entry : template.getIgnoreLineByFieldValueMap().entrySet()) {
        int column = template.getPropertyColumnMapping().get(entry.getKey());
        if (values[column].matches(entry.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  private void detectEncodingOfFile() {
    try {
      encoding = UniversalDetector.detectCharset(uploadFile.getInputStream());
      log.info("Encoding for file {} was detected as {}", uploadFile.getOriginalFilename(), encoding);
    } catch (IOException e) {
      log.error("Encoding of file {} could not detected", uploadFile.getOriginalFilename());
    }
    if (encoding == null) {
      encoding = StandardCharsets.UTF_8.toString();
    }
  }

  protected ParseLineSuccessError parseDataLine(Integer lineNumber, String[] values,
      TemplateConfigurationAndStateCsv template, ValueFormatConverter valueFormatConverter) {

    String lastSuccessProperty = null;
    ImportProperties importProperties = new ImportProperties(template.getTransactionTypesMap(),
        template.getImportKnownOtherFlagsSet().clone(), lineNumber, template.getIgnoreTaxOnDivInt());

    for (int i = 0; i < values.length; i++) {
      String propertyName = template.getColumnPropertyMapping().get(i);
      if (propertyName != null) {
        String value = values[i];
        if (!StringUtils.isEmpty(value)) {
          if (propertyName.equals(template.getBondProperty()) && value.contains(template.getBondIndicator())) {
            importProperties.setPer(template.getBondIndicator());
          }
          try {
            valueFormatConverter.convertAndSetValue(importProperties, propertyName, value,
                TemplateConfiguration.getPropertyDataTypeMap().get(propertyName));
          } catch (Exception ex) {
            log.error("Line: {}  Field: {}", lineNumber, propertyName);
            return new ParseLineSuccessError(null, lastSuccessProperty, ex.toString());
          }
          lastSuccessProperty = propertyName;
        }
      }
    }
    return new ParseLineSuccessError(importProperties, lastSuccessProperty);
  }

  private void transferToImportTransactionPosForOneDay(List<ImportProperties> importPropertiesDuringDay,
      String fileName, ImportTransactionTemplate importTransactionTemplate, TemplateConfigurationAndStateCsv template,
      SecurityJpaRepository securityJpaRepository, List<Cashaccount> cashaccountList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {

    if (importPropertiesDuringDay.size() > 1 && template.isOrderSupport()) {
      Map<String, List<ImportProperties>> orderMap = importPropertiesDuringDay.stream()
          .collect(Collectors.groupingBy(ImportProperties::getOrder));

      for (Map.Entry<String, List<ImportProperties>> entry : orderMap.entrySet()) {
        if (entry.getKey() == null || entry.getKey().isBlank()
            || StringUtils.isNumeric(entry.getKey()) && ignoreOrderPattern.matcher(entry.getKey()).matches()) {
          for (ImportProperties ip : entry.getValue()) {
            createImportTransactionPosByOrder(Arrays.asList(ip), fileName, importTransactionTemplate,
                securityJpaRepository, cashaccountList, importTransactionPosJpaRepository);
          }
        } else {
          // Instance of ImportProperties with same order belongs together
          createImportTransactionPosByOrder(entry.getValue(), fileName, importTransactionTemplate,
              securityJpaRepository, cashaccountList, importTransactionPosJpaRepository);
        }
      }

    } else {
      importPropertiesDuringDay.forEach(ip -> createImportTransactionPosByOrder(Arrays.asList(ip), fileName,
          importTransactionTemplate, securityJpaRepository, cashaccountList, importTransactionPosJpaRepository));
    }
  }

  @Transactional
  @Modifying
  private void createImportTransactionPosByOrder(List<ImportProperties> importPropertiesList, String fileName,
      ImportTransactionTemplate importTransactionTemplate, SecurityJpaRepository securityJpaRepository,
      List<Cashaccount> cashaccountList, ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    checkAndSaveSuccessImportTransaction(importTransactionTemplate, cashaccountList, importPropertiesList, fileName,
        importTransactionPosJpaRepository, securityJpaRepository);
  }

  @Transactional
  @Modifying
  private void failedReadLine(ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, ParseLineSuccessError parseLineSuccessError,
      Integer idTransactionImportTemplate, String fileName, Integer lineNumber) {

    ImportTransactionPos importTransactionPos = new ImportTransactionPos(importTransactionHead.getIdTenant(), fileName,
        importTransactionHead.getIdTransactionHead());
    importTransactionPos.setIdFilePart(lineNumber);
    importTransactionPos = importTransactionPosJpaRepository.save(importTransactionPos);
    ImportTransactionPosFailed importTransactionPosFailed = new ImportTransactionPosFailed(
        importTransactionPos.getIdTransactionPos(), idTransactionImportTemplate,
        parseLineSuccessError.lastSuccessProperty,
        parseLineSuccessError.isEmpty() ? "Incomplete information" : parseLineSuccessError.errorMessage);
    importTransactionPosFailedJpaRepository.save(importTransactionPosFailed);

  }

  private TemplateConfigurationAndStateCsv checkAndGetTemplate(
      Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap, String headerLine,
      int requiredIdTransactionImportTemplate) {
    Optional<TemplateConfigurationAndStateCsv> templateOpt = templateScannedMap.entrySet().stream()
        .filter(e -> templateScannedMap.get(e.getKey()).getIdTransactionImportTemplate()
            .equals(requiredIdTransactionImportTemplate))
        .findFirst().map(e -> e.getKey());
    if (templateOpt.isPresent()) {
      if (!templateOpt.get().isValidTemplateForForm(headerLine)) {
        // Template does not have required columns
        throw new GeneralNotTranslatedWithArgumentsException("gt.import.column.missmatch", null);
      }
    } else {
      // Template with id not found
      throw new GeneralNotTranslatedWithArgumentsException("gt.import.csv.id",
          new Object[] { requiredIdTransactionImportTemplate });
    }
    return templateOpt.get();
  }

  public static class ParseLineSuccessError {
    public ImportProperties importProperties;
    public String lastSuccessProperty;
    public String errorMessage;

    public ParseLineSuccessError(ImportProperties importProperties, String lastSuccessProperty) {
      this.importProperties = importProperties;
      this.lastSuccessProperty = lastSuccessProperty;
    }

    public ParseLineSuccessError(ImportProperties importProperties, String lastSuccessProperty, String errorMessage) {
      this(importProperties, lastSuccessProperty);
      this.errorMessage = errorMessage;
    }

    public boolean hasSuccess() {
      return importProperties != null && !importProperties.maybeEmpty();
    }

    public boolean isEmpty() {
      return importProperties != null && lastSuccessProperty == null;
    }
  }

}
