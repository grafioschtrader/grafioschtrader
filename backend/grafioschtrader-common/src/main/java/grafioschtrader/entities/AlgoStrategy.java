package grafioschtrader.entities;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A tenant's portfolio, asset class, or security may have none or more strategies. This entity represents a single
 * strategy within the algo hierarchy. The strategy type is identified by {@code algoStrategyImplementations} and its
 * type-specific parameters are stored as JSON in {@code strategyConfig}.
 */
@Schema(description = """
    Single algo strategy within the algo hierarchy. The strategy implementation type defines the evaluation logic,
    and strategyConfig holds the type-specific JSON parameters (e.g. alert thresholds, indicator settings).""")
@Entity
@Table(name = AlgoStrategy.TABNAME)
@DiscriminatorValue("S")
public class AlgoStrategy extends AlgoRuleStrategy {

  public static final String TABNAME = "algo_strategy";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @Column(name = "algo_strategy_impl")
  private byte algoStrategyImplementations;

  @Schema(description = "Whether this strategy is active and should be evaluated by the alarm service.")
  @Column(name = "activatable")
  private boolean activatable = true;

  @Schema(description = """
      JSON configuration specific to the strategy implementation type. Structure varies per type, e.g.
      {"lowerValue": 90.0, "upperValue": 110.0} for AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, or
      {"indicatorType": "SMA", "period": 50, "crossDirection": "ABOVE"} for AS_OBSERVED_SECURITY_MA_CROSSING.""")
  @Column(name = "strategy_config", columnDefinition = "JSON")
  private String strategyConfig;

  public boolean isActivatable() {
    return activatable;
  }

  public void setActivatable(boolean activatable) {
    this.activatable = activatable;
  }

  public AlgoStrategyImplementationType getAlgoStrategyImplementations() {
    return AlgoStrategyImplementationType.getAlgoStrategyImplentaionType(this.algoStrategyImplementations);
  }

  public void setAlgoStrategyImplementations(AlgoStrategyImplementationType algoStrategyImplementations) {
    this.algoStrategyImplementations = algoStrategyImplementations.getValue();
  }

  public String getStrategyConfig() {
    return strategyConfig;
  }

  public void setStrategyConfig(String strategyConfig) {
    this.strategyConfig = strategyConfig;
  }

}
