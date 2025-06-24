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

import grafiosch.common.DateHelper;
import grafiosch.common.ValueFormatConverter;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.platformimport.csv.ImportTransactionHelperCsv;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Generic CSV transaction importer supporting template-based parsing with advanced transaction grouping and encoding
 * detection.
 * 
 * <p>
 * This class provides comprehensive CSV import functionality that can handle transaction files from various trading
 * platforms. It features automatic encoding detection, template-based field mapping, multi-line transaction support,
 * day-based transaction grouping, and sophisticated error handling. The importer can process CSV files where single
 * financial transactions span multiple rows or where transactions need to be grouped by order numbers.
 * </p>
 * 
 * <h3>Key Features</h3>
 * 
 * <h4>Automatic Encoding Detection</h4>
 * <p>
 * Uses Mozilla's UniversalDetector to automatically identify file encoding, ensuring proper handling of international
 * characters and various CSV export formats from different trading platforms.
 * </p>
 * 
 * <h4>Template-Based Parsing</h4>
 * <p>
 * Employs configurable templates that define:
 * </p>
 * <ul>
 * <li>Column-to-field mappings for extracting transaction properties</li>
 * <li>Data type conversion rules for dates, numbers, and text fields</li>
 * <li>Transaction type recognition patterns</li>
 * <li>Line filtering rules to ignore header/footer rows</li>
 * </ul>
 * 
 * <h4>Multi-Line Transaction Support</h4>
 * <p>
 * Handles complex transactions that span multiple CSV rows using order-based grouping:
 * </p>
 * <ul>
 * <li><b>Order-based grouping:</b> Transactions with the same order number are processed together</li>
 * <li><b>Single transactions:</b> Rows without order numbers or with "0" orders are processed individually</li>
 * <li><b>Day boundaries:</b> Transaction groups are automatically split at day boundaries</li>
 * </ul>
 * 
 * <h4>Day-Based Processing</h4>
 * <p>
 * Automatically groups transactions by trading day, ensuring that multi-row transactions spanning different days are
 * properly separated and processed in distinct batches.
 * </p>
 * 
 * <h3>CSV Processing Workflow</h3>
 * <ol>
 * <li><b>Encoding Detection:</b> Automatically detects file character encoding</li>
 * <li><b>Template Selection:</b> Validates CSV header against specified template</li>
 * <li><b>Line-by-Line Processing:</b> Parses data rows with field validation</li>
 * <li><b>Transaction Grouping:</b> Groups rows by day and order number</li>
 * <li><b>Import Position Creation:</b> Converts grouped data to import positions</li>
 * <li><b>Error Recording:</b> Captures parsing failures with diagnostic information</li>
 * </ol>
 * 
 * <h3>Template Validation</h3>
 * <p>
 * The importer validates that the CSV header matches the expected template structure:
 * </p>
 * <ul>
 * <li>Required columns must be present in the header</li>
 * <li>Column order can vary but must match template mapping</li>
 * <li>Template ID must exist in the provided template list</li>
 * <li>Field delimiters and data formats must match template configuration</li>
 * </ul>
 * 
 * <h3>Error Handling</h3>
 * <p>
 * Comprehensive error handling includes:
 * </p>
 * <ul>
 * <li><b>Template Mismatch:</b> Clear error messages for header/template conflicts</li>
 * <li><b>Data Conversion Errors:</b> Field-specific error reporting with line numbers</li>
 * <li><b>Line Filtering:</b> Configurable rules to ignore invalid or header rows</li>
 * <li><b>Partial Success:</b> Continues processing valid rows even when some rows fail</li>
 * </ul>
 * 
 * <h3>Line Filtering</h3>
 * <p>
 * Supports flexible line filtering to ignore unwanted rows based on field values:
 * </p>
 * <ul>
 * <li>Regex patterns for field value matching</li>
 * <li>Multi-field filtering rules</li>
 * <li>Header and footer row exclusion</li>
 * </ul>
 * 
 * <h3>Bond Transaction Support</h3>
 * <p>
 * Special handling for bond transactions with percentage-based pricing and bond indicators that require different
 * processing logic than standard equity transactions.
 * </p>
 */
public class GenericTransactionImportCSV extends GenericTransactionImportCsvPdfBase {

  /** Order value indicating no specific order grouping for individual transactions. */
  public static final String ORDER_NOTHING = "0";

  /** Pattern to match order values that should be treated as individual transactions. */
  private static final Pattern ignoreOrderPattern = Pattern.compile("^" + ORDER_NOTHING + "+$");

  /** CSV file to be imported. */
  private final MultipartFile uploadFile;

  /** Detected character encoding for the CSV file. */
  private String encoding;
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Creates a new CSV transaction importer with automatic encoding detection. Immediately analyzes the file to
   * determine the appropriate character encoding for proper text processing during import.
   * 
   * @param importTransactionHead         Import session container with portfolio and account information
   * @param uploadFile                    CSV file containing transaction data to import
   * @param importTransactionTemplateList Available CSV templates for parsing
   */
  public GenericTransactionImportCSV(final ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
    this.uploadFile = uploadFile;
    detectEncodingOfFile();
  }

  /**
   * Imports transaction data from the CSV file using the specified template. Processes the entire file with
   * template-based parsing, transaction grouping, and comprehensive error handling for invalid or incomplete data.
   * 
   * @param importTransactionPosJpaRepository       Repository for persisting successful import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale                              User's locale for number and date formatting
   * @param templateId                              Specific template ID to use for parsing the CSV structure
   * @throws IOException if file reading or processing fails
   */
  public void importCSV(ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer templateId) throws IOException {
    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperCsv
        .readTemplates(importTransactionTemplateList, userLocale);
    parseCsv(templateScannedMap, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, templateId);
  }

