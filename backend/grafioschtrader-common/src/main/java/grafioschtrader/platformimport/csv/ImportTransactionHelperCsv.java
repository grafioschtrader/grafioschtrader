package grafioschtrader.platformimport.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import grafioschtrader.entities.ImportTransactionTemplate;

public abstract class ImportTransactionHelperCsv {

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
