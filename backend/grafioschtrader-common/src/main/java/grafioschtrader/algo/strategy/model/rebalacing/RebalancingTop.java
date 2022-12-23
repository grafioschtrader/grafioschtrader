package grafioschtrader.algo.strategy.model.rebalacing;

import grafioschtrader.common.DynamicFormPropertySupport;
import grafioschtrader.dynamic.model.DynamicFormPropertyHelps;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RebalancingTop {

  /**
   * Interval of rebalancing
   */
  @NotNull()
  @Min(value = 1)
  @Max(value = 53)
  Integer timePeriodPerYear;

  /**
   * Maximum allowed deviation
   */
  @NotNull()
  @Min(value = 1)
  @Max(value = 49)
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.PERCENTAGE })
  Integer thresholdPercentage;

}
