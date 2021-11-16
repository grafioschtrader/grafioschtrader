package grafioschtrader.dto;

public class SecurityStatisticsReturnResult {
  public AnnualisedSecurityPerformance annualisedPerformance;
  public SecurityStatisticsSummaryResult summaryResult;

  public SecurityStatisticsReturnResult(AnnualisedSecurityPerformance annualisedPerformance,
      SecurityStatisticsSummaryResult summaryResult) {
    this.annualisedPerformance = annualisedPerformance;
    this.summaryResult = summaryResult;
  }
   
}
