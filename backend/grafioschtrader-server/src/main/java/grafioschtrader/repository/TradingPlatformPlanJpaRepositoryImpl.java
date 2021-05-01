package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.TradingPlatformPlan;

public class TradingPlatformPlanJpaRepositoryImpl extends BaseRepositoryImpl<TradingPlatformPlan>
    implements TradingPlatformPlanJpaRepositoryCustom {

  @Autowired
  TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  @Override
  public TradingPlatformPlan saveOnlyAttributes(TradingPlatformPlan tradingPlatformPlan,
      TradingPlatformPlan existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {

    return RepositoryHelper.saveOnlyAttributes(tradingPlatformPlanJpaRepository, tradingPlatformPlan, existingEntity,
        updatePropertyLevelClasses);
  }

}
