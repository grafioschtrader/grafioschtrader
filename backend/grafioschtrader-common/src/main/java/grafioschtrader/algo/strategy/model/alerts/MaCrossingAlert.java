package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration for a moving-average crossing alert (security level only). The alert fires when the security's last
 * price crosses above or below the specified moving average. The indicator is computed from the most recent
 * {@code period} daily closing prices loaded from the history-quote table.
 *
 * <p>
 * The indicator type and the cross direction are enums, so the generated edit form offers them as selection lists and a
 * value outside the enum is rejected when the stored parameters are read.
 * </p>
 *
 * <p>
 * Evaluated by Tier 2 (scheduled indicator evaluation) because it requires historical price data.
 * </p>
 */
public class MaCrossingAlert {

  /** Kind of moving average the last price is compared with. */
  @NotNull
  MovingAverageType indicatorType;

  /** Number of trading days used to calculate the moving average (1..999). */
  @NotNull
  @Min(value = 1)
  @Max(value = 999)
  Integer period;

  /** Direction of the crossing that triggers the alert. */
  @NotNull
  CrossDirection crossDirection;

  public MovingAverageType getIndicatorType() {
    return indicatorType;
  }

  public void setIndicatorType(MovingAverageType indicatorType) {
    this.indicatorType = indicatorType;
  }

  public Integer getPeriod() {
    return period;
  }

  public void setPeriod(Integer period) {
    this.period = period;
  }

  public CrossDirection getCrossDirection() {
    return crossDirection;
  }

  public void setCrossDirection(CrossDirection crossDirection) {
    this.crossDirection = crossDirection;
  }
}
