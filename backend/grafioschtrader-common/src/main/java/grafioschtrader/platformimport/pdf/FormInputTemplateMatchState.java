package grafioschtrader.platformimport.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import grafiosch.common.ValueFormatConverter;
import grafioschtrader.platformimport.ImportProperties;

/**
 * Contains the state of a single form/template parsing. First the non optional properties are parsed. Afterwards the
 * optional properties are parsed.
 */
public class FormInputTemplateMatchState {
  
  /** Template configuration containing parsing rules and property definitions */
  private TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT;
  
  /** Currently active property being matched against form input */
  private PropertyWithOptionsConfiguration actProperty;
  
  /** Index of the current property in the properties list */
  private int propertyIndex = -1;
  
  /** Ordered list of all properties to match from the template */
  private List<PropertyWithOptionsConfiguration> properties;
  
  /** Flag indicating whether the form has been successfully matched */
  private boolean formMatches = false;
  
  /** Primary container for extracted import properties and transaction data */
  private ImportProperties importPropertiesPrimary;
  
  /** List of all import properties, including additional instances from table rows */
  private List<ImportProperties> importPropertiesList = new ArrayList<>();
  
  /** Converter for locale-aware formatting of dates, numbers, and other data types */
  private ValueFormatConverter valueFormatConverter;
  
  /** List of optional properties awaiting deferred scanning */
  private List<OptionalProperties> openOptionalProperies = new ArrayList<>();
 
  /** Maximum row processed to prevent redundant parsing */
  private int maxRow = 0;
  
  /** Name of the last successfully matched property for tracking purposes */
  private String lastMatchingProperty;

  /**
   * Initializes the form matching state with template configuration and creates the primary import properties container.
   * 
   * <p>This constructor establishes the foundation for template matching by configuring all necessary components
   * including the value format converter, property list, and primary import properties container. The initialization
   * process prepares the state machine for sequential property matching against form input data.</p>
   * 
   * @param templateConfigurationPDFasTXT The PDF template configuration containing all parsing rules, property
   *                                     definitions, formatting specifications, and validation criteria for
   *                                     the target PDF format
   * @param fileNumber Unique identifier for the file being processed, used for tracking and reference in
   *                   the resulting import properties
   */
  public FormInputTemplateMatchState(TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT, Integer fileNumber) {
    this.templateConfigurationPDFasTXT = templateConfigurationPDFasTXT;
    importPropertiesPrimary = new ImportProperties(templateConfigurationPDFasTXT.getTransactionTypesMap(),
        templateConfigurationPDFasTXT.getImportKnownOtherFlagsSet().clone(), fileNumber,
        templateConfigurationPDFasTXT.getIgnoreTaxOnDivInt());
    importPropertiesList.add(importPropertiesPrimary);

    valueFormatConverter = new ValueFormatConverter(templateConfigurationPDFasTXT.getDateFormat(),
        templateConfigurationPDFasTXT.getTimeFormat(), templateConfigurationPDFasTXT.getThousandSeparators(),
        templateConfigurationPDFasTXT.getThousandSeparatorsPattern(),
        templateConfigurationPDFasTXT.getDecimalSeparator(), templateConfigurationPDFasTXT.getLocale());

    properties = templateConfigurationPDFasTXT.getPropertyWithOptionsConfiguration();
    setNextActProperty(0);
  }

