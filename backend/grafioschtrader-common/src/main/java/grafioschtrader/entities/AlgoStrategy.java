package grafioschtrader.entities;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = """
    A tenant's portfolio, asset class or security may have one or more strategies. The implementation type defines the
    evaluation logic, and strategyConfig holds its type-specific JSON parameters, such as alert thresholds or indicator
    settings.""")
@Entity
@Table(name = AlgoStrategy.TABNAME)
@DiscriminatorValue("S")
public class AlgoStrategy extends AlgoRuleStrategy {

  public static final String TABNAME = "algo_strategy";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Strategy implementation that defines the evaluation logic")
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
