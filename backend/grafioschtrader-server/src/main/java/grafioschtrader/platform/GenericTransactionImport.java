package grafioschtrader.platform;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Base class for import of transactions. For the different platform my exist a
 * specialized version.
 *
 */
public class GenericTransactionImport extends BaseTransactionImport implements IPlatformTransactionImport {

  public GenericTransactionImport(final String id, final String readableName) {
    super(id, readableName);
  }

  public GenericTransactionImport() {
    this("generic", "Generic");
  }

  @Override
  public void importSinglePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    GenericTransactionImportPDF gti = getPDFImporter(importTransactionHead, importTransactionTemplateList);
    gti.importSinglePdfForm(uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, userLocale);
  }

  @Override
  public void importMultiplePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile[] uploadFiles,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    GenericTransactionImportPDF gti = getPDFImporter(importTransactionHead, importTransactionTemplateList);
    gti.importMultiplePdfForm(uploadFiles, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, userLocale);

  }

  @Override
  public void importCSV(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer idTransactionImportTemplate) throws IOException {
    GenericTransactionImportCSV gti = getCSVImporter(importTransactionHead, uploadFile, importTransactionTemplateList);
    gti.importCSV(importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
        userLocale, idTransactionImportTemplate);

  }

  @Override
  public void importGTTransform(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale) {
    GenericTransactionImportPDF gti = getPDFImporter(importTransactionHead, importTransactionTemplateList);
    gti.importGTTransform(uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository, userLocale);
  }

  public GenericTransactionImportPDF getPDFImporter(ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    return new GenericTransactionImportPDF(importTransactionHead, importTransactionTemplateList);
  }

  public GenericTransactionImportCSV getCSVImporter(ImportTransactionHead importTransactionHead,
      MultipartFile uploadFile, List<ImportTransactionTemplate> importTransactionTemplateList) {
    return new GenericTransactionImportCSV(importTransactionHead, uploadFile, importTransactionTemplateList);
  }

}