  /**
   * Processes a single row of form input against the current template property, advancing the parsing state upon successful matches.
   * 
   * <p>This method represents the core of the template matching engine, handling both simple property matching
   * and complex table structure parsing. It intelligently determines the parsing approach based on the current
   * property configuration and advances the internal state when matches are found.</p>
   * 
   * <h4>Processing Logic</h4>
   * <p>The method employs different parsing strategies based on the current property type:</p>
   * <ul>
   *   <li><b>Table Row Parsing:</b> When the current property represents the first column of a table structure,
   *       initiates comprehensive table row matching with pattern recognition and repeating data extraction</li>
   *   <li><b>Single Property Matching:</b> For individual properties, performs direct pattern matching and
   *       value extraction with proper type conversion</li>
   * </ul>
   * 
   * @param formInputLines The complete array of text lines extracted from the PDF form, representing the
   *                      raw input data to be parsed against the template
   * @param row The current row index being processed, used for position tracking and state management
   */
  public void matchTemplatesProperties(String[] formInputLines, int row) throws Exception {
    if (row >= maxRow) {
      if (actProperty.isFirstColumnTable()) {
        maxRow = matchTableRow(formInputLines, row);
      } else {
        maxRow = matchSingleRequiredProperty(formInputLines, row);
      }
    }
  }

  /**
   * Performs deferred scanning for optional template properties across the specified ranges of form input lines.
   * 
   * <p>
   * This method implements the deferred optional property matching strategy, allowing flexible template matching even
   * when optional data appears in unexpected locations or is entirely absent. The scanning process examines previously
   * defined ranges where optional properties might appear, attempting to match them without disrupting the main parsing
   * flow.
   * </p>
   * 
   * <h4>Range Management</h4>
   * <p>
   * Optional property ranges are automatically calculated based on the positions of surrounding required properties. If
   * no explicit end range is defined, the scanning continues to the end of the form input. This approach ensures
   * comprehensive coverage while maintaining parsing efficiency.
   * </p>
   * 
   * <h4>Integration with Main Parsing</h4>
   * <p>
   * This scanning occurs after the main parsing pass has completed, ensuring that all required properties have been
   * processed and that optional property ranges are properly defined. The results are integrated into the primary
   * import properties container.
   * </p>
   * 
   * @param formInputLines The complete array of text lines from the PDF form to scan for optional property matches
   */
  public void scanForOptionalProperties(String[] formInputLines) throws Exception {
    for (OptionalProperties op : openOptionalProperies) {
      propertyIndex = op.propertyIndex;
      actProperty = properties.get(propertyIndex);
      boolean matches = false;
      int endRow = op.endRowNo == null ? formInputLines.length - 1 : op.endRowNo;
      for (int row = op.startRowNo; row <= endRow && !matches; row++) {
        matches = actProperty.matchePropertyAndSetValue(formInputLines, row, importPropertiesPrimary,
            valueFormatConverter);
      }
    }
  }

  public List<ImportProperties> getImportPropertiesList() {
    return importPropertiesList;
  }

  public boolean isFormMatches() {
    return formMatches;
  }

  /**
   * Handles matching of single required properties that may appear on the same template line.
   * 
   * <p>This method addresses scenarios where multiple template properties are defined on the same line number,
   * requiring sequential matching within the same input row. It continues processing properties as long as
   * they belong to the same template line and successful matches are found.</p>
   * 
   * @param formInputLines The array of text lines from the PDF form
   * @param row The current row index being processed
   * @return The row index after processing, typically unchanged for single-row property matching
   * @throws Exception if property matching fails or state advancement encounters errors
   */
  private int matchSingleRequiredProperty(String[] formInputLines, int row) throws Exception {
    int templateLineNo = actProperty.getLineNo();
    boolean matches = false;
    while (!formMatches && actProperty.getLineNo() == templateLineNo && (matches = actProperty
        .matchePropertyAndSetValue(formInputLines, row, importPropertiesPrimary, valueFormatConverter))) {
      if (matches) {
        lastMatchingProperty = actProperty.getFieldName();
      }
      setNextActProperty(row);
    }
    return row;
  }

