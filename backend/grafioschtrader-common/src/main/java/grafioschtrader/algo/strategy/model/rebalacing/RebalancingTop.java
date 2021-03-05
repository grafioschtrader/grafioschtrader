package grafioschtrader.algo.strategy.model.rebalacing;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import grafioschtrader.common.DynamicFormPropertySupport;
import grafioschtrader.dynamic.model.DynamicFormPropertyHelps;

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
