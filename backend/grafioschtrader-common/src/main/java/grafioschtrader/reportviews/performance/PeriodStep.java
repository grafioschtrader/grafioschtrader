package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a trading period step with complete financial performance data.
 * 
 * <p>
 * This class extends PeriodStepMissingHoliday to provide detailed financial metrics
 * for a specific trading period, typically representing a single day or month within
 * a performance analysis window. Contains all monetary values in the main currency (MC)
 * for consistent reporting and analysis.
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

  /**
   * Constructs a new period step with complete financial performance data.
   * 
   * @param lastDate end date of this period step
   * @param externalCashTransferMC external cash transfers in main currency
   * @param gainMC investment gains in main currency
   * @param marginCloseGainMC margin gains in main currency
   * @param cashBalanceMC cash balance in main currency
   * @param securitiesMC securities value in main currency
   * @param totalBalanceMC total balance in main currency
   * @param missingDayCount number of missing data days
   */
  public PeriodStep(LocalDate lastDate, double externalCashTransferMC, double gainMC, double marginCloseGainMC,
      double cashBalanceMC, double securitiesMC, double totalBalanceMC, int missingDayCount) {
    super(HolidayMissing.HM_TRADING_DAY);
    this.lastDate = lastDate;
    this.gainMC = gainMC;
    this.marginCloseGainMC = marginCloseGainMC;
    this.cashBalanceMC = cashBalanceMC;
    this.externalCashTransferMC = externalCashTransferMC;
    this.securitiesMC = securitiesMC;
    this.totalBalanceMC = totalBalanceMC;
    this.missingDayCount = missingDayCount;
  }

  @Schema(description = "Combined total of investment gains and margin gains in main currency")
  public double getTotalGainMC() {
    return gainMC + marginCloseGainMC;
  }

}
