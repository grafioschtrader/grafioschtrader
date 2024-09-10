package grafioschtrader.algo.strategy.model;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The class for a particular strategy can be different at different levels. The
 * user will make his settings via these classes. It may also make sense to have
 * a strategy only at the top level or, for example, on a security.
 */
public class StrategyClassBindingDefinition {

  public final AlgoStrategyImplementationType algoStrategyImplementations;
  @JsonIgnore
  public final Class<?> algoTopModel;
  @JsonIgnore
  public final Class<?> algoAssetclassModel;
  @JsonIgnore
  public final Class<?> algoSecurityModel;
  
  public final EnumSet<AlgoStrategyLevelRequirementType> algoStrategyLevelRequirementsSet;
  
  public boolean canRepeatSameLevel;

  public StrategyClassBindingDefinition(AlgoStrategyImplementationType algoStrategyImplementations, Class<?> algoTopModel,
      Class<?> algoAssetclassModel, Class<?> algoSecurityModel,
      EnumSet<AlgoStrategyLevelRequirementType> algoStrategyLevelRequirementsSet, boolean canRepeatSameLevel) {
    super();
    this.algoStrategyImplementations = algoStrategyImplementations;
    this.algoTopModel = algoTopModel;
    this.algoAssetclassModel = algoAssetclassModel;
    this.algoSecurityModel = algoSecurityModel;
    this.algoStrategyLevelRequirementsSet = algoStrategyLevelRequirementsSet;
    this.canRepeatSameLevel = canRepeatSameLevel;
  }

}
