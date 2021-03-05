package grafioschtrader.repository;

import java.util.Set;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementations;
import grafioschtrader.entities.AlgoStrategy;

public interface AlgoStrategyJpaRepositoryCustom extends BaseRepositoryCustom<AlgoStrategy> {
  Set<AlgoStrategyImplementations> getUnusedStrategiesForManualAdding(Integer idAlgoAssetclassSecurity);
}
