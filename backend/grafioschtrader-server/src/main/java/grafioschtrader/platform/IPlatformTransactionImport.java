package grafioschtrader.platform;

import java.util.List;
import java.util.Locale;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

public interface IPlatformTransactionImport {

  String getID();

  String getReadableName();

  void importCSV(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer idTransactionImportTemplate) throws Exception;

  void importSinglePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception;

  void importMultiplePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile[] uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception;

  /**
   * Import a text file which was produced by Grafioschtrader Transform.
   *
   * @param importTransactionHead
   * @param uploadFile
   * @param importTransactionTemplateList
   * @param importTransactionPosJpaRepository
   * @param securityJpaRepository
   * @param importTransactionPosFailedJpaRepository
   */
  void importGTTransform(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale);

}
