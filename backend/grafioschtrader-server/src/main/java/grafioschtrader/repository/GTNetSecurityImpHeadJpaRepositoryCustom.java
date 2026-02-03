package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNetSecurityImpHead;

/**
 * Custom repository interface for GTNetSecurityImpHead operations beyond standard CRUD.
 */
public interface GTNetSecurityImpHeadJpaRepositoryCustom extends BaseRepositoryCustom<GTNetSecurityImpHead> {

  /**
   * Deletes an import header and all associated positions with tenant verification.
   *
   * @param id the header ID to delete
   * @param idTenant the tenant ID for access verification
   * @return number of deleted records
   */
  int delEntityWithTenant(Integer id, Integer idTenant);

  /**
   * Queues a background job to import securities from GTNet, preventing duplicates.
   * Checks if a pending job already exists for this header before creating a new one.
   *
   * <p>
   * If idTransactionHead is provided (non-null), the task will also auto-assign
   * linked securities to matching ImportTransactionPos entries after successful import.
   *
   * @param idGtNetSecurityImpHead the import header ID
   * @param idTenant the tenant ID for verification
   * @param idUser the user ID to set as created_by on imported securities
   * @param idTransactionHead optional import transaction head ID to indicate context from transaction import;
   *                          if non-null, matching ImportTransactionPos entries will be auto-updated
   * @return true if a new job was queued, false if a pending job already exists
   */
  boolean queueImportJobIfNotExists(Integer idGtNetSecurityImpHead, Integer idTenant, Integer idUser,
      Integer idTransactionHead);
}
