package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

public interface IPeriodHolding {
  public LocalDate getDate();

  public double getDividendRealMC();

  public double getFeeRealMC();

  public double getInterestCashaccountRealMC();

  public double getAccumulateReduceMC();

  public double getCashBalanceMC();

  public double getExternalCashTransferMC();

  public double getSecuritiesMC();

  public double getMarginCloseGainMC();

  public double getSecurityRiskMC();

  public double getGainMC();
}