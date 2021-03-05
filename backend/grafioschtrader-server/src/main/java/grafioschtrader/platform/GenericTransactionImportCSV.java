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
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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
 * 
 * @author Hugo Graf
 *
 */
public class GenericTransactionImportCSV extends GenericTransactionImportCsvPdfBase {


  public static final String ORDER_NOTHING = "0";
  private MultipartFile uploadFile;

  public GenericTransactionImportCSV(final ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
    this.uploadFile = uploadFile;
  }

  public void importCSV(ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale) throws IOException {

    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperCsv
        .readTemplates(importTransactionTemplateList, userLocale);
    parseCsv(templateScannedMap, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository);

  }

  private void parseCsv(Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) throws IOException {

    if (!uploadFile.isEmpty()) {
      List<Cashaccount> cashaccountList = importTransactionHead.getSecurityaccount().getPortfolio()
          .getCashaccountList();

      TemplateConfigurationAndStateCsv template = null;
      ValueFormatConverter valueFormatConverter = null;
      List<ImportProperties> importPropertiesDuringDay = new ArrayList<>();
      int lineCounter = 0;
      int templateId = -1;

      try (InputStream is = uploadFile.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        while (reader.ready()) {
          String line = reader.readLine();
          lineCounter++;
          switch (lineCounter) {
          case 1:
            String templateIdStr = line.replaceFirst("(\\d+).+", "$1").replaceAll("\\uFEFF", "");
            templateId = Integer.parseInt(templateIdStr);
            break;
          case 2:
            // Header line
            template = checkAndGetTemplate(templateScannedMap, line, templateId);
            valueFormatConverter = new ValueFormatConverter(template.getLocale(), template.getDateFormat(),
                template.getThousandSeparatorsPattern(), template.getTimeFormat());
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
    ParseLineSuccessError parseLineSuccessError = parseDataLine(lineCounter, line, template, valueFormatConverter);

    if (parseLineSuccessError.hasSuccess()) {
      if (!importPropertiesDuringDay.isEmpty() && !DateHelper.isSameDay(importPropertiesDuringDay.get(0).getDatetime(),
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

  protected ParseLineSuccessError parseDataLine(Integer lineNumber, String line,
      TemplateConfigurationAndStateCsv template, ValueFormatConverter valueFormatConverter) {
    String[] values = StringUtils.splitByWholeSeparatorPreserveAllTokens(line, template.getDelimiterField());
    String lastSuccessProperty = null;
    ImportProperties importProperties = new ImportProperties(template.getTransactionTypesMap(),
        template.getImportKnownOtherFlagsSet(), lineNumber);

    for (int i = 0; i < values.length; i++) {
      String propertyName = template.getColumnPropertyMapping().get(i);
      if (propertyName != null) {
        String value = values[i];
        if(!StringUtils.isEmpty(value)) {
          if (propertyName.equals(template.getBondProperty()) && value.contains(template.getBondIndicator())) {
            importProperties.setPer(template.getBondIndicator());
          }
          try {
            valueFormatConverter.convertAndSetValue(importProperties, propertyName, value,
                TemplateConfiguration.getPropertyDataTypeMap().get(propertyName));
         
          } catch (Exception ex) {
            System.out.println("Line:" + lineNumber + " Field:" + propertyName);
            
            Throwable cause = ex.getCause();
            return new ParseLineSuccessError(null, lastSuccessProperty, cause.getMessage());
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
        if (entry.getKey().equals(ORDER_NOTHING)) {
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
 
    this.checkAndSaveSuccessImportTransaction(importTransactionTemplate, cashaccountList, importPropertiesList,
        fileName, importTransactionPosJpaRepository, securityJpaRepository);

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
      int requiredTempalteId) {
    Optional<TemplateConfigurationAndStateCsv> templateOpt = templateScannedMap.keySet().stream()
        .filter(itt -> itt.getTemplateId() == requiredTempalteId).findFirst();
    if (templateOpt.isPresent()) {
      if (!templateOpt.get().isValidTemplateForForm(headerLine)) {
        // Template does not have required columns
        throw new GeneralNotTranslatedWithArgumentsException("gt.import.column.missmatch", null);
      }

    } else {
      // Template with id not found
      throw new GeneralNotTranslatedWithArgumentsException("gt.import.csv.id", new Object[]{requiredTempalteId});

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
