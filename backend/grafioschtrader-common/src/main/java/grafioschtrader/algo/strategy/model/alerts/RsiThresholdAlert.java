package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for an RSI (Relative Strength Index) threshold alert (security level only). The alert fires when
 * the RSI value drops below the lower threshold (oversold) or rises above the upper threshold (overbought). At
 * least one of the two thresholds should be set; if both are set, either breach triggers the alert.
 *
 * <p>Evaluated by Tier 2 (scheduled indicator evaluation) because it requires historical price data for RSI
 * calculation.</p>
 */
public class RsiThresholdAlert {

  /** Number of trading days used to calculate the RSI (typically 14). */
  @NotNull
  @Min(value = 1)
  @Max(value = 999)
  Integer rsiPeriod;

  /** RSI value below which the asset is considered oversold and the alert fires (e.g. 30). Null to disable. */
  @Min(value = 0)
  @Max(value = 100)
  Integer lowerThreshold;

  /** RSI value above which the asset is considered overbought and the alert fires (e.g. 70). Null to disable. */
  @Min(value = 0)
  @Max(value = 100)
  Integer upperThreshold;

  public Integer getRsiPeriod() {
    return rsiPeriod;
  }

  public void setRsiPeriod(Integer rsiPeriod) {
    this.rsiPeriod = rsiPeriod;
  }

  public Integer getLowerThreshold() {
    return lowerThreshold;
  }

  public void setLowerThreshold(Integer lowerThreshold) {
    this.lowerThreshold = lowerThreshold;
  }

  public Integer getUpperThreshold() {
    return upperThreshold;
  }

  public void setUpperThreshold(Integer upperThreshold) {
    this.upperThreshold = upperThreshold;
  }
}
