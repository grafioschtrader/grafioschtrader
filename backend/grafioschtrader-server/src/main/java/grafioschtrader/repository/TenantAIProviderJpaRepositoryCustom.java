package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.TenantAIProvider;

/** Validated changes of home-tenant model credentials. */
public interface TenantAIProviderJpaRepositoryCustom extends BaseRepositoryCustom<TenantAIProvider> {
  int delEntityWithTenant(Integer id, Integer idTenant);
}
