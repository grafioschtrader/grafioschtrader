package grafioschtrader.algo.strategy.model.rebalacing;

import grafioschtrader.common.DynamicFormPropertySupport;
import grafioschtrader.dynamic.model.DynamicFormPropertyHelps;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class RebalancingAssetclassSecurity {

  @Min(value = 1)
  @Max(value = 100)
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.PERCENTAGE })
  private Integer weightingPercentage;

  public Integer getWeightingPercentage() {
    return weightingPercentage;
  }

  public void setWeightingPercentage(Integer weightingPercentage) {
    this.weightingPercentage = weightingPercentage;
  }

}
