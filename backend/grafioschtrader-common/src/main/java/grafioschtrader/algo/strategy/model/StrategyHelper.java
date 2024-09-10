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

  public static final String TOP_LEVEL_LETTER = "T";
  public static final String ASSET_CLASS_LEVEL_LETTER = "A";
  public static final String SECURITY_LEVEL_LETTER = "S";
  
  private static Map<AlgoStrategyImplementationType, StrategyClassBindingDefinition> strategyBindingMap;

  static {
    strategyBindingMap = new HashMap<>();
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_REBALANCING,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_REBALANCING, RebalancingTop.class,
            RebalancingAssetclassSecurity.class, RebalancingAssetclassSecurity.class, null, false));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_ABSOLUTE_PRICE_ALERT, new StrategyClassBindingDefinition(
        AlgoStrategyImplementationType.AS_ABSOLUTE_PRICE_ALERT, null, null, AbsoluteValuePriceAlert.class, null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT,
            HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class,
            null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT,
            PeriodPriceGainLosePercentAlert.class, PeriodPriceGainLosePercentAlert.class,
            PeriodPriceGainLosePercentAlert.class, null, true));
  }

  public static Set<AlgoStrategyImplementationType> getUnusedStrategiesForManualAdding(
      Set<AlgoStrategyImplementationType> existingSet, AlgoLevelType algoLevelType) {
    Predicate<StrategyClassBindingDefinition> levelImplementaionPredicate = null;
    switch (algoLevelType) {
    case TOP_LEVEL:
      levelImplementaionPredicate = scbd -> scbd.algoTopModel != null;
      break;
    case ASSET_CLASS_LEVEL:
      levelImplementaionPredicate = scbd -> scbd.algoAssetclassModel != null;
      break;
    case SECURITY_LEVEL:
      levelImplementaionPredicate = scbd -> scbd.algoSecurityModel != null;
      break;
    }
    Set<AlgoStrategyImplementationType> allASI = strategyBindingMap.values().stream()
        .filter(levelImplementaionPredicate).map(scbc -> scbc.algoStrategyImplementations).collect(Collectors.toSet());
    allASI.removeAll(
        existingSet.stream().filter(es -> !strategyBindingMap.get(es).canRepeatSameLevel).collect(Collectors.toSet()));
    return allASI;
  }

  public static Map<AlgoStrategyImplementationType, StrategyClassBindingDefinition> getStrategyBindingMap() {
    return strategyBindingMap;
  }

  public static InputAndShowDefinitionStrategy getFormDefinitionsByAlgoStrategyImpl(
      AlgoStrategyImplementationType algoStrategyImplementations) {
    StrategyClassBindingDefinition scbd = strategyBindingMap.get(algoStrategyImplementations);

    return new InputAndShowDefinitionStrategy(
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoTopModel),
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoAssetclassModel),
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoSecurityModel));
  }

}
