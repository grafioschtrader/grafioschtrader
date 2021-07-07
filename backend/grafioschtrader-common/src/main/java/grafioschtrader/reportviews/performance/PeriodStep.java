package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

/**
 * Normally used for a single month or day
 *
 * @author Hugo Graf
 *
 */
public class PeriodStep extends PeriodStepMissingHoliday {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate lastDate;
  public double externalCashTransferMC;
  public double gainMC;
  public double marginCloseGainMC;
  public double cashBalanceMC;
  public double securitiesMC;
  public double totalBalanceMC;
  public int missingDayCount;

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

  public double getTotalGainMC() {
    return gainMC + marginCloseGainMC;
  }

}
