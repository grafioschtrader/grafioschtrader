package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a trading period step with complete financial performance data.
 *
 * <p>
 * This class extends PeriodStepMissingHoliday to provide detailed financial metrics for a specific trading period,
 * typically representing a single day or month within a performance analysis window. Contains all monetary values in
 * the main currency (MC) for consistent reporting and analysis.
 * </p>
 */
@Schema(description = "Period step containing complete financial performance data for a trading period")
public class PeriodStep extends PeriodStepMissingHoliday {

  @Schema(description = "End date of this period step")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate lastDate;

  @Schema(description = "External cash transfers (deposits/withdrawals) in main currency")
  public double externalCashTransferMC;

  @Schema(description = "Investment gains/losses in main currency")
  public double gainMC;

  @Schema(description = "Realized gains from closing margin positions in main currency")
  public double marginCloseGainMC;

  @Schema(description = "Total cash balance in main currency")
  public double cashBalanceMC;

  @Schema(description = "Market value of all securities in main currency")
  public double securitiesMC;

  @Schema(description = "Total portfolio balance (cash + securities + margin gains) in main currency")
  public double totalBalanceMC;

  @Schema(description = "Number of days with missing data within this period step")
  public int missingDayCount;

  @Schema(description = """
      Date of the valuation this step is measured from, i.e. the previous step. In the yearly split it lies in an
      earlier month than lastDate unless the last valuation of the previous month is missing.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate baseDate;

  @Schema(description = """
      Time-weighted return from baseDate to lastDate in percent, chained from the daily returns in between. Null when
      no usable daily return lies in between.""")
  public Double twrPercent;

  @Schema(description = """
      Whether the step covers exactly one trading day (weekly split) or exactly one month (yearly split). Only complete
      steps are ranked as best or worst step.""")
  public boolean complete;

  /**
   * Constructs a new period step with complete financial performance data.
   *
   * @param lastDate               end date of this period step
   * @param externalCashTransferMC external cash transfers in main currency
   * @param gainMC                 investment gains in main currency
   * @param marginCloseGainMC      margin gains in main currency
   * @param cashBalanceMC          cash balance in main currency
   * @param securitiesMC           securities value in main currency
   * @param totalBalanceMC         total balance in main currency
   * @param missingDayCount        number of missing data days
   * @param baseDate               date of the valuation the step is measured from
   * @param twrPercent             time-weighted return from baseDate to lastDate in percent, may be null
   * @param complete               whether the step covers exactly one trading day or one month
   */
  public PeriodStep(LocalDate lastDate, double externalCashTransferMC, double gainMC, double marginCloseGainMC,
      double cashBalanceMC, double securitiesMC, double totalBalanceMC, int missingDayCount, LocalDate baseDate,
      Double twrPercent, boolean complete) {
    super(HolidayMissing.HM_TRADING_DAY);
    this.lastDate = lastDate;
    this.gainMC = gainMC;
    this.marginCloseGainMC = marginCloseGainMC;
    this.cashBalanceMC = cashBalanceMC;
    this.externalCashTransferMC = externalCashTransferMC;
    this.securitiesMC = securitiesMC;
    this.totalBalanceMC = totalBalanceMC;
    this.missingDayCount = missingDayCount;
    this.baseDate = baseDate;
    this.twrPercent = twrPercent;
    this.complete = complete;
  }

  @Schema(description = "Combined total of investment gains and margin gains in main currency")
  public double getTotalGainMC() {
    return gainMC + marginCloseGainMC;
  }

}
