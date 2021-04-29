package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.Tenant;
import grafioschtrader.rest.UpdateCreateJpaRepository;

@Repository
public interface TenantJpaRepository
    extends JpaRepository<Tenant, Integer>, TenantJpaRepositoryCustom, UpdateCreateJpaRepository<Tenant> {

  @EntityGraph(value = "graph.tenant.portfolios", type = EntityGraphType.FETCH)
  Tenant findByTenantName(String tenantName);

  List<Tenant> findByTenantKindType(byte tenantKindType);

}
