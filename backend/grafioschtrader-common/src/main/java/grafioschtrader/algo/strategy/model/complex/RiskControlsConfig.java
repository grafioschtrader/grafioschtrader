package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Risk management parameters: position exposure limits, drawdown limits, and breach actions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskControlsConfig {

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  public Double max_position_exposure_pct;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  public Double max_position_drawdown_pct;

  public Boolean force_exit_on_risk_breach;

  public Boolean block_entry_if_exposure_exceeded;

  public Boolean block_add_if_exposure_exceeded;
}
