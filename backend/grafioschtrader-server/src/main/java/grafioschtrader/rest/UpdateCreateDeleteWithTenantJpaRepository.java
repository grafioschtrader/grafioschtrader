package grafioschtrader.rest;

/**
 * The deletion of the entity will only happened when the entity belongs to a
 * certain tenant.
 *
 * @author Hugo Graf
 *
 * @param <T>
 */
public interface UpdateCreateDeleteWithTenantJpaRepository<T> extends UpdateCreateJpaRepository<T> {

  int delEntityWithTenant(Integer id, Integer idTenant);

}
