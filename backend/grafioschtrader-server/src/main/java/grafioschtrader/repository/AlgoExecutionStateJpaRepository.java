package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoExecutionState;

/** Internal fill state; no generic REST write endpoint. */
public interface AlgoExecutionStateJpaRepository extends JpaRepository<AlgoExecutionState, Integer> {
  List<AlgoExecutionState> findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyOrderByLifecycle(Integer tenant,
      Integer strategy, Integer security);

  long countByIdTenant(Integer tenant);

  void deleteByIdTenant(Integer tenant);
}
