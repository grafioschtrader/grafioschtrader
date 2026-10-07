package grafioschtrader.reportviews.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wire contract of the chart card showing how the total value of a client or of a single portfolio developed, next to
 * the capital paid into it. The values are read from {@code hold_daily_total}, so the card runs no performance query of
 * its own and agrees with the period performance report (Securities + balance) on every day both show.
 */
public final class PerformanceTotalChartPayload {

  private PerformanceTotalChartPayload() {
  }

  /** How densely the points of a chart are spaced. */
  public enum Sampling {
    /** Every trading day. */
    DAY,
    /** The last trading day of each week, because the period has too many days for one point each. */
    WEEK,
    /** The last trading day of each month, because even one point per week would be too many. */
    MONTH
  }

  @Schema(description = """
      One chart card. The currency is that of the client, or that of the portfolio when a single portfolio is shown,
      and every amount is expressed in it.""")
  public record PerformanceTotalChart(String currency,
      @Schema(description = "Identifier of the portfolio shown, null when the whole client is shown") Integer idPortfolio,
      @Schema(description = "The period shown, one of the constants of DashboardChartRange") String range,
      @Schema(description = "Every period the card may ask for, in the order the select offers them") List<String> ranges,
      @Schema(description = "Spacing of the points; a long period is reduced to weekly or monthly values") Sampling sampling,
      @Schema(description = "The newest day with a value, null when there is none") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate newestDate,
      @Schema(description = """
          True when the background task has not yet computed every day up to the newest one shown, so the newest
          values may still change or be missing.""") boolean recalcPending,
      @Schema(description = """
          Translation key explaining why the chart has no points, null when it has some. A client whose daily values
          have not been computed yet, or which held nothing in the period, is an ordinary state and not a fault.""") String reasonKey,
      List<Point> points) {
  }

  @Schema(description = "One point of the chart, the close of a trading day")
  public record Point(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
      @Schema(description = "Total value: cash balance, securities and the result of closed margin positions") double totalBalanceMC,
      @Schema(description = "Invested capital: deposits less withdrawals accumulated up to this day") double investedCapitalMC) {
  }
}
