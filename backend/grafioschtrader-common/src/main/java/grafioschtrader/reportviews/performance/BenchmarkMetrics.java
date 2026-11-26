package grafioschtrader.reportviews.performance;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Figures of the comparison of the portfolio with its benchmark, both linked over exactly the same intervals: those of
    the comparison span in which the portfolio has a usable capital base. Percentages are in percent (12.34 means
    12.34 %), differences of percentages in percentage points. A figure that cannot be determined is null.""")
public record BenchmarkMetrics(
    @Schema(description = "Time-weighted return of the portfolio over the common intervals") Double portfolioTwrPercent,
    @Schema(description = "Return of the benchmark over the common intervals") Double benchmarkTwrPercent,
    @Schema(description = "Portfolio return minus benchmark return, in percentage points") Double excessReturnPercent,
    @Schema(description = "Portfolio return p.a.; null below 360 calendar days or with gaps in the exposure") Double portfolioTwrAnnualizedPercent,
    @Schema(description = "Benchmark return p.a.; null below 360 calendar days or with gaps in the exposure") Double benchmarkTwrAnnualizedPercent,
    @Schema(description = "Difference of the two annualized returns, in percentage points") Double excessReturnAnnualizedPercent,
    @Schema(description = "Number of common intervals both returns are linked over") int includedIntervals,
    @Schema(description = """
        Intervals of the risk sample: common, regular and with an observed price and exchange rate of the benchmark on
        both ends. A carried value is no observation.""") int riskObservations,
    @Schema(description = "Annualized volatility of the benchmark over the risk sample; null below 20 observations") Double benchmarkVolatilityAnnualizedPercent,
    @Schema(description = "Deepest decline of the benchmark over the common intervals, negative; 0 without a decline") Double benchmarkMaxDrawdownPercent,
    @Schema(description = "Annualized standard deviation of the active returns; null below 20 observations") Double trackingErrorPercent,
    @Schema(description = "Annualized mean active return per unit of tracking error; null below 20 observations or for a tracking error of 0") Double informationRatio,
    @Schema(description = "Sensitivity of the portfolio to the benchmark; null below 20 observations") Double beta,
    @Schema(description = "Correlation of the interval returns; null below 20 observations") Double correlation,
    @Schema(description = """
        Annualized return above the return the beta of the portfolio would have earned in the benchmark, both in excess
        of the risk-free rate of the main currency; null below 20 observations or without a known rate""") Double jensenAlphaAnnualizedPercent,
    @Schema(description = "Geometric mean return of the portfolio over the rising benchmark intervals relative to that of the benchmark; null below 10 such intervals") Double upCapturePercent,
    @Schema(description = "The same over the falling benchmark intervals; below 100 the portfolio fell less") Double downCapturePercent) {
}
