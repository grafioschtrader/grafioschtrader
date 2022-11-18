package grafioschtrader.algo.strategy.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import grafioschtrader.algo.strategy.model.alerts.AbsoluteValuePriceAlert;
import grafioschtrader.algo.strategy.model.alerts.HoldingGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.PeriodPriceGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingAssetclassSecurity;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop;
import grafioschtrader.dynamic.model.DynamicModelHelper;

public abstract class StrategyHelper {

  private static Map<AlgoStrategyImplementations, StrategyClassBindingDefinition> strategyBindingMap;

  static {
    strategyBindingMap = new HashMap<>();
    strategyBindingMap.put(AlgoStrategyImplementations.AS_REBALANCING,
        new StrategyClassBindingDefinition(AlgoStrategyImplementations.AS_REBALANCING, RebalancingTop.class,
            RebalancingAssetclassSecurity.class, RebalancingAssetclassSecurity.class, null));
    strategyBindingMap.put(AlgoStrategyImplementations.AS_ABSOLUTE_PRICE_ALERT, new StrategyClassBindingDefinition(
        AlgoStrategyImplementations.AS_ABSOLUTE_PRICE_ALERT, null, null, AbsoluteValuePriceAlert.class, null));
    strategyBindingMap.put(AlgoStrategyImplementations.AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT,
        new StrategyClassBindingDefinition(AlgoStrategyImplementations.AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT,
            HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class,
            null));
    strategyBindingMap.put(AlgoStrategyImplementations.AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT,
        new StrategyClassBindingDefinition(AlgoStrategyImplementations.AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT,
            PeriodPriceGainLosePercentAlert.class, PeriodPriceGainLosePercentAlert.class,
            PeriodPriceGainLosePercentAlert.class, null));
  }

  public static Set<AlgoStrategyImplementations> getUnusedStrategiesForManualAdding(
      Set<AlgoStrategyImplementations> existingSet, String algoLevelType) {
    Predicate<StrategyClassBindingDefinition> levelImplementaionPredicate = null;

    switch (algoLevelType) {
    case "T":
      levelImplementaionPredicate = scbd -> scbd.algoTopModel != null;
      break;
    case "A":
      levelImplementaionPredicate = scbd -> scbd.algoAssetclassModel != null;
      break;
    case "S":
      levelImplementaionPredicate = scbd -> scbd.algoSecurityModel != null;
      break;
    }

    Set<AlgoStrategyImplementations> allASI = strategyBindingMap.values().stream().filter(levelImplementaionPredicate)
        .map(scbc -> scbc.algoStrategyImplementations).collect(Collectors.toSet());
    allASI.removeAll(existingSet);
    return allASI;
  }

  public static Map<AlgoStrategyImplementations, StrategyClassBindingDefinition> getStrategyBindingMap() {
    return strategyBindingMap;
  }

  public static InputAndShowDefinitionStrategy getFormDefinitionsByAlgoStrategyImpl(
      AlgoStrategyImplementations algoStrategyImplementations) {
    StrategyClassBindingDefinition scbd = strategyBindingMap.get(algoStrategyImplementations);

    return new InputAndShowDefinitionStrategy(DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoTopModel),
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoAssetclassModel),
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoSecurityModel));
  }

}
