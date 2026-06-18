package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.TenantAccess;

/**
 * Repository for {@link TenantAccess} grants that link a user to the additional tenants they may enter.
 *
 * <p>
 * Grants are looked up per request to resolve whether the tenant the request currently operates in is read-only for the
 * user. Because the lookup happens on every request (in the JWT token handler), revoking or downgrading a grant takes
 * effect on the next request without waiting for the JWT to expire.
 * </p>
 */
public interface TenantAccessJpaRepository extends JpaRepository<TenantAccess, Integer> {

  /**
   * Returns all tenant-access grants held by the given user (excluding their home tenant, which is implicit).
   *
   * @param idUser the user whose grants are requested
   * @return the list of grants, possibly empty
   */
  List<TenantAccess> findByIdUser(Integer idUser);

  /**
   * Returns all grants on a specific tenant, used to list the registered users who have been granted access to the
   * owner's tenant (for the shared-viewers management view).
   *
   * @param idTenant the tenant whose grants are requested
   * @return the list of grants, possibly empty
   */
  List<TenantAccess> findByIdTenant(Integer idTenant);

  /**
   * Returns the grant for a specific user/tenant pair, used to resolve the access level for the tenant the request
   * currently operates in.
   *
   * @param idUser   the user
   * @param idTenant the tenant the user wants to access
   * @return the grant if one exists, otherwise empty
   */
  Optional<TenantAccess> findByIdUserAndIdTenant(Integer idUser, Integer idTenant);
}
