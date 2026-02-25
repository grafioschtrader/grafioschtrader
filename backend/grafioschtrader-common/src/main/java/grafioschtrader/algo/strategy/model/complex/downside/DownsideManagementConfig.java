package grafioschtrader.algo.strategy.model.complex.downside;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.LossAction;
import jakarta.validation.Valid;

/**
 * Downside management configuration: defines the loss trigger, chosen action (sell or average down),
 * and the parameters for each variant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DownsideManagementConfig {

  @Valid
  public DownsideTriggerConfig trigger;

  public LossAction loss_action;

  @Valid
  public SellLossConfig variant_A_sell_loss;

  @Valid
  public AverageDownConfig variant_B_average_down;
}
