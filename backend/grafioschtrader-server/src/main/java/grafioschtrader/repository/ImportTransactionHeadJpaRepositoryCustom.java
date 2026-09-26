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

  /**
   * Deletes all transactions created from the positions of an import head, newest first, so that the whole import can
   * be carried out again. The import positions are kept and become importable again, including the pairing of
   * connected cash-account transfers.
   *
   * <p>
   * The rollback is all or nothing and is refused when another transaction of the tenant is dated on or after the day
   * of the earliest imported transaction, when an imported transaction lies within a closed period, or when an imported
   * transaction is referenced by a simulation opening, a security action, a security transfer or a standing order.
   * </p>
   *
   * @param idTransactionHead the import head of the current tenant
   * @return the number of deleted transactions, 0 when the head has no imported positions
   * @throws SecurityException if the head does not belong to the current tenant
   */
  int rollbackImportedTransactions(Integer idTransactionHead);

}
