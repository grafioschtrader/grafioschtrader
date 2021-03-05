package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

public class PerformanceChartDayDiff {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  public double externalCashTransferDiffMC;
  public double gainDiffMC;
  public double cashBalanceDiffMC;
  public double securitiesDiffMC;
  public double totalBalanceMC;

  public PerformanceChartDayDiff(LocalDate date) {
    this.date = date;
  }

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
