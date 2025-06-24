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
 * Generic implementation for importing financial transactions from various file formats including PDF and CSV.
 * 
 * <p>This class serves as the base implementation for transaction import operations, providing support for
 * multiple file formats and import scenarios. It implements a delegation pattern where the actual import
 * work is performed by specialized importer classes (GenericTransactionImportPDF and GenericTransactionImportCSV)
 * while this class handles the orchestration and provides a unified interface for all import operations.</p>
 * 
 * <h3>Supported Import Formats</h3>
 * <p>The class supports importing transaction data from PDF documents (both single and multiple files) and
 * CSV files. Each format uses specialized processing logic appropriate for the file type, with PDF imports
 * supporting form-based extraction and CSV imports handling structured data parsing.</p>
 */ 
public class GenericTransactionImport extends BaseTransactionImport implements IPlatformTransactionImport {

  /**
   * Creates a generic transaction import instance with specified platform identification.
   * 
   * <p>This constructor allows for custom platform identification, enabling the creation of
   * specialized instances while maintaining the generic import functionality. The ID and readable
   * name are used for platform identification and user interface display purposes.</p>
   * 
   * @param id Unique identifier for the import platform, used for internal reference and configuration
   * @param readableName Human-readable name for the platform, displayed in user interfaces
   */
  public GenericTransactionImport(final String id, final String readableName) {
    super(id, readableName);
  }

  /**
   * Creates a default generic transaction import instance with standard identification.
   * 
   * <p>This default constructor creates an instance with "generic" as the platform ID and "Generic"
   * as the readable name, suitable for general-purpose transaction import operations.</p>
   */
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
