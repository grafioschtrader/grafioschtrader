package grafioschtrader.platformimport.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.platformimport.ImportProperties;

/**
 * Contains the state of a single form/template parsing. First the non optional
 * properties are parsed. Afterwards the optional properties are parsed.
 *
 * @author Hugo Graf
 *
 */
public class FormInputTemplateMatchState {
  private TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT;
  private PropertyWithOptionsConfiguration actProperty;
  private int propertyIndex = -1;
  private List<PropertyWithOptionsConfiguration> properties;
  private boolean formMatches = false;
  private ImportProperties importPropertiesPrimary;
  private List<ImportProperties> importPropertiesList = new ArrayList<>();
  private ValueFormatConverter valueFormatConverter;
  private List<OptionalProperties> openOptionalProperies = new ArrayList<>();
  private int maxRow = 0;
  private String lastMatchingProperty;

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

  public void matchTemplatesProperties(String[] formInputLines, int row) throws Exception {
    if (row >= maxRow)
      if (actProperty.isFirstColumnTable()) {
        maxRow = matchTableRow(formInputLines, row);
      } else {
        maxRow = matchSingleRequiredProperty(formInputLines, row);
      }
  }

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
   * Maybe there are more then only one property on a single line
   *
   * @param formInputLines
   * @param row
   * @throws Exception
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
   * Matches a repeating lines
   *
   * @param formInputLines
   * @param row
   * @return
   * @throws Exception
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

  private int getLastTableColumnIndex(int matchRow) {
    int i = propertyIndex;
    while (i < properties.size() && properties.get(i).getLineNo() == matchRow) {
      i++;
    }
    return i - 1;
  }

  /**
   * Creates the pattern for a table row.
   *
   * @param lastColumnIndex The number of columns in a row (separated by spaces)
   * @return
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

  static class OptionalProperties {
    public int startRowNo;
    public Integer endRowNo;
    /**
     * Index of optional Property
     */
    public int propertyIndex;

    public OptionalProperties(int startRowNo, int propertyIndex) {
      this.startRowNo = startRowNo;
      this.propertyIndex = propertyIndex;
    }

  }

}
