package grafioschtrader.platform.migros;

import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImport;
import grafioschtrader.platform.GenericTransactionImportPDF;

@Component
public class MigrosTransactionImport extends GenericTransactionImport {

  public MigrosTransactionImport() {
    super("migros", "Migros Bank");
  }

  @Override
  public GenericTransactionImportPDF getPDFImporter(ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    return new MigrosTransactionImportPDF(importTransactionHead, importTransactionTemplateList);
  }

}
