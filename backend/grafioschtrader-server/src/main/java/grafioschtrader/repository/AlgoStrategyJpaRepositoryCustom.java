package grafioschtrader.repository;

import java.util.Set;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.AlgoStrategy;

public interface AlgoStrategyJpaRepositoryCustom extends BaseRepositoryCustom<AlgoStrategy> {
  Set<AlgoStrategyImplementationType> getStrategiesForLevel(AlgoLevelType algoLevelType);

  Set<AlgoStrategyImplementationType> getUnusedStrategiesForManualAdding(Integer idAlgoAssetclassSecurity);
}
