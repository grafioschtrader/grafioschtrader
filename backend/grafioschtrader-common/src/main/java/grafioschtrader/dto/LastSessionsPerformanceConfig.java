package grafioschtrader.dto;

import grafiosch.common.DynamicFormField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Saved settings of the card reporting the last completed sessions, and the source of its settings form.
 *
 * <p>
 * The portfolio is not here on purpose: the reader switches it on the card itself while comparing, so it reaches the
 * server as a setting of one read and is never written into the layout.
 * </p>
 */
public class LastSessionsPerformanceConfig {

  /**
   * How many sessions the card reports. Two is the smallest span in which anything can be compared at all, and beyond
   * ten the card stops being something a reader takes in at a glance.
   */
  @DynamicFormField(uiOrder = "1.1", labelKey = "DASHBOARD_LAST_SESSIONS_DAYS")
  @NotNull
  @Min(2)
  @Max(10)
  private Integer days = 5;

  public Integer getDays() {
    return days;
  }

  public void setDays(Integer days) {
    this.days = days;
  }
}
