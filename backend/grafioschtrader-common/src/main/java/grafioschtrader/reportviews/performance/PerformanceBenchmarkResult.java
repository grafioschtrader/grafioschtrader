package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.BenchmarkReturnBasis;
import grafioschtrader.types.BenchmarkSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Comparison of the period performance with a benchmark instrument. Both sides are linked over exactly the same
    intervals. A resolved benchmark without usable data still carries its identity and notices, while metrics is null
    and chart is empty.""")
public record PerformanceBenchmarkResult(
    @Schema(description = "The benchmark instrument, a security or a currency pair") Integer idSecuritycurrency,
    @Schema(description = "Display name of the benchmark") String name,
    @Schema(description = "Currency of the benchmark quotes") String currency,
    @Schema(description = "Where the benchmark came from: this request, the portfolio or the tenant") BenchmarkSource source,
    @Schema(description = "Currency of the report, into which the benchmark is converted") String mainCurrency,
    @Schema(description = "Conversion pair as stored, for example USD/CHF; null without conversion") String fxPair,
    @Schema(description = "Whether the stored pair is the inverse of the needed direction") boolean fxInverse,
    @Schema(description = "What the benchmark return contains") BenchmarkReturnBasis returnBasis,
    @Schema(description = "Excluded base date of the report") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate requestedFrom,
    @Schema(description = "Last date of the report") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate requestedTo,
    @Schema(description = "First date of the comparison span; null without a span") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate effectiveFrom,
    @Schema(description = "Last date of the comparison span; null without a span") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate effectiveTo,
    @Schema(description = "Latest reliable quote of the instrument up to the end of the report") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastInstrumentDate,
    @Schema(description = "Latest reliable conversion rate up to the end of the report; null without conversion") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastRateDate,
    @Schema(description = "Conditions of the comparison the reader must know about") List<BenchmarkNotice> notices,
    @Schema(description = "Figures of the comparison; null without a comparison span") BenchmarkMetrics metrics,
    @Schema(description = "The shadow portfolio; null without a comparison span") ShadowPortfolioResult shadow,
    @Schema(description = "Cumulative returns of both sides per valuation date") List<BenchmarkChartPoint> chart) {
}
