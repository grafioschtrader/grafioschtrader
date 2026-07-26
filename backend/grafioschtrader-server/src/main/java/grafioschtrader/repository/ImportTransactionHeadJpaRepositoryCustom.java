package grafioschtrader.repository;

import org.springframework.web.multipart.MultipartFile;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.repository.ImportTransactionHeadJpaRepositoryImpl.SuccessFailedDirectImportTransaction;

public interface ImportTransactionHeadJpaRepositoryCustom extends BaseRepositoryCustom<ImportTransactionHead> {

  /**
   * Upload of different kind of transaction files with a existing transaction head record.
   */
  void uploadCsvPdfTxtFileSecurityAccountTransactions(Integer idTransactionHead, MultipartFile[] uploadFiles,
      Integer idTransactionImportTemplate) throws Exception;

  /**
   * Upload one or more PDF/CSV/Txt files, each for a single transaction.
   *
   * @param idSecuritycasshaccount the securities account the documents are imported into
   * @param uploadFiles            the uploaded documents
   * @param useGtPlatform          when true the import templates are taken from the tenant's Grafioschtrader import
   *                               platform instead of the securities account's trading platform mapping
   */
  SuccessFailedDirectImportTransaction uploadPdfFileSecurityAccountTransactions(Integer idSecuritycasshaccount,
      MultipartFile[] uploadFiles, boolean useGtPlatform) throws Exception;

  int delEntityWithTenant(Integer id, Integer idTenant);

}
