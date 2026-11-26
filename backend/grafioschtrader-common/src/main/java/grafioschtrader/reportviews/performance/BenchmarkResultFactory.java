package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.types.BenchmarkNoticeCode;

/**
 * Builds the comparison of a valuation series with its benchmark: the time-weighted comparison with its relative risk,
 * the shadow portfolio, the chart and the notices. The period performance report and the historical replay both build
 * their result here, so they agree on every figure for the same input and differ only in the settings their own series
 * call for.
 */
public final class BenchmarkResultFactory {

  private BenchmarkResultFactory() {
  }

  /**
   * What distinguishes the callers.
   *
   * @param convention           flow timing of the series, which the shadow portfolio follows
   * @param minAnnualizationDays fewest calendar days from which returns are annualized
   * @param annualRate           annual risk-free rate of the main currency known on a date, null without rates
   */
  public record Settings(Convention convention, long minAnnualizationDays, Function<LocalDate, Double> annualRate) {
  }

  /**
   * The result and the comparison it was built from.
   *
   * @param result     the comparison for the client
   * @param comparison the comparison, null without a span, for callers that fill further figures from it
   */
  public record Built(PerformanceBenchmarkResult result, BenchmarkComparison comparison) {
  }

  /**
   * Builds the comparison.
   *
   * @param descriptor    identity, conversion and return basis of the benchmark
   * @param aligned       the benchmark aligned on the valuation dates, null when it could not be loaded
   * @param series        the time-weighted series of the valuation points, null without valuations
   * @param points        the valuation points with their flows
   * @param flows         the dated external flows for the money-weighted return of the shadow portfolio
   * @param requestedFrom first date the caller asked for
   * @param requestedTo   last date the caller asked for
   * @param settings      convention, annualization and risk-free rate of the caller
   * @return the result, with the identity and the notices even when no comparison could be formed
   */
  public static Built build(BenchmarkDescriptor descriptor, BenchmarkSeries.Result aligned, ReturnSeries series,
      List<ValuationPoint> points, List<DatedFlow> flows, LocalDate requestedFrom, LocalDate requestedTo,
      Settings settings) {
    List<BenchmarkNotice> notices = new ArrayList<>(descriptor.notices());
    BenchmarkMetrics metrics = null;
    ShadowPortfolioResult shadow = null;
    List<BenchmarkChartPoint> chart = List.of();
    LocalDate effectiveFrom = null;
    LocalDate effectiveTo = null;
    LocalDate lastInstrumentDate = null;
    LocalDate lastRateDate = null;
    BenchmarkComparison comparison = null;
    if (aligned != null && series != null) {
      notices.addAll(aligned.notices());
      lastInstrumentDate = aligned.lastInstrumentDate();
      lastRateDate = aligned.lastRateDate();
      if (aligned.hasSpan()) {
        comparison = new BenchmarkComparison(series, aligned);
        if (comparison.partialExposure()) {
          notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_PARTIAL_EXPOSURE));
        }
        effectiveFrom = series.date(aligned.k());
        effectiveTo = series.date(aligned.e());
        metrics = comparison.metrics(settings.minAnnualizationDays(), settings.annualRate());
        ShadowPortfolio.Result shadowPortfolio = ShadowPortfolio.calculate(points, aligned.levels(), aligned.k(),
            aligned.e(), settings.convention(), flows);
        shadow = shadowPortfolio.summary();
        if (shadow != null && shadow.shortfallDate() != null) {
          notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_SHADOW_SHORTFALL, "date",
              shadow.shortfallDate()));
        }
        chart = comparison.chart(shadowPortfolio.values());
      }
    }
    return new Built(new PerformanceBenchmarkResult(descriptor.idSecuritycurrency(), descriptor.name(),
        descriptor.currency(), descriptor.source(), descriptor.mainCurrency(), descriptor.fxPair(),
        descriptor.fxInverse(), descriptor.returnBasis(), requestedFrom, requestedTo, effectiveFrom, effectiveTo,
        lastInstrumentDate, lastRateDate, notices, metrics, shadow, chart), comparison);
  }
}
