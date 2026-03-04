package grafioschtrader.algo.strategy.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import grafiosch.dynamic.model.DynamicModelHelper;
import grafioschtrader.algo.strategy.model.alerts.AbsoluteValuePriceAlert;
import grafioschtrader.algo.strategy.model.alerts.ExpressionAlert;
import grafioschtrader.algo.strategy.model.alerts.HoldingGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.MaCrossingAlert;
import grafioschtrader.algo.strategy.model.alerts.PeriodPriceGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.RsiThresholdAlert;
import grafioschtrader.algo.strategy.model.complex.StrategyConfig;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingAssetclassSecurity;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop;

public abstract class StrategyHelper {

  public static final String TOP_LEVEL_LETTER = "T";
  public static final String ASSET_CLASS_LEVEL_LETTER = "A";
  public static final String SECURITY_LEVEL_LETTER = "S";

  private static Map<AlgoStrategyImplementationType, StrategyClassBindingDefinition> strategyBindingMap;

  static {
    strategyBindingMap = new HashMap<>();
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING,
            RebalancingTop.class, RebalancingAssetclassSecurity.class, RebalancingAssetclassSecurity.class, null,
            false));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null,
            null, AbsoluteValuePriceAlert.class, null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE,
            HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class, HoldingGainLosePercentAlert.class,
            null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT,
        new StrategyClassBindingDefinition(
            AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT, null, null,
            PeriodPriceGainLosePercentAlert.class, null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP,
            null, null, null, StrategyConfig.class, null, false));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING, null,
            null, MaCrossingAlert.class, null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD, null,
            null, RsiThresholdAlert.class, null, true));
    strategyBindingMap.put(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_EXPRESSION,
        new StrategyClassBindingDefinition(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_EXPRESSION, null,
            null, ExpressionAlert.class, null, true));
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
      levelImplementaionPredicate = scbd -> scbd.algoSecurityModel != null || scbd.complexConfigClass != null;
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
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(scbd.algoSecurityModel),
        scbd.complexConfigClass != null);
  }

}
