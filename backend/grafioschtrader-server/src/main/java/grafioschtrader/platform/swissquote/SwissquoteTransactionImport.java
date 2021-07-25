package grafioschtrader.platform.swissquote;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImport;
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
public class SwissquoteTransactionImport extends GenericTransactionImport {

  public SwissquoteTransactionImport() {
    super("swissquote", "Swissquote");
  }

  @Override
  public void importCSV(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer idTransactionImportTemplate) throws IOException {
    SwissqouteTransactionImportCSV stic = new SwissqouteTransactionImportCSV(importTransactionHead, uploadFile,
        importTransactionTemplateList);
    stic.importCSV(importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
        userLocale, idTransactionImportTemplate);
  }

}
