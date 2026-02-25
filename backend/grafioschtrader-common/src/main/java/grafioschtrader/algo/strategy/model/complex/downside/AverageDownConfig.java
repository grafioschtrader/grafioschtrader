package grafioschtrader.algo.strategy.model.complex.downside;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.SizingConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

/**
 * Variant B of downside management: average down by adding to the position at lower prices.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AverageDownConfig {

  public Boolean enabled;

  @Valid
  public SizingConfig add_sizing;

  @Min(1)
  public Integer max_adds;

  @Valid
  public AddStepRuleConfig add_step_rule;

  public Boolean recalculate_avg_cost;
}
