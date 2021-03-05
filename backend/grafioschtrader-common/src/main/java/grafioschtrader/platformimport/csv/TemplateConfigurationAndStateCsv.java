package grafioschtrader.platformimport.csv;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

  /**
   * Contains the header to property mapping of a template
   */
  private Map<String, String> columnHeaderNamePropertyMap = new HashMap<>();
  /**
   * Contains the csv column to property mapping
   */
  private Map<Integer, String> columnPropertyMapping;
  private int templateId;
  private String delimiterField;
  private String bondProperty;
  private String bondIndicator;
  private boolean orderSupport;

  public TemplateConfigurationAndStateCsv(ImportTransactionTemplate importTransactionTemplate, Locale userLocale) {
    super(importTransactionTemplate, userLocale);
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
      String[] propertyIndicator = splitEqual[1].split("|");
      bondProperty = propertyIndicator[0];
      bondIndicator = propertyIndicator[1];
      break;
    }
  }

  public String getDelimiterField() {
    return delimiterField;
  }

  public boolean isValidTemplateForForm(String headerLine) {
    String[] headerFields = StringUtils.splitByWholeSeparatorPreserveAllTokens(headerLine, delimiterField);
    columnPropertyMapping = new HashMap<>();
    for (int i = 0; i < headerFields.length; i++) {
      String property = columnHeaderNamePropertyMap.get(headerFields[i]);
      if (property != null) {
        columnPropertyMapping.put(i, property);
        if (property.equals(ImportProperties.ORDER)) {
          this.orderSupport = true;
        }
      }
    }
    return columnPropertyMapping.size() == columnHeaderNamePropertyMap.size();
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
  
  

}
