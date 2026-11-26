package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.types.BenchmarkCoverage;

//@formatter:off
/**
 * Compares the time-weighted series of a report with an aligned benchmark over common intervals. A pure function of
 * its two inputs, fixed by hand-calculated tests.
 *
 * <p>
 * The common intervals {@code J} are the intervals {@code i} of the comparison span {@code (k, e]} in which the
 * portfolio has a usable capital base. For a base index {@code f >= k} and an end index {@code t <= e}:
 * </p>
 * <pre>
 * portfolioReturn(f, t) = product over i in J, f &lt; i &lt;= t of g_i - 1
 * benchmarkReturn(f, t) = product over i in J, f &lt; i &lt;= t of (b_i / b_(i-1)) - 1
 * </pre>
 * <p>
 * Both are null when no interval of {@code J} lies in {@code (f, t]}. Inside the span the portfolio side equals
 * {@link ReturnSeries#linked(int, int)}, which already skips unusable intervals. An interval without portfolio capital
 * is skipped on both sides, so neither earns anything in such a gap.
 * </p>
 */
//@formatter:on
public final class BenchmarkComparison {

  private final ReturnSeries portfolio;
  private final BenchmarkSeries.Result benchmark;
  private final int k;
  private final int e;
  /** Number of common intervals up to and including the index. */
  private final int[] commonCount;
  /** Product of the benchmark ratios over the common intervals up to and including the index. */
  private final double[] benchmarkProduct;

  /**
   * Prepares the comparison.
   *
   * @param portfolio the time-weighted series of the report
   * @param benchmark the benchmark aligned on the same valuation dates
   */
  public BenchmarkComparison(ReturnSeries portfolio, BenchmarkSeries.Result benchmark) {
    this.portfolio = portfolio;
    this.benchmark = benchmark;
    this.k = benchmark.k();
    this.e = benchmark.e();
    int size = portfolio.size();
    commonCount = new int[size];
    benchmarkProduct = new double[size];
    if (size > 0) {
      benchmarkProduct[0] = 1;
    }
    for (int i = 1; i < size; i++) {
      boolean common = isCommon(i);
      commonCount[i] = commonCount[i - 1] + (common ? 1 : 0);
      benchmarkProduct[i] = common ? benchmarkProduct[i - 1] * benchmark.levels()[i] / benchmark.levels()[i - 1]
          : benchmarkProduct[i - 1];
    }
  }

  /** Whether the interval ending at the index is a common interval of the span. */
  public boolean isCommon(int i) {
    return benchmark.hasSpan() && i > k && i <= e && portfolio.isUsable(i);
  }

  /**
   * Time-weighted return of the portfolio over the common intervals of {@code (f, t]}.
   *
   * @param f base index
   * @param t end index
   * @return the return as a ratio, null outside the span or without a common interval
   */
  public Double portfolioReturn(int f, int t) {
    return inSpanWithCommon(f, t) ? portfolio.linked(f, t) : null;
  }

  /**
   * Return of the benchmark over the common intervals of {@code (f, t]}.
   *
   * @param f base index
   * @param t end index
   * @return the return as a ratio, null outside the span or without a common interval
   */
  public Double benchmarkReturn(int f, int t) {
    return inSpanWithCommon(f, t) ? benchmarkProduct[t] / benchmarkProduct[f] - 1 : null;
  }

  /**
   * Coverage of a window or step measured from {@code f} to {@code t}.
   *
   * @param f base index
   * @param t end index
   * @return covered when it lies inside the span with a common interval, partial when it reaches over a span boundary
   */
  public BenchmarkCoverage coverage(int f, int t) {
    if (inSpanWithCommon(f, t)) {
      return BenchmarkCoverage.BENCHMARK_COVERED;
    }
    if (benchmark.hasSpan() && f < e && t > k && (f < k || t > e)) {
      return BenchmarkCoverage.BENCHMARK_PARTIAL;
    }
    return BenchmarkCoverage.BENCHMARK_NONE;
  }

  /** Whether the span holds an interval in which the portfolio had no usable capital. */
  public boolean partialExposure() {
    if (!benchmark.hasSpan()) {
      return false;
    }
    for (int i = k + 1; i <= e; i++) {
      if (!portfolio.isUsable(i)) {
        return true;
      }
    }
    return false;
  }

