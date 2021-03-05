package grafioschtrader.algo.strategy.model;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class StrategyClassBindingDefinition {

  public final AlgoStrategyImplementations algoStrategyImplementations;
  @JsonIgnore
  public final Class<?> algoTopModel;
  @JsonIgnore
  public final Class<?> algoAssetclassModel;
  @JsonIgnore
  public final Class<?> algoSecurityModel;
  public final EnumSet<AlgoStrategyLevelRequirements> algoStrategyLevelRequirementsSet;

  public StrategyClassBindingDefinition(AlgoStrategyImplementations algoStrategyImplementations, Class<?> algoTopModel,
      Class<?> algoAssetclassModel, Class<?> algoSecurityModel,
      EnumSet<AlgoStrategyLevelRequirements> algoStrategyLevelRequirementsSet) {
    super();
    this.algoStrategyImplementations = algoStrategyImplementations;
    this.algoTopModel = algoTopModel;
    this.algoAssetclassModel = algoAssetclassModel;
    this.algoSecurityModel = algoSecurityModel;
    this.algoStrategyLevelRequirementsSet = algoStrategyLevelRequirementsSet;
  }

}
