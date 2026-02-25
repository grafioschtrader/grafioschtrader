package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration for a moving-average crossing alert (security level only). The alert fires when the security's last
 * price crosses above or below the specified moving average. The indicator is computed from the most recent
 * {@code period} daily closing prices loaded from the history-quote table.
 *
 * <p>Evaluated by Tier 2 (scheduled indicator evaluation) because it requires historical price data.</p>
 */
public class MaCrossingAlert {

  /** Moving-average type: "SMA" (Simple Moving Average) or "EMA" (Exponential Moving Average). */
  @NotNull
  @Pattern(regexp = "SMA|EMA")
  String indicatorType;

  /** Number of trading days used to calculate the moving average (1..999). */
  @NotNull
  @Min(value = 1)
  @Max(value = 999)
  Integer period;

  /**
   * Direction of the crossing that triggers the alert: "ABOVE" fires when the last price crosses above the MA,
   * "BELOW" fires when the last price crosses below the MA.
   */
  @NotNull
  @Pattern(regexp = "BELOW|ABOVE")
  String crossDirection;

  public String getIndicatorType() {
    return indicatorType;
  }

  public void setIndicatorType(String indicatorType) {
    this.indicatorType = indicatorType;
  }

  public Integer getPeriod() {
    return period;
  }

  public void setPeriod(Integer period) {
    this.period = period;
  }

  public String getCrossDirection() {
    return crossDirection;
  }

  public void setCrossDirection(String crossDirection) {
    this.crossDirection = crossDirection;
  }
}