  /** Number of common intervals of the whole span. */
  public int includedIntervals() {
    return benchmark.hasSpan() ? commonCount[e] - commonCount[k] : 0;
  }

  /**
   * The figures of the whole span with the annualization rule of the report and without risk-free rate.
   *
   * @return the metrics, null without a span
   */
  public BenchmarkMetrics metrics() {
    return metrics(ReturnSeries.MIN_ANNUALIZATION_DAYS, null);
  }

  /**
   * The figures of the whole span, including the relative risk of {@link BenchmarkRisk}. Annualized returns need the
   * given number of calendar days and no gap in the exposure, otherwise a gap would be scaled as if it had been
   * invested.
   *
   * @param minAnnualizationDays fewest calendar days from which returns are annualized: 360 for the report, 0 for the
   *                             replay
   * @param annualRate           the annual risk-free rate of the main currency known on a date; null without rates
   * @return the metrics, null without a span
   */
  public BenchmarkMetrics metrics(long minAnnualizationDays, Function<LocalDate, Double> annualRate) {
    if (!benchmark.hasSpan()) {
      return null;
    }
    Double p = portfolioReturn(k, e);
    Double b = benchmarkReturn(k, e);
    Double pa = null;
    Double ba = null;
    if (!partialExposure()) {
      long days = ChronoUnit.DAYS.between(portfolio.date(k), portfolio.date(e));
      pa = ReturnSeries.annualize(p, days, minAnnualizationDays);
      ba = ReturnSeries.annualize(b, days, minAnnualizationDays);
    }
    BenchmarkRisk.Result risk = BenchmarkRisk.calculate(portfolio, benchmark, this::isCommon, annualRate);
    return new BenchmarkMetrics(PerformancePeriodMetrics.percent(p), PerformancePeriodMetrics.percent(b),
        PerformancePeriodMetrics.percent(difference(p, b)), PerformancePeriodMetrics.percent(pa),
        PerformancePeriodMetrics.percent(ba), PerformancePeriodMetrics.percent(difference(pa, ba)),
        includedIntervals(), risk.riskObservations(), risk.benchmarkVolatilityAnnualizedPercent(),
        risk.benchmarkMaxDrawdownPercent(), risk.trackingErrorPercent(), risk.informationRatio(), risk.beta(),
        risk.correlation(), risk.jensenAlphaAnnualizedPercent(), risk.upCapturePercent(), risk.downCapturePercent());
  }

  /**
   * One chart point per valuation date. At the first date of the span both returns are 0; later dates carry the
   * returns linked from it. Dates outside the span and intervals without portfolio capital give null returns. Inside
   * the span every point carries the value of the portfolio and that of the shadow portfolio.
   *
   * @param shadowValues values of the shadow portfolio per valuation date, null where it has none
   * @return the points in the order of the valuation dates
   */
  public List<BenchmarkChartPoint> chart(Double[] shadowValues) {
    List<BenchmarkChartPoint> points = new ArrayList<>(portfolio.size());
    for (int i = 0; i < portfolio.size(); i++) {
      Double p = null;
      Double b = null;
      if (benchmark.hasSpan() && i == k) {
        p = 0.0;
        b = 0.0;
      } else if (benchmark.hasSpan() && i > k && i <= e && portfolio.isUsable(i)) {
        p = zeroIfNull(portfolioReturn(k, i));
        b = zeroIfNull(benchmarkReturn(k, i));
      }
      boolean inSpan = benchmark.hasSpan() && i >= k && i <= e;
      points.add(new BenchmarkChartPoint(portfolio.date(i), PerformancePeriodMetrics.percent(p),
          PerformancePeriodMetrics.percent(b), inSpan ? money(portfolio.value(i)) : null,
          inSpan ? money(shadowValues[i]) : null));
    }
    return points;
  }

  private boolean inSpanWithCommon(int f, int t) {
    return benchmark.hasSpan() && f >= k && t <= e && f < t && commonCount[t] - commonCount[f] > 0;
  }

  private static Double difference(Double a, Double b) {
    return a == null || b == null ? null : a - b;
  }

  private static Double money(Double amount) {
    return amount == null ? null : DataBusinessHelper.roundStandard(amount);
  }

  private static Double zeroIfNull(Double value) {
    return value == null ? 0.0 : value;
  }
}
