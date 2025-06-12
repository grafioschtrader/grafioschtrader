package grafiosch.repository;

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
   * Exports all personal data for the currently authenticated user as a ZIP file. The ZIP contains SQL scripts with
   * table structures and user data.
   * 
   * @param response the HTTP response to write the ZIP file to
   * @throws Exception if the export operation fails or user is not authenticated
   */
  void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception;
}
