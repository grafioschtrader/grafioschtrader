package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains the results of statistical calculations for an instrument, including annualised performance and a summary of various statistical measures.")
public class InstrumentStatisticsResult {
  @Schema(description = "Annualised performance data for the instrument.")
  public AnnualisedPerformance annualisedPerformance;
  @Schema(description = "Summary of various statistical measures for the instrument (e.g., standard deviation, min, max) grouped by time periods.")
  public StatisticsSummary statisticsSummary;

  public InstrumentStatisticsResult(AnnualisedPerformance annualisedPerformance, StatisticsSummary statisticsSummary) {
    this.annualisedPerformance = annualisedPerformance;
    this.statisticsSummary = statisticsSummary;
  }

}
