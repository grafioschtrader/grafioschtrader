package grafioschtrader.platform.swissquote;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImportCSV;

/**
 *
 * The is no special implementation, i is intended as an example.
 *
 * @author Hugo Graf
 *
 */
public class SwissqouteTransactionImportCSV extends GenericTransactionImportCSV {

  public SwissqouteTransactionImportCSV(final ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, uploadFile, importTransactionTemplateList);
  }

}
