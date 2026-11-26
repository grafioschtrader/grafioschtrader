package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;

import grafiosch.common.DataHelper;
import grafioschtrader.common.ReturnSeries;

//@formatter:off
/**
 * Relative risk of a portfolio against its benchmark. A pure function of its inputs, fixed by hand-calculated tests.
 *
 * <p>
 * The <b>risk sample</b> {@code I} holds the common intervals of the comparison that are regular and in which both
 * legs of the benchmark, the price and the exchange rate, were observed on both ends of the interval; a carried value
 * is no observation. With {@code r_i} the interval return of the portfolio, {@code q_i = b_i / b_(i-1) - 1} that of
 * the benchmark and {@code a_i = r_i - q_i} the active return, all deviations are sample deviations:
 * </p>
 * <pre>
 * tracking error      = 100 * sqrt(252) * sd(a)
 * information ratio   = sqrt(252) * mean(a) / sd(a)
 * beta                = cov(r, q) / var(q)
 * correlation         = cov(r, q) / (sd(r) * sd(q))
 * Jensen's alpha      = 100 * 252 * alpha of the least-squares fit r_i - f_i = alpha + beta' * (q_i - f_i)
 * up / down capture   = 100 * G(r, S) / G(q, S), S the intervals with q_i &gt; 0 / q_i &lt; 0,
 *                       G(x, S) = (product over S of (1 + x_i))^(1/|S|) - 1
 * </pre>
 * <p>
 * {@code f_i = (1 + R)^(days/365) - 1} is the risk-free return of the interval, with {@code R} the annual rate known on
 * its first day. Tracking error and information ratio use the same sample, so a jump on an excluded interval changes
 * neither of them, while it still enters the linked returns of the comparison.
 * </p>
 */
//@formatter:on
public final class BenchmarkRisk {

  /** Fewest observations of the risk sample from which a deviation based figure is shown. */
  public static final int MIN_OBSERVATIONS = PerformancePeriodMetrics.MIN_VOLATILITY_OBSERVATIONS;
  /** Fewest up or down intervals from which a capture ratio is shown. */
  public static final int MIN_CAPTURE_INTERVALS = 10;
  private static final double SQRT_TRADING_DAYS = Math.sqrt(ReturnSeries.TRADING_DAYS_PER_YEAR);
  /** Fraction digits of the dimensionless figures. */
  private static final int RATIO_DIGITS = 4;
  /**
   * A deviation below this is no deviation. Portfolio and benchmark returns come from different value series, so two
   * identical return series still differ by floating point noise, and a ratio of that noise would be meaningless.
   */
  private static final double ZERO_DEVIATION = 1e-12;

  private BenchmarkRisk() {
  }

  /**
   * The risk rows of {@link BenchmarkMetrics}.
   *
   * @param riskObservations                     size of the risk sample
   * @param benchmarkVolatilityAnnualizedPercent annualized volatility of the benchmark over the risk sample
   * @param benchmarkMaxDrawdownPercent          deepest decline of the benchmark linked over the common intervals,
   *                                             negative, 0 without a decline
   * @param trackingErrorPercent                 annualized deviation of the active returns
   * @param informationRatio                     annualized mean active return per unit of tracking error
   * @param beta                                 sensitivity of the portfolio to the benchmark
   * @param correlation                          correlation of the two return series
   * @param jensenAlphaAnnualizedPercent         return above the risk-adjusted benchmark return, per year
   * @param upCapturePercent                     share of the rising benchmark intervals the portfolio captured
   * @param downCapturePercent                   share of the falling benchmark intervals the portfolio took part in
   */
  public record Result(int riskObservations, Double benchmarkVolatilityAnnualizedPercent,
      Double benchmarkMaxDrawdownPercent, Double trackingErrorPercent, Double informationRatio, Double beta,
      Double correlation, Double jensenAlphaAnnualizedPercent, Double upCapturePercent, Double downCapturePercent) {
  }

  /** One interval of the risk sample. */
  private record Observation(double r, double q, Double f) {
  }

