package grafioschtrader.algo.strategy.model.complex;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Risk management parameters of one position: exposure limit, drawdown limit and breach action. The position exposure
 * limit applies to every entry and every addition; no setting lifts it.
 */

public class RiskControlsConfig {

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  public Double max_position_exposure_pct;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  public Double max_position_drawdown_pct;

  public Boolean force_exit_on_risk_breach;
}
