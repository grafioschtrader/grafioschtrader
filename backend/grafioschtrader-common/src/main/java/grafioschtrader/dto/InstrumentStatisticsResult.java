package grafioschtrader.dto;

public class InstrumentStatisticsResult {
  public AnnualisedPerformance annualisedPerformance;
  public StatisticsSummary statisticsSummary;

  public InstrumentStatisticsResult(AnnualisedPerformance annualisedPerformance, StatisticsSummary statisticsSummary) {
    this.annualisedPerformance = annualisedPerformance;
    this.statisticsSummary = statisticsSummary;
  }

}
