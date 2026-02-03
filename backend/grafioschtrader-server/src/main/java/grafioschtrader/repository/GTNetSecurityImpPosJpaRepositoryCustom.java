package grafioschtrader.repository;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;

/**
 * Custom repository interface for GTNetSecurityImpPos operations beyond standard CRUD.
 */
public interface GTNetSecurityImpPosJpaRepositoryCustom {

  /**
   * Finds all positions for a header with tenant verification through the header.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @param idTenant the tenant ID for access verification
   * @return list of positions if tenant has access, empty list otherwise
   */
  List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHeadAndIdTenant(Integer idGtNetSecurityImpHead, Integer idTenant);

  /**
   * Saves or updates a position with tenant verification through the header.
   *
   * @param entity the position to save
   * @param idTenant the tenant ID for access verification
   * @return the saved position
   * @throws SecurityException if tenant doesn't have access to the header
   */
  GTNetSecurityImpPos saveWithTenantCheck(GTNetSecurityImpPos entity, Integer idTenant);

  /**
   * Deletes a position with tenant verification through the header.
   *
   * @param id the position ID to delete
   * @param idTenant the tenant ID for access verification
   * @return number of deleted records
   */
  int deleteWithTenantCheck(Integer id, Integer idTenant);

  /**
   * Uploads and processes a CSV file containing GTNet security import positions.
   * Skips duplicates (same ISIN + currency within the same head).
   *
   * @param idGtNetSecurityImpHead the header ID to associate positions with
   * @param uploadFiles array of uploaded CSV files (only first file is processed)
   * @param idTenant the tenant ID for access verification
   * @return upload result statistics
   * @throws Exception if file processing fails
   */
  UploadHistoryquotesSuccess uploadCSV(Integer idGtNetSecurityImpHead, MultipartFile[] uploadFiles,
      Integer idTenant) throws Exception;

  /**
   * Deletes the linked security from a position and removes it from the system.
   * The position itself remains and can be queried again via GTNet.
   *
   * <p>This operation:
   * <ol>
   *   <li>Unlinks the security from the position (sets security reference to null)</li>
   *   <li>Deletes all history quotes for the security</li>
   *   <li>Deletes the security entity from the database</li>
   * </ol>
   *
   * @param idGtNetSecurityImpPos the position ID
   * @param idTenant the tenant ID for access verification
   * @return the updated position with security set to null
   * @throws SecurityException if tenant doesn't have access or position has no linked security
   */
  GTNetSecurityImpPos deleteLinkedSecurity(Integer idGtNetSecurityImpPos, Integer idTenant);

  /**
   * Creates GTNet security import positions from an import transaction head.
   * Reads ImportTransactionPos entries where security is null and (isin or symbolImp exists),
   * then creates corresponding GTNetSecurityImpPos entries. Can create a new header or add to an existing one.
   * Skips duplicates (same ISIN + currency) and entries with invalid data.
   *
   * @param idTransactionHead the import transaction head ID to read positions from
   * @param idGtNetSecurityImpHead optional existing header ID to add positions to (null to create new)
   * @param headName name for new header (required if idGtNetSecurityImpHead is null)
   * @param idTenant the tenant ID for access verification
   * @return the GTNet security import header (existing or newly created)
   * @throws SecurityException if tenant doesn't have access to the import transaction head
   */
  GTNetSecurityImpHead createFromImportTransactionHead(Integer idTransactionHead, Integer idGtNetSecurityImpHead,
      String headName, Integer idTenant);
}
