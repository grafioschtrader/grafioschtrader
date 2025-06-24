package grafioschtrader.platformimport.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import grafioschtrader.entities.ImportTransactionTemplate;

/**
 * Utility class providing CSV-specific template processing and configuration management for import transaction operations.
 * 
 * <p>This abstract helper class serves as a centralized facility for managing CSV import transaction templates,
 * focusing on the conversion of raw template entities into executable configuration objects that can parse
 * and validate CSV transaction data. The class provides essential template preparation services for the
 * CSV import workflow.</p>
 */ 
public abstract class ImportTransactionHelperCsv {

  /**
   * Transforms a collection of import transaction templates into executable CSV configuration objects with 
   * locale-specific settings and validation.
   * 
   * <p>This method performs the critical transformation from stored template entities to runtime configuration
   * objects, enabling the CSV import system to efficiently process and validate incoming transaction data.
   * Each template is converted into a configuration object that encapsulates both parsing rules and
   * validation logic specific to CSV format requirements.</p>
   */ 
  public static Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> readTemplates(
      List<ImportTransactionTemplate> importTransactionTemplateList, Locale userLocale) {
    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateScannedMap = new HashMap<>();
    for (ImportTransactionTemplate itt : importTransactionTemplateList) {
      TemplateConfigurationAndStateCsv templateConfigurationCsv = new TemplateConfigurationAndStateCsv(itt, userLocale,
          null);
      templateScannedMap.put(templateConfigurationCsv, itt);
      templateConfigurationCsv.parseTemplateAndThrowError(false);
    }
    return templateScannedMap;
  }

}
