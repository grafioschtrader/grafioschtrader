package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.GTNetSecurityImpHead;

/**
 * Repository for managing GTNet security import header records. Provides CRUD operations
 * with tenant-level access control.
 */
public interface GTNetSecurityImpHeadJpaRepository extends JpaRepository<GTNetSecurityImpHead, Integer>,
    GTNetSecurityImpHeadJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<GTNetSecurityImpHead> {

  /**
   * Finds all import headers belonging to a specific tenant.
   *
   * @param idTenant the tenant identifier
   * @return list of import headers for the tenant
   */
  List<GTNetSecurityImpHead> findByIdTenant(Integer idTenant);

  /**
   * Finds a specific import header by ID and tenant.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @param idTenant the tenant ID
   * @return the matching header or null if not found
   */
  GTNetSecurityImpHead findByIdGtNetSecurityImpHeadAndIdTenant(Integer idGtNetSecurityImpHead, Integer idTenant);

  /**
   * Deletes a header by ID and tenant, returning the number of deleted rows.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @param idTenant the tenant ID
   * @return number of deleted rows (0 or 1)
   */
  @Transactional
  @Modifying
  int deleteByIdGtNetSecurityImpHeadAndIdTenant(Integer idGtNetSecurityImpHead, Integer idTenant);
}
