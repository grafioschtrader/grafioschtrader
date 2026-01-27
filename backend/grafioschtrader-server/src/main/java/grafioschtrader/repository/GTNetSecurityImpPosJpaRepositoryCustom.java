package grafioschtrader.repository;

import java.util.List;

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
}
