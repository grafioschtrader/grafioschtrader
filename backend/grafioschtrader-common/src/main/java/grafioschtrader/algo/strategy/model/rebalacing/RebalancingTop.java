package grafioschtrader.algo.strategy.model.rebalacing;

import grafiosch.common.DynamicFormPropertySupport;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration of the strategic rebalancing strategy. It exists at the top level of the hierarchy only: the allocation
 * targets themselves are the parent-relative {@code percentage} of the {@code AlgoAssetclass} and {@code AlgoSecurity}
 * nodes, so a second weight on the strategy of a bucket or an instrument would be a competing target for the same
 * position.
 *
 * <p>
 * The accessors are required rather than decorative. The form writes the two values as flat parameters of the strategy,
 * and {@code AlertConfigAdapter} reads them back through {@code ValueFormatConverter}, which resolves the property by
 * JavaBean introspection; without a getter the field has no known data type and without a setter the parsed value is
 * never assigned, which would leave the configuration silently empty.
 * </p>
 */
public class RebalancingTop {

  /**
   * Interval of rebalancing
   */
  @NotNull()
  @Min(value = 1)
  @Max(value = 53)
  Integer timePeriodPerYear;

  /**
   * Class trigger tolerance in percentage points of the target investment budget.
   */
  @NotNull()
  @Min(value = 1)
  @Max(value = 49)
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.PERCENTAGE })
  Integer thresholdPercentage;

  /** Default security band in percentage points of the target class amount; each class may override it. */
  @NotNull
  @Min(0)
  @Max(100)
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.PERCENTAGE })
  Double securityDeviationPercentage = 5.0;

  /** Maximum distinct securities selected per class checkpoint; each class may override it. */
  @NotNull
  @Min(1)
  Integer maxTradedSecuritiesPerAssetclass = 3;

  public Double getSecurityDeviationPercentage() {
    return securityDeviationPercentage;
  }

  public void setSecurityDeviationPercentage(Double value) {
    securityDeviationPercentage = value;
  }

  public Integer getMaxTradedSecuritiesPerAssetclass() {
    return maxTradedSecuritiesPerAssetclass;
  }

  public void setMaxTradedSecuritiesPerAssetclass(Integer value) {
    maxTradedSecuritiesPerAssetclass = value;
  }

  public Integer getTimePeriodPerYear() {
    return timePeriodPerYear;
  }

  public void setTimePeriodPerYear(Integer timePeriodPerYear) {
    this.timePeriodPerYear = timePeriodPerYear;
  }

  public Integer getThresholdPercentage() {
    return thresholdPercentage;
  }

  public void setThresholdPercentage(Integer thresholdPercentage) {
    this.thresholdPercentage = thresholdPercentage;
  }

}