  /**
   * Core CSV parsing logic that processes the file line by line with template validation. Handles header validation,
   * data line parsing, transaction grouping by day and order, and maintains processing state throughout the import
   * operation.
   * 
   * @param templateScannedMap                      Available templates mapped to their configurations
   * @param importTransactionPosJpaRepository       Repository for persisting import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   * @param idTransactionImportTemplate             Required template ID for CSV processing
   * @throws IOException if file reading fails
   */
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

  /**
   * Processes a single CSV data line with field extraction, validation, and transaction grouping. Handles day boundary
   * detection for transaction grouping, applies line filtering rules, and manages the accumulation of transaction
   * properties for order-based grouping.
   * 
   * @param templateScannedMap                      Available templates for error handling
   * @param template                                Active template configuration for field parsing
   * @param line                                    CSV line to process
   * @param lineCounter                             Current line number for error reporting
   * @param valueFormatConverter                    Converter for data type transformation
   * @param importPropertiesDuringDay               Accumulator for same-day transactions
   * @param cashaccountList                         Available cash accounts for assignment
   * @param importTransactionPosJpaRepository       Repository for persisting positions
   * @param securityJpaRepository                   Repository for resolving securities
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   */
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

  /**
   * Determines whether a CSV line should be ignored based on field value filtering rules. Applies configured regex
   * patterns to specific fields to exclude header rows, footer rows, or other irrelevant data from transaction
   * processing.
   * 
   * @param values   Array of field values from the CSV line
   * @param template Template configuration containing filtering rules
   * @return true if the line should be ignored, false if it should be processed
   */
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

  /**
   * Automatically detects the character encoding of the uploaded CSV file. Uses Mozilla's UniversalDetector to analyze
   * file content and determine the appropriate encoding, falling back to UTF-8 if detection fails.
   */
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

  /**
   * Converts a single CSV line into an ImportProperties object with field validation and type conversion. Handles
   * special cases like bond indicators, applies data type conversions, and provides detailed error information for
   * debugging failed conversions.
   * 
   * @param lineNumber           Line number for error reporting
   * @param values               Array of string values from the CSV line
   * @param template             Template configuration for field mapping and validation
   * @param valueFormatConverter Converter for data type transformation
   * @return ParseLineSuccessError containing either successful ImportProperties or error details
   */
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

  /**
   * Groups transactions by day and order, then creates import positions for each logical transaction. Handles complex
   * scenarios where single financial transactions span multiple CSV rows, using order numbers to group related rows and
   * day boundaries to separate transaction batches.
   * 
   * @param importPropertiesDuringDay         List of same-day transaction properties
   * @param fileName                          Original filename for tracking
   * @param importTransactionTemplate         Template used for parsing
   * @param template                          Template configuration for order support checking
   * @param securityJpaRepository             Repository for resolving securities
   * @param cashaccountList                   Available cash accounts
   * @param importTransactionPosJpaRepository Repository for persisting positions
   */
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

  /**
   * Creates import transaction positions for a group of related transaction properties. Each group represents a
   * complete logical transaction that may span multiple CSV rows, processed within its own transaction boundary for
   * data consistency.
   * 
   * @param importPropertiesList              Related transaction properties to process together
   * @param fileName                          Original filename for tracking
   * @param importTransactionTemplate         Template used for parsing
   * @param securityJpaRepository             Repository for resolving securities
   * @param cashaccountList                   Available cash accounts
   * @param importTransactionPosJpaRepository Repository for persisting positions
   */
  @Transactional
  @Modifying
  private void createImportTransactionPosByOrder(List<ImportProperties> importPropertiesList, String fileName,
      ImportTransactionTemplate importTransactionTemplate, SecurityJpaRepository securityJpaRepository,
      List<Cashaccount> cashaccountList, ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    checkAndSaveSuccessImportTransaction(importTransactionTemplate, cashaccountList, importPropertiesList, fileName,
        importTransactionPosJpaRepository, securityJpaRepository);
  }

  /**
   * Records detailed information about CSV line parsing failures for debugging and troubleshooting. Creates import
   * position records with failure details including the last successfully parsed field and specific error messages for
   * template and data format issues.
   * 
   * @param importTransactionPosFailedJpaRepository Repository for storing failure details
   * @param importTransactionPosJpaRepository       Repository for creating failure records
   * @param parseLineSuccessError                   Error details from parsing attempt
   * @param idTransactionImportTemplate             Template ID that was attempted
   * @param fileName                                Original filename for tracking
   * @param lineNumber                              Line number where parsing failed
   */
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

  /**
   * Validates that the CSV header matches the specified template and retrieves the template configuration. Ensures that
   * required columns are present and properly mapped according to the template definition.
   * 
   * @param templateScannedMap                  Available templates mapped to their configurations
   * @param headerLine                          CSV header line cleaned of BOM characters
   * @param requiredIdTransactionImportTemplate Required template ID for validation
   * @return Validated template configuration ready for CSV parsing
   * @throws GeneralNotTranslatedWithArgumentsException if template validation fails
   */
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

  /**
   * Container class for tracking parsing results and error information for individual CSV lines. Provides detailed
   * success/failure status and diagnostic information for troubleshooting template matching and data conversion issues.
   */
  public static class ParseLineSuccessError {
    /** Successfully parsed transaction properties, null if parsing failed. */
    public ImportProperties importProperties;

    /** Name of the last field that was successfully parsed before failure. */
    public String lastSuccessProperty;

    /** Detailed error message describing parsing failure. */
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
