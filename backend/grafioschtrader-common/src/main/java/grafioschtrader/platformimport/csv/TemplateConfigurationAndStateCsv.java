package grafioschtrader.platformimport.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.TemplateConfiguration;

/**
 * Read the template configuration. It many also be used to hold the state of
 * parsing a csv file.
 *
 */
public class TemplateConfigurationAndStateCsv extends TemplateConfiguration {

  /**
   * CSV template id, this value is required
   */
  private static final String CONF_TEMPLATE_ID = "templateId";

  /**
   * Delimiter for csv file.
   */
  private static final String CONF_DELIMITER_FIELD = "delimiterField";
  /**
   * The quotation of a bond may differ from the stock quotation. It may have a
   * percentage sign.
   */
  private static final String CONF_BOND_FIELD_INDICATOR = "bond";

  private static final String CONF_IGNORE_LINE_BY_FIELD_VALUE = "ignoreLineByFieldValue";

  /**
   * Contains the header to property mapping of a template
   */
  private Map<String, String> columnHeaderNamePropertyMap = new HashMap<>();
  /**
   * Contains the csv column number to property mapping. It starts with 0.
   */
  private Map<Integer, String> columnPropertyMapping;

  /**
   * Contains the internal property name to column mapping. It starts with 0.
   */
  private Map<String, Integer> propertyColumnMapping;
  private Map<String, String> ignoreLineByFieldValueMap = new HashMap<>();
  private Integer templateId;
  private String delimiterField;
  private String bondProperty;
  private String bondIndicator;
  private boolean orderSupport;
  private List<TemplateIdPurposeCsv> templateIdPurposeCsvList;

  public TemplateConfigurationAndStateCsv(ImportTransactionTemplate importTransactionTemplate, Locale userLocale,
      List<TemplateIdPurposeCsv> templateIdPurposeCsvList) {
    super(importTransactionTemplate, userLocale);
    this.templateIdPurposeCsvList = templateIdPurposeCsvList;
  }

  @Override
  protected void readTemplateProperties(String[] templateLines, int startRowConfig,
      DataViolationException dataViolationException) {
    for (int i = 0; i < startRowConfig; i++) {
      String splitEqual[] = templateLines[i].split("=");
      columnHeaderNamePropertyMap.put(splitEqual[1], splitEqual[0]);
    }
  }

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

  public String getDelimiterField() {
    return delimiterField;
  }

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

  public boolean isOrderSupport() {
    return orderSupport;
  }

  public String getBondProperty() {
    return bondProperty;
  }

  public String getBondIndicator() {
    return bondIndicator;
  }

  public int getTemplateId() {
    return templateId;
  }

  public Map<Integer, String> getColumnPropertyMapping() {
    return columnPropertyMapping;
  }

  public Map<String, String> getIgnoreLineByFieldValueMap() {
    return ignoreLineByFieldValueMap;
  }

  public Map<String, Integer> getPropertyColumnMapping() {
    return propertyColumnMapping;
  }

}
