package grafioschtrader.dto;

import grafiosch.common.DynamicFormField;
import grafioschtrader.types.DashboardChartRange;
import jakarta.validation.constraints.NotNull;

/**
 * Saved settings of the chart card showing the development of the total value and of the invested capital, and the
 * source of its settings form.
 *
 * <p>
 * The portfolio is not here on purpose: the reader switches it on the card itself while comparing, so it reaches the
 * server as a setting of one read and is never written into the layout. The card offers the period there as well; the
 * value saved here is the period it opens with.
 * </p>
 */
public class PerformanceTotalChartConfig {

  /** The period the card opens with. The enum constants are the options of the select. */
  @DynamicFormField(uiOrder = "1.1", labelKey = "DASHBOARD_TOTAL_CHART_RANGE")
  @NotNull
  private DashboardChartRange range = DashboardChartRange.CHART_RANGE_1Y;

  public DashboardChartRange getRange() {
    return range;
  }

  public void setRange(DashboardChartRange range) {
    this.range = range;
  }
}
