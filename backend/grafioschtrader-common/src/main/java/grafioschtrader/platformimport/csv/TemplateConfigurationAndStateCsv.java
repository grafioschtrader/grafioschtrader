package grafioschtrader.platformimport.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.TemplateConfiguration;

/**
 * CSV-specific template configuration for parsing financial transaction data from CSV documents.
 * 
 * <p>
 * This class extends TemplateConfiguration to handle CSV file format specifics including column mappings, field
 * delimiters, and multi-row transaction processing. CSV templates map column headers from trading platform exports to
 * transaction fields, enabling automated import of transaction data.
 * </p>
 * 
 * <h3>CSV Template Structure</h3>
 * <p>
 * CSV templates define mappings between CSV column headers and transaction fields:
 * </p>
 * 
 * <pre>
 * cac=Currency
 * ta=Total Amount  
 * datetime=Date
 * transType=Transaction Type
 * [END]
 * templateId=1
 * delimiterField=,
 * dateFormat=dd.MM.yyyy
 * transType=ACCUMULATE|Purchase,Buy
 * </pre>
 * 
 * <h3>CSV-Specific Configuration</h3>
 * <p>
 * Additional configuration options for CSV processing:
 * </p>
 * <ul>
 * <li>templateId - Unique identifier for CSV template selection (mandatory)</li>
 * <li>delimiterField - Column separator character like "," or ";" (mandatory)</li>
 * <li>bond - Bond price indicator: "quotation|%" for percentage-based pricing</li>
 * <li>ignoreLineByFieldValue - Skip rows: "ta||-" ignores rows with "-" in total amount</li>
 * </ul>
 * 
 * <h3>Multi-Row Transaction Support</h3>
 * <p>
 * CSV files can contain multiple transactions and support features like:
 * </p>
 * <ul>
 * <li>Order field - Links related transaction rows together</li>
 * <li>Line filtering - Ignores header/footer rows or invalid data</li>
 * <li>Partial fills - Handles split executions across multiple rows</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * 
 * <pre>{@code
 * // Create CSV template configuration
 * TemplateConfigurationAndStateCsv csvConfig = new TemplateConfigurationAndStateCsv(template, Locale.GERMAN,
 *     templateList);
 * 
 * // Parse template
 * csvConfig.parseTemplateAndThrowError(true);
 * 
 * // Validate against CSV header
 * String csvHeader = "Date,Currency,Amount,Transaction Type";
 * boolean isValid = csvConfig.isValidTemplateForForm(csvHeader);
 * 
 * // Access column mappings
 * Map<Integer, String> columnMappings = csvConfig.getColumnPropertyMapping();
 * String delimiter = csvConfig.getDelimiterField();
 * }</pre>
 * 
 * <h3>State Management</h3>
 * <p>
 * This class maintains parsing state including column mappings that are populated when validating against actual CSV
 * headers. The same instance can be reused for parsing multiple CSV files with the same structure.
 * </p>
 */
public class TemplateConfigurationAndStateCsv extends TemplateConfiguration {

  /** Configuration key for CSV template identifier, this value is required */
  private static final String CONF_TEMPLATE_ID = "templateId";

  /** Configuration key for CSV field delimiter. */
  private static final String CONF_DELIMITER_FIELD = "delimiterField";

  /** Configuration key for bond price percentage indicator. It may have a percentage sign. */
  private static final String CONF_BOND_FIELD_INDICATOR = "bond";

  /** Configuration key for line filtering rules. */
  private static final String CONF_IGNORE_LINE_BY_FIELD_VALUE = "ignoreLineByFieldValue";

  /** Contains the header to property mapping of a template */
  private Map<String, String> columnHeaderNamePropertyMap = new HashMap<>();

  /** Contains the csv column number to property mapping. It starts with 0. */
  private Map<Integer, String> columnPropertyMapping;

  /** Contains the internal property name to column mapping. It starts with 0. */
  private Map<String, Integer> propertyColumnMapping;

  /** Rules for ignoring CSV rows based on field values. */
  private Map<String, String> ignoreLineByFieldValueMap = new HashMap<>();

  /** Unique identifier for this CSV template. */
  private Integer templateId;

  /** Character used to separate CSV columns (e.g., "," or ";"). */
  private String delimiterField;

  /** Property name that contains bond pricing information. */
  private String bondProperty;

  /** Indicator text that marks bond percentage pricing (e.g., "%"). */
  private String bondIndicator;

  /** Whether this template supports transaction order linking. */
  private boolean orderSupport;

  /** List of existing template IDs for uniqueness validation. */
  private List<TemplateIdPurposeCsv> templateIdPurposeCsvList;

  /**
   * Creates a new CSV template configuration.
   * 
   * @param importTransactionTemplate The template containing CSV mapping definitions
   * @param userLocale                User's locale for number and date formatting
   * @param templateIdPurposeCsvList  Existing templates for ID uniqueness validation
   */
  public TemplateConfigurationAndStateCsv(ImportTransactionTemplate importTransactionTemplate, Locale userLocale,
      List<TemplateIdPurposeCsv> templateIdPurposeCsvList) {
    super(importTransactionTemplate, userLocale);
    this.templateIdPurposeCsvList = templateIdPurposeCsvList;
  }

