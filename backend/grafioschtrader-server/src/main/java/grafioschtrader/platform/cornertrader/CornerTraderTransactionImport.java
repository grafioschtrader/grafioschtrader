package grafioschtrader.platform.cornertrader;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImport;
import grafioschtrader.platform.GenericTransactionImportPDF;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 *
 * The is no special implementation, i is intended as an example.
 *
 * @author Hugo Graf
 *
 */
@Component
public class CornerTraderTransactionImport extends GenericTransactionImport {

  public CornerTraderTransactionImport() {
    super("cornertrader", "CornerTrader");
  }

  @Override
  public void importGTTransform(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale) {

    GenericTransactionImportPDF gti = new CornerTraderTransactionImportPDF(importTransactionHead,
        importTransactionTemplateList);
    gti.importGTTransform(uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, userLocale);
  }
}