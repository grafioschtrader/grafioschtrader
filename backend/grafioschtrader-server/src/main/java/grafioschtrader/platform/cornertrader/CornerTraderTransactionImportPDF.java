package grafioschtrader.platform.cornertrader;

import java.util.List;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImportPDF;

/**
 * The is no special implementation, i is intended as an example.
 */
public class CornerTraderTransactionImportPDF extends GenericTransactionImportPDF {

  public CornerTraderTransactionImportPDF(ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
  }

}
