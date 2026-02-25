package grafioschtrader.algo.strategy.model.complex.profit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;

/**
 * A single tranche in the scale-out plan: defines when to sell a fraction of the position.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleOutTrancheConfig {

  public String id;

  @Valid
  public TriggerConfig trigger;

  public Double sell_fraction;

  public Boolean sell_remainder;
}