  /**
   * Advances the parsing state to the next template property, managing optional property tracking and form completion detection.
   * 
   * <p>This method orchestrates state transition logic, handling the advancement through template properties
   * while properly managing optional properties and detecting when the entire form has been successfully parsed.
   * Optional properties are registered for deferred scanning with appropriate row range information.</p>
   * 
   * @param row The current row position, used for optional property range calculation
   */
  private void setNextActProperty(int row) {
    wasBeforeOptionalProperty(row);
    if (propertyIndex + 1 < properties.size()) {
      do {
        actProperty = properties.get(++propertyIndex);
        if (actProperty.isOptional()) {
          openOptionalProperies.add(new OptionalProperties(row, propertyIndex));
        }
      } while (actProperty.isOptional() && propertyIndex + 1 < properties.size());
      if (actProperty.isOptional()) {
        formMatches();
      }
    } else {
      formMatches();
    }
  }

  /**
   * Updates end row boundaries for preceding optional properties that were left open during main parsing.
   * 
   * <p>This method ensures that optional properties have properly defined search ranges by setting end boundaries
   * when the parser advances past their potential locations.</p>
   * 
   * @param endRow The row number to use as the end boundary for open optional properties
   */
  private void wasBeforeOptionalProperty(int endRow) {
    if (propertyIndex > 0 && properties.get(propertyIndex - 1).isOptional()) {
      int i = openOptionalProperies.size() - 1;
      while (i >= 0 && openOptionalProperies.get(i).endRowNo == null) {
        openOptionalProperies.get(i).endRowNo = endRow;
        i--;
      }
    }
  }

  private void formMatches() {
    this.formMatches = true;
  }

  /**
  * Processes repeating table rows by matching structured data patterns and creating multiple import property instances.
  * 
  * <p>This method handles the most complex parsing scenario where PDF data is organized in table format with
  * repeating rows following a consistent pattern. It dynamically generates regex patterns for table rows,
  * matches multiple data rows, and creates separate ImportProperties instances for each row of data. The method
  * supports tables with optional columns by generating multiple regex patterns for flexible matching.</p>
  * 
  * @param formInputLines The array of text lines from the PDF form
  * @param row The current row index where table parsing should begin
  * @return The row index after processing the entire table structure
  * @throws Exception if table pattern generation fails, regex matching errors occur, or value conversion fails
  */
  private int matchTableRow(String[] formInputLines, int row) throws Exception {
    if (actProperty.matchePropertyAndSetValue(formInputLines, row, null, null)) {
      int startRow = row;
      int lastColumnIndex = getLastTableColumnIndex(actProperty.getLineNo());
      // PropertyWithOptionsConfiguration lastColumnProperty =
      // properties.get(lastColumnIndex);
      int columsInRowTemplate = templateConfigurationPDFasTXT.getColumnNoOnProperty(actProperty);
      Pattern[] tableRowPatterns = getTableRowPattern(lastColumnIndex);
      int requiredColumns = lastColumnIndex - propertyIndex + 1;
      boolean successRowRead = false;
      do { // For each row, at least one row
        int optionalIndex = 0;
        if (tableRowPatterns.length == 2) {
          String[] words = formInputLines[row].split("\\s+");
          optionalIndex = columsInRowTemplate == words.length ? 0 : 1;
          requiredColumns = lastColumnIndex - propertyIndex + 1 - optionalIndex;
        }

        Matcher matcher = tableRowPatterns[optionalIndex].matcher(formInputLines[row]);
        if (matcher.find() && matcher.groupCount() == requiredColumns) {
          ImportProperties ipRepeated = this.importPropertiesPrimary;
          if (row > startRow) {
            ipRepeated = new ImportProperties(null, null, null);
            importPropertiesList.add(ipRepeated);
          }
          for (int proertyCounter = propertyIndex, i = 1; i <= matcher.groupCount(); proertyCounter++, i++) {
            PropertyWithOptionsConfiguration pwoc = properties.get(proertyCounter);
            if (optionalIndex == 1 && pwoc.isOptional()) {
              pwoc = properties.get(++proertyCounter);
            }
            valueFormatConverter.convertAndSetValue(ipRepeated, pwoc.getFieldName(), matcher.group(i),
                pwoc.getDataType());
            lastMatchingProperty = pwoc.getFieldName();
          }
          successRowRead = true;
          row++;
        } else {
          successRowRead = false;
        }

      } while (successRowRead);
      if (row > startRow) {
        // There was at least one successfully match
        propertyIndex = lastColumnIndex;
        setNextActProperty(row);
      }
    }
    return row;
  }

