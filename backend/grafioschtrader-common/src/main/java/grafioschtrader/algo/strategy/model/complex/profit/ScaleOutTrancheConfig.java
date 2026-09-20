package grafioschtrader.algo.strategy.model.complex.profit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * A single tranche in the scale-out plan: defines when to sell a fraction of the position.
 *
 * <p>
 * The identifier is what makes a tranche executable exactly once per position lifecycle, so it is carried into the
 * signal identity and into {@code algo_message_alert.tranche_key} and is bounded by that column's width.
 * </p>
 */

public class ScaleOutTrancheConfig {

  @Size(max = 32)
  public String id;

  @Valid
  public TriggerConfig trigger;

  public Double sell_fraction;

  public Boolean sell_remainder;
}
