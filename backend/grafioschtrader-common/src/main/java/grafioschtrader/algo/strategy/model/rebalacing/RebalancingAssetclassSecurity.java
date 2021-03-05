package grafioschtrader.algo.strategy.model.rebalacing;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import grafioschtrader.common.DynamicFormPropertySupport;
import grafioschtrader.dynamic.model.DynamicFormPropertyHelps;

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
