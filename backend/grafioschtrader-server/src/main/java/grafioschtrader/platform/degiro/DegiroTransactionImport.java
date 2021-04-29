package grafioschtrader.platform.degiro;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImport;
import grafioschtrader.platform.GenericTransactionImportCSV;

@Component
public class DegiroTransactionImport extends GenericTransactionImport {

  public DegiroTransactionImport() {
    super("degiro", "DEGIRO");
  }

  @Override
  public GenericTransactionImportCSV getCSVImporter(ImportTransactionHead importTransactionHead,
      MultipartFile uploadFile, List<ImportTransactionTemplate> importTransactionTemplateList) {
    return new DegiroTransactionImportCSV(importTransactionHead, uploadFile, importTransactionTemplateList);
  }

}
