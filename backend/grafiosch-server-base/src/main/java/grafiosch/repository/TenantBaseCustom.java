package grafiosch.repository;

import grafiosch.dto.AccountDeletionEligibility;
import grafiosch.entities.User;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom repository interface for tenant-specific data operations. Provides functionality for data deletion and export
 * operations.
 */
public interface TenantBaseCustom {

  /**
   * Deletes all personal data and user account for the currently authenticated user. This operation removes all
   * user-specific data from the system and cannot be undone.
   *
   * @throws Exception if the deletion operation fails or user is not authenticated
   */
  void deleteMyDataAndUserAccount() throws Exception;

  /**
   * Read-only evaluation of whether the given user may delete their own account. Returns the same conditions that
   * {@link #deleteMyDataAndUserAccount()} enforces (still managing clients, or others still reading the home tenant), so
   * the frontend can warn the user up front instead of submitting a deletion that would be rejected. Changes no data.
   *
   * @param user the user whose self-deletion eligibility is evaluated
   * @return the deletion eligibility (DELETABLE, HAS_CLIENTS or HAS_VIEWERS)
   */
  AccountDeletionEligibility getAccountDeletionEligibility(User user);

  /**
   * Deletes all data, the tenant and the user account of the given managed client. Uses the same complete-deletion
   * engine as {@link #deleteMyDataAndUserAccount()} but for an explicitly supplied user instead of the authenticated
   * user, so an advisor can remove a client they manage. Authorization (the advisor must hold a MANAGE grant on the
   * client's tenant) must be checked by the caller. Cannot be undone.
   *
   * @param clientUser the read-only client user whose tenant and account are to be deleted
   * @throws Exception if the deletion operation fails
   */
  void deleteManagedClientData(User clientUser) throws Exception;

  /**
   * Exports all personal data for the currently authenticated user as a ZIP file. The ZIP contains SQL scripts with
   * table structures and user data.
   * 
   * @param response the HTTP response to write the ZIP file to
   * @throws Exception if the export operation fails or user is not authenticated
   */
  void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception;
}
