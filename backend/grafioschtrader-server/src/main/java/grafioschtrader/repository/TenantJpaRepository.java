package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.Tenant;

public interface TenantJpaRepository
    extends JpaRepository<Tenant, Integer>, TenantJpaRepositoryCustom, UpdateCreateJpaRepository<Tenant> {

  @EntityGraph(value = "graph.tenant.portfolios", type = EntityGraphType.FETCH)
  Tenant findByTenantName(String tenantName);

  List<Tenant> findByTenantKindType(byte tenantKindType);

}
