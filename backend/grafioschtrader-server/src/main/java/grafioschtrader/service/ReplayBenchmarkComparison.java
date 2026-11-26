package grafioschtrader.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.reportviews.performance.PerformanceBenchmarkResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Comparison of the equity curve of a completed historical replay with a benchmark. It is calculated on request from
    the stored curve and the current price history, so a later request can differ after a price correction or a changed
    default benchmark; calculatedAt names the moment. Every return is in percent already and must not be scaled again,
    unlike the decimal ratios of the replay result.""")
public record ReplayBenchmarkComparison(
    @Schema(description = """
        The comparison in the currency of the simulation environment: identity, source and return basis of the
        benchmark, the effective dates, the linked returns with the relative risk, the shadow portfolio and the chart.
        Flows count at the start of a day, as in the metrics of the replay, and annualized figures are formed from the
        first day on.""") PerformanceBenchmarkResult comparison,
    @Schema(description = "Moment the comparison was calculated") @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime calculatedAt) {
}
