package grafiosch.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.integration.entities.Tenant;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface TenantJpaRepository
    extends JpaRepository<Tenant, Integer>, TenantJpaRepositoryCustom, UpdateCreateJpaRepository<Tenant> {

}
