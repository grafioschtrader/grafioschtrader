package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface TradingPlatformPlanJpaRepository extends JpaRepository<TradingPlatformPlan, Integer>,
    TradingPlatformPlanJpaRepositoryCustom, UpdateCreateJpaRepository<TradingPlatformPlan> {

}
