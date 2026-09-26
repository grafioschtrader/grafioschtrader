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

  /**
   * Originally intended to support verification that this strategy can be executed in a simulation. No complete
   * verification workflow currently certifies this flag; {@code true} does not establish simulation readiness.
   * Currently gates this strategy's live alert scopes, mean-reversion replay and scheduled rebalancing, but manual
   * rebalancing and rebalancing replay ignore it. Complex configurations require executable validation on save when
   * true; false permits absent configuration or a draft passing structural validation only. That save-time check is not
   * verification of the complete hierarchy or simulation inputs.
   */
  @Schema(description = """
      Activation flag originally intended to support simulation verification; no complete verification workflow
      certifies it, and true does not establish simulation readiness. Currently gates this strategy's live alert
      scopes, mean-reversion replay and scheduled rebalancing; manual rebalancing and rebalancing replay ignore it.
      Complex configurations require executable validation on save when true; false permits absent configuration
      or a structurally validated draft. This save-time check does not verify the complete hierarchy or simulation
      inputs. Defaults to true.""")
  @Column(name = "activatable")
  private boolean activatable = true;

  /** Live notifications only; historical replay and strategy validation never use this preference. */
  @Schema(description = """
      Enables live notifications of this strategy, either as a standalone alert or as part of the main tenant's
      assigned monitoring hierarchy. Defaults to true. Editable in the standalone alert overview and in the view of
      the assigned hierarchy; ignored for historical replay. Existing activation and evaluation conditions still
      apply.""")
  @Column(name = "alert_enabled", nullable = false)
  private boolean alertEnabled = true;

  public boolean isAlertEnabled() {
    return alertEnabled;
  }

  public void setAlertEnabled(boolean alertEnabled) {
    this.alertEnabled = alertEnabled;
  }

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
