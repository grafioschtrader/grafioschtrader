package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data point for charting daily performance differences in period performance analysis.
 * 
 * <p>
 * This class represents a single day's performance metrics as differences from a baseline (typically the first day of
 * the analysis period). It is specifically designed for time-series charting and visualization of portfolio
 * performance.
 * </p>
 * 
 * <p>
 * <strong>Difference Calculation:</strong>
 * </p>
 * <p>
 * All "Diff" fields represent the change from the baseline period, calculated as (current day value - baseline value).
 * This allows charts to show cumulative changes over time starting from zero.
 * </p>
 * 
 * <p>
 * <strong>Currency Context:</strong>
 * </p>
 * <p>
 * All monetary values are in "MC" (Main Currency), which is either the tenant's base currency or portfolio's base
 * currency depending on analysis scope.
 * </p>
 */
@Schema(description = "Daily performance differences for chart visualization")
public class PerformanceChartDayDiff {

  @Schema(description = "Date of the performance measurement")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;

  @Schema(description = "Cumulative change in external cash transfers from baseline in main currency")
  public double externalCashTransferDiffMC;
  @Schema(description = "Cumulative change in investment gains from baseline in main currency")
  public double gainDiffMC;

  @Schema(description = "Cumulative change in cash balance from baseline in main currency")
  public double cashBalanceDiffMC;

  @Schema(description = "Cumulative change in securities market value from baseline in main currency")
  public double securitiesDiffMC;

  @Schema(description = "Total portfolio balance (cash + securities + margin gains) in main currency")
  public double totalBalanceMC;

  public PerformanceChartDayDiff(LocalDate date) {
    this.date = date;
  }

  /**
   * Creates a complete data point with all performance differences.
   * 
   * <p>
   * This constructor is used for all days after the baseline, where differences from the first day have been
   * calculated.
   * </p>
   * 
   * @param date                       the date for this data point
   * @param externalCashTransferDiffMC change in external transfers from baseline
   * @param gainDiffMC                 change in investment gains from baseline
   * @param cashBalanceDiffMC          change in cash balance from baseline
   * @param securitiesDiffMC           change in securities value from baseline
   * @param totalBalanceMC             total portfolio balance on this date
   */
  public PerformanceChartDayDiff(LocalDate date, double externalCashTransferDiffMC, double gainDiffMC,
      double cashBalanceDiffMC, double securitiesDiffMC, double totalBalanceMC) {
    super();
    this.date = date;
    this.externalCashTransferDiffMC = externalCashTransferDiffMC;
    this.gainDiffMC = gainDiffMC;
    this.cashBalanceDiffMC = cashBalanceDiffMC;
    this.securitiesDiffMC = securitiesDiffMC;
    this.totalBalanceMC = totalBalanceMC;
  }

}
