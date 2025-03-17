package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.TradingPlatformPlan;

public interface TradingPlatformPlanJpaRepository extends JpaRepository<TradingPlatformPlan, Integer>,
    TradingPlatformPlanJpaRepositoryCustom, UpdateCreateJpaRepository<TradingPlatformPlan> {

}
