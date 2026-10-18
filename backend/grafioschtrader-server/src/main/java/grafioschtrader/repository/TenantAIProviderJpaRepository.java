package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.TenantAIProvider;

/** Home-tenant-scoped provider configurations, including inactive rows for the settings dialog. */
public interface TenantAIProviderJpaRepository extends JpaRepository<TenantAIProvider, Integer>,
    TenantAIProviderJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<TenantAIProvider> {
  List<TenantAIProvider> findByIdTenantOrderByPriorityAscIdTenantAIProviderAsc(Integer idTenant);

  Optional<TenantAIProvider> findByIdTenantAIProviderAndIdTenant(Integer id, Integer idTenant);

  Optional<TenantAIProvider> findByIdTenantAndProvider(Integer idTenant, String provider);
}