  /**
   * Calculates the risk figures.
   *
   * @param portfolio  the time-weighted series of the portfolio
   * @param benchmark  the benchmark aligned on the same valuation dates, with a span
   * @param common     whether the interval ending at an index is a common interval of the comparison
   * @param annualRate the annual risk-free rate as a decimal known on a date, null when unknown; the function itself
   *                   may be null when no rate is available at all
   * @return the figures, a figure that cannot be determined is null
   */
  public static Result calculate(ReturnSeries portfolio, BenchmarkSeries.Result benchmark, IntPredicate common,
      Function<LocalDate, Double> annualRate) {
    List<Observation> sample = new ArrayList<>();
    boolean rateMissing = annualRate == null;
    for (int i = benchmark.k() + 1; i <= benchmark.e(); i++) {
      if (!common.test(i) || !portfolio.isRegular(i) || !observed(benchmark, i - 1) || !observed(benchmark, i)) {
        continue;
      }
      Double f = null;
      if (annualRate != null) {
        Double rate = annualRate.apply(portfolio.date(i - 1));
        if (rate == null) {
          rateMissing = true;
        } else {
          long days = ChronoUnit.DAYS.between(portfolio.date(i - 1), portfolio.date(i));
          f = Math.pow(1 + rate, days / 365.0) - 1;
        }
      }
      sample.add(new Observation(portfolio.intervalReturn(i),
          benchmark.levels()[i] / benchmark.levels()[i - 1] - 1, f));
    }
    int n = sample.size();
    Double drawdown = maxDrawdown(benchmark, common);
    if (n < MIN_OBSERVATIONS) {
      return new Result(n, null, drawdown, null, null, null, null, null, capture(sample, true),
          capture(sample, false));
    }
    double[] r = sample.stream().mapToDouble(Observation::r).toArray();
    double[] q = sample.stream().mapToDouble(Observation::q).toArray();
    double[] a = new double[n];
    for (int i = 0; i < n; i++) {
      a[i] = r[i] - q[i];
    }
    double sdA = sd(a);
    double varQ = covariance(q, q);
    double sdR = sd(r);
    double sdQ = Math.sqrt(varQ);
    double covRQ = covariance(r, q);
    return new Result(n, PerformancePeriodMetrics.percent(SQRT_TRADING_DAYS * sdQ), drawdown,
        PerformancePeriodMetrics.percent(SQRT_TRADING_DAYS * sdA),
        sdA < ZERO_DEVIATION ? null : ratio(SQRT_TRADING_DAYS * mean(a) / sdA),
        sdQ < ZERO_DEVIATION ? null : ratio(covRQ / varQ),
        sdR < ZERO_DEVIATION || sdQ < ZERO_DEVIATION ? null : ratio(covRQ / (sdR * sdQ)),
        rateMissing ? null : jensenAlpha(sample),
        capture(sample, true), capture(sample, false));
  }

  /** Both legs observed on the valuation date; an identity conversion counts as observed. */
  private static boolean observed(BenchmarkSeries.Result benchmark, int index) {
    return benchmark.instrumentObserved()[index] && benchmark.rateObserved()[index];
  }

  /** Deepest decline of the benchmark wealth linked over the common intervals, null without a common interval. */
  private static Double maxDrawdown(BenchmarkSeries.Result benchmark, IntPredicate common) {
    double wealth = 1;
    double peak = 1;
    double depth = 0;
    boolean any = false;
    for (int i = benchmark.k() + 1; i <= benchmark.e(); i++) {
      if (!common.test(i)) {
        continue;
      }
      any = true;
      wealth *= benchmark.levels()[i] / benchmark.levels()[i - 1];
      peak = Math.max(peak, wealth);
      depth = Math.min(depth, wealth / peak - 1);
    }
    return any ? PerformancePeriodMetrics.percent(depth) : null;
  }

  private static Double jensenAlpha(List<Observation> sample) {
    double[] x = sample.stream().mapToDouble(o -> o.q() - o.f()).toArray();
    double[] y = sample.stream().mapToDouble(o -> o.r() - o.f()).toArray();
    double varX = covariance(x, x);
    if (Math.sqrt(varX) < ZERO_DEVIATION) {
      return null;
    }
    double betaPrime = covariance(x, y) / varX;
    double alpha = mean(y) - betaPrime * mean(x);
    return PerformancePeriodMetrics.percent(ReturnSeries.TRADING_DAYS_PER_YEAR * alpha);
  }

  /** Up or down capture over the intervals in which the benchmark rose or fell. */
  private static Double capture(List<Observation> sample, boolean up) {
    List<Observation> subset = sample.stream().filter(o -> up ? o.q() > 0 : o.q() < 0).toList();
    if (subset.size() < MIN_CAPTURE_INTERVALS) {
      return null;
    }
    double gq = geometricMean(subset.stream().mapToDouble(Observation::q).toArray());
    return gq == 0 ? null
        : PerformancePeriodMetrics.percent(geometricMean(subset.stream().mapToDouble(Observation::r).toArray()) / gq);
  }

  private static double geometricMean(double[] values) {
    double logSum = 0;
    for (double v : values) {
      logSum += Math.log1p(v);
    }
    return Math.expm1(logSum / values.length);
  }

  private static double mean(double[] values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }

  /** Sample covariance. */
  private static double covariance(double[] x, double[] y) {
    double mx = mean(x);
    double my = mean(y);
    double sum = 0;
    for (int i = 0; i < x.length; i++) {
      sum += (x[i] - mx) * (y[i] - my);
    }
    return sum / (x.length - 1);
  }

  private static double sd(double[] values) {
    return Math.sqrt(covariance(values, values));
  }

  private static Double ratio(double value) {
    return Double.isFinite(value) ? DataHelper.round(value, RATIO_DIGITS) : null;
  }
}
