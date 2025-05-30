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
   */
  SuccessFailedDirectImportTransaction uploadPdfFileSecurityAccountTransactions(Integer idSecuritycasshaccount,
      MultipartFile[] uploadFiles) throws Exception;

  int delEntityWithTenant(Integer id, Integer idTenant);

}