  /**
   * Determines the index of the last property that belongs to the same table row as the current property.
   * 
   * <p>This method calculates the column span of a table row by finding all properties that share the same
   * line number, which is essential for generating appropriate regex patterns and managing table parsing boundaries.</p>
   * 
   * @param matchRow The line number of the table row being analyzed
   * @return The index of the last property in the table row
   */
  private int getLastTableColumnIndex(int matchRow) {
    int i = propertyIndex;
    while (i < properties.size() && properties.get(i).getLineNo() == matchRow) {
      i++;
    }
    return i - 1;
  }

  /**
   * Generates regex patterns for parsing table rows, supporting both complete and partial row structures with optional columns.
   * 
   * <p>This method creates sophisticated regex patterns that can handle table structures with optional columns.
   * It generates one or two patterns depending on whether optional columns are present, allowing flexible
   * matching of table rows that may have varying column counts. The dual-pattern approach enables parsing
   * of tables where some rows may omit optional columns while maintaining structural consistency.</p>
   * 
   * @param lastColumnIndex The index of the last column in the table row
   * @return Array containing one or two compiled regex patterns for table row matching
   */
  private Pattern[] getTableRowPattern(int lastColumnIndex) {
    boolean includeOptional = true;
    boolean hasOptional = false;
    Pattern[] patterns = null;
    for (int optinalIndex = 0; optinalIndex == 0 || optinalIndex <= 1 && hasOptional; optinalIndex++) {

      String regex = "";
      for (int pIndex = propertyIndex; pIndex <= lastColumnIndex; pIndex++) {
        PropertyWithOptionsConfiguration property = properties.get(pIndex);
        if (optinalIndex == 0 && property.isOptional()) {
          hasOptional = true;
        }
        if (!property.isOptional() || (property.isOptional() && includeOptional)) {
          regex = regex + property.getRegexPattern();
        }
        if (pIndex < lastColumnIndex - optinalIndex) {
          regex = regex + "\\s";
          // Add regex for static column text in the table row
          int startIndex = property.getPropertyColumnIncludeNext();
          int endIndex = properties.get(pIndex + 1).getPropertyColumnIncludePrevious();

          for (int i = startIndex + 1; i < endIndex; i++) {
            regex = regex + "[^\\s]*" + "\\s";
          }
        }
      }
      if (optinalIndex == 0) {
        patterns = new Pattern[hasOptional ? 2 : 1];
      }
      patterns[optinalIndex] = Pattern.compile(regex);
      includeOptional = false;
    }
    return patterns;
  }

  public TemplateConfigurationPDFasTXT getTemplateConfigurationPDFasTXT() {
    return templateConfigurationPDFasTXT;
  }

  public String getLastMatchingProperty() {
    return lastMatchingProperty;
  }

  /**
   * Container for tracking optional property scanning ranges and metadata during deferred processing.
   * 
   * <p>This class maintains the necessary information for optional property scanning, including the
   * row range where the property might appear and the property index for reference. The start row
   * is set when the optional property is first encountered, and the end row is determined when
   * parsing advances past the property's potential location.</p>
   */
  static class OptionalProperties {
    /** The starting row number where scanning for this optional property should begin */
    public int startRowNo;
    
    /** The ending row number where scanning should stop, or null to scan to end of input */
    public Integer endRowNo;
    
    /** Index of the optional property in the template's property list for reference during scanning */
    public int propertyIndex;

    public OptionalProperties(int startRowNo, int propertyIndex) {
      this.startRowNo = startRowNo;
      this.propertyIndex = propertyIndex;
    }

  }

}