  /**
   * Reads CSV column header to property mappings from template lines. Each line before [END] contains a mapping like
   * "cac=Currency".
   * 
   * @param templateLines          Array of template lines
   * @param startRowConfig         Index where configuration section begins
   * @param dataViolationException Exception to collect validation errors
   */
  @Override
  protected void readTemplateProperties(String[] templateLines, int startRowConfig,
      DataViolationException dataViolationException) {
    for (int i = 0; i < startRowConfig; i++) {
      String splitEqual[] = templateLines[i].split("=");
      columnHeaderNamePropertyMap.put(splitEqual[1], splitEqual[0]);
    }
  }

  /**
   * Processes CSV-specific configuration options from the template. Handles templateId, delimiterField, bond
   * indicators, and line filtering rules.
   * 
   * @param splitEqual Configuration key and value pair
   */
  @Override
  protected void addionalConfigurations(String[] splitEqual) {
    switch (splitEqual[0]) {
    case CONF_TEMPLATE_ID:
      templateId = Integer.parseInt(splitEqual[1]);
      break;
    case CONF_DELIMITER_FIELD:
      delimiterField = splitEqual[1];
      break;
    case CONF_BOND_FIELD_INDICATOR:
      String[] propertyIndicator = splitEqual[1].split(Pattern.quote("|"));
      bondProperty = propertyIndicator[0];
      bondIndicator = propertyIndicator[1];
      break;
    case CONF_IGNORE_LINE_BY_FIELD_VALUE:
      String ignoreField[] = splitEqual[1].split(Pattern.quote("||"));
      ignoreLineByFieldValueMap.put(ignoreField[0], ignoreField[1]);
      break;
    }
  }

  /**
   * Returns the CSV field delimiter character.
   * 
   * @return Field delimiter (e.g., "," or ";")
   */
  public String getDelimiterField() {
    return delimiterField;
  }

  /**
   * Validates whether this template can process a CSV file with the given header line. Creates column mappings if all
   * required headers are found in the CSV.
   * 
   * @param headerLine First line of CSV containing column headers
   * @return true if all template columns are found in the CSV header
   */
  public boolean isValidTemplateForForm(String headerLine) {
    String[] headerFields = StringUtils.splitByWholeSeparatorPreserveAllTokens(headerLine, delimiterField);
    columnPropertyMapping = new HashMap<>();
    propertyColumnMapping = new HashMap<>();
    for (int i = 0; i < headerFields.length; i++) {
      String property = columnHeaderNamePropertyMap.get(headerFields[i].strip());
      if (property != null) {
        columnPropertyMapping.put(i, property);
        propertyColumnMapping.put(property, i);
        if (property.equals(ImportProperties.ORDER)) {
          this.orderSupport = true;
        }
      }
    }
    return columnPropertyMapping.size() == columnHeaderNamePropertyMap.size();
  }

  /**
   * Validates CSV-specific template configuration including required delimiter and unique template ID.
   * 
   * @param dataViolationException Exception to collect validation errors
   */
  @Override
  protected void validateTemplate(final DataViolationException dataViolationException) {
    if (delimiterField == null) {
      dataViolationException.addDataViolation(CONF_DELIMITER_FIELD, "gt.imptemplate.missing.delimiter", null, false);
    }
    if (templateId == null) {
      dataViolationException.addDataViolation(CONF_TEMPLATE_ID, "gt.imptemplate.missing.csv.id", null, false);
    } else {
      Optional<TemplateIdPurposeCsv> templateIdPurposeCsvOpt = templateIdPurposeCsvList.stream()
          .filter(tp -> tp.getTemplateId().equals(templateId)
              && (importTransactionTemplate.getIdTransactionImportTemplate() == null || !importTransactionTemplate
                  .getIdTransactionImportTemplate().equals(tp.getIdTransactionImportTemplate())))
          .findFirst();
      if (templateIdPurposeCsvOpt.isPresent()) {
        dataViolationException.addDataViolation(CONF_TEMPLATE_ID, "gt.imptemplate.notunique.csv.id",
            templateIdPurposeCsvOpt.get().getTemplatePurpose(), false);
      }
    }
    super.validateTemplate(dataViolationException);
  }

  /**
   * Returns whether this template supports transaction order linking. Order support enables grouping related
   * transaction rows together.
   * 
   * @return true if order field is mapped in the template
   */
  public boolean isOrderSupport() {
    return orderSupport;
  }

  /**
   * Returns the property name that contains bond pricing information.
   * 
   * @return Bond property name (e.g., "quotation")
   */
  public String getBondProperty() {
    return bondProperty;
  }

  /**
   * Returns the text indicator that marks bond percentage pricing.
   * 
   * @return Bond indicator text (e.g., "%")
   */
  public String getBondIndicator() {
    return bondIndicator;
  }

  /**
   * Returns the unique template identifier for CSV template selection.
   * 
   * @return Template ID number
   */
  public int getTemplateId() {
    return templateId;
  }

  /**
   * Returns the mapping from CSV column indices to transaction property names. Only populated after successful
   * validation against a CSV header.
   * 
   * @return Map of column index to property name
   */
  public Map<Integer, String> getColumnPropertyMapping() {
    return columnPropertyMapping;
  }

  /**
   * Returns rules for ignoring CSV rows based on field values.
   * 
   * @return Map of field name to ignore pattern
   */
  public Map<String, String> getIgnoreLineByFieldValueMap() {
    return ignoreLineByFieldValueMap;
  }

  /**
   * Returns the mapping from transaction property names to CSV column indices. Only populated after successful
   * validation against a CSV header.
   * 
   * @return Map of property name to column index
   */
  public Map<String, Integer> getPropertyColumnMapping() {
    return propertyColumnMapping;
  }

}
