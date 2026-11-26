package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One valuation date of the cumulative comparison chart. Both returns start at 0 on the first date of the comparison
    span and are linked over the common intervals only. Both are null outside the span and on an interval without
    portfolio capital, which the chart shows as a gap. Both values are equal on the first date of the span and null
    outside it.""")
public record BenchmarkChartPoint(
    @Schema(description = "Valuation date") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
    @Schema(description = "Cumulative time-weighted return of the portfolio in percent") Double portfolioTwrPercent,
    @Schema(description = "Cumulative return of the benchmark in percent") Double benchmarkTwrPercent,
    @Schema(description = "Value of the portfolio in main currency: cash, securities and the open margin result") Double portfolioValueMC,
    @Schema(description = "Value of the shadow portfolio in main currency; null after a shortfall") Double shadowValueMC) {
}
