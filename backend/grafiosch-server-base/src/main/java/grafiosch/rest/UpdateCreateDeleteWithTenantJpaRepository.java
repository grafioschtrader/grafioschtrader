package grafiosch.rest;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * The deletion of the entity will only happened when the entity belongs to a certain tenant.
 *
 * @param <T>
 */
@NoRepositoryBean
public interface UpdateCreateDeleteWithTenantJpaRepository<T> extends UpdateCreateJpaRepository<T> {

  int delEntityWithTenant(Integer id, Integer idTenant);

}
