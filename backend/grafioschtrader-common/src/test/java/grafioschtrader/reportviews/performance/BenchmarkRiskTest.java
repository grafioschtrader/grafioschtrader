package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;

/**
 * Fixes the relative risk figures against values computed independently in this test from the interval returns it
 * defines, with plain loops instead of the production code. Valuation dates are consecutive calendar days.
 */
class BenchmarkRiskTest {

  private static final LocalDate D0 = LocalDate.of(2025, 1, 6);
  private static final double PCT = 0.0051;
  private static final double RATIO = 0.00006;

  /** Interval returns of portfolio and benchmark, with the flags that decide the risk sample. */
  private static final class Case {
    final List<Double> r = new ArrayList<>();
    final List<Double> q = new ArrayList<>();
    final List<Boolean> regular = new ArrayList<>();
    final List<Boolean> observed = new ArrayList<>();

    Case add(double rr, double qq) {
      return add(rr, qq, true);
    }

    Case add(double rr, double qq, boolean isRegular) {
      r.add(rr);
      q.add(qq);
      regular.add(isRegular);
      observed.add(true);
      return this;
    }

    /** A varying, non-constant pair of returns; the active return varies as well. */
    static Case varying(int n) {
      Case c = new Case();
      for (int i = 1; i <= n; i++) {
        double qq = 0.01 * Math.sin(i * 1.3) + 0.002;
        c.add(1.2 * qq + 0.003 * Math.cos(i * 0.7) + 0.0005, qq);
      }
      return c;
    }

    BenchmarkMetrics metrics(Function<LocalDate, Double> rate) {
      int n = r.size();
      List<ValuationPoint> points = new ArrayList<>();
      Double[] levels = new Double[n + 1];
      boolean[] instrumentObserved = new boolean[n + 1];
      boolean[] rateObserved = new boolean[n + 1];
      double value = 100;
      levels[0] = 1000.0;
      points.add(new ValuationPoint(D0, value, 0, 0, true));
      instrumentObserved[0] = true;
      rateObserved[0] = true;
      for (int i = 1; i <= n; i++) {
        value *= 1 + r.get(i - 1);
        levels[i] = levels[i - 1] * (1 + q.get(i - 1));
        points.add(new ValuationPoint(D0.plusDays(i), value, 0, 0, regular.get(i - 1)));
        instrumentObserved[i] = observed.get(i - 1);
        rateObserved[i] = true;
      }
      BenchmarkSeries.Result aligned = new BenchmarkSeries.Result(levels, instrumentObserved, rateObserved, 0, n,
          D0.plusDays(n), null, List.of());
      return new BenchmarkComparison(ReturnSeries.of(points, Convention.REPORT), aligned).metrics(360, rate);
    }
  }

  private static double mean(double[] x) {
    return Arrays.stream(x).sum() / x.length;
  }

  private static double cov(double[] x, double[] y) {
    double mx = mean(x);
    double my = mean(y);
    double s = 0;
    for (int i = 0; i < x.length; i++) {
      s += (x[i] - mx) * (y[i] - my);
    }
    return s / (x.length - 1);
  }

  private static double[] arr(List<Double> values) {
    return values.stream().mapToDouble(Double::doubleValue).toArray();
  }

  @Test
  @DisplayName("30 varying intervals: tracking error, information ratio, beta, correlation and volatility")
  void figuresOfAVaryingSample() {
    Case c = Case.varying(30);
    BenchmarkMetrics m = c.metrics(null);
    double[] r = arr(c.r);
    double[] q = arr(c.q);
    double[] a = new double[r.length];
    for (int i = 0; i < r.length; i++) {
      a[i] = r[i] - q[i];
    }
    double sdA = Math.sqrt(cov(a, a));
    assertEquals(30, m.riskObservations());
    assertEquals(100 * Math.sqrt(252) * sdA, m.trackingErrorPercent(), PCT);
    assertEquals(Math.sqrt(252) * mean(a) / sdA, m.informationRatio(), RATIO);
    assertEquals(cov(r, q) / cov(q, q), m.beta(), RATIO);
    assertEquals(cov(r, q) / Math.sqrt(cov(r, r) * cov(q, q)), m.correlation(), RATIO);
    assertEquals(100 * Math.sqrt(252) * Math.sqrt(cov(q, q)), m.benchmarkVolatilityAnnualizedPercent(), PCT);
    assertNull(m.jensenAlphaAnnualizedPercent(), "no risk-free rate, no alpha");
  }

  @Test
  @DisplayName("A jump of +50 % on an irregular interval changes the linked return but not TE and information ratio")
  void jumpOnAnExcludedIntervalLeavesTheSampleUnchanged() {
    BenchmarkMetrics without = Case.varying(30).metrics(null);
    Case jump = Case.varying(30);
    jump.add(0.5, 0.0, false);
    BenchmarkMetrics with = jump.metrics(null);
    assertEquals(without.riskObservations(), with.riskObservations());
    assertEquals(without.trackingErrorPercent(), with.trackingErrorPercent());
    assertEquals(without.informationRatio(), with.informationRatio());
    assertEquals((1 + without.portfolioTwrPercent() / 100) * 1.5, 1 + with.portfolioTwrPercent() / 100, 1e-4);
  }

  @Test
  @DisplayName("Identical varying series: tracking error 0, beta 1, correlation 1, no information ratio")
  void identicalSeries() {
    Case c = new Case();
    for (int i = 1; i <= 25; i++) {
      double v = 0.01 * Math.sin(i);
      c.add(v, v);
    }
    BenchmarkMetrics m = c.metrics(null);
    assertEquals(0.0, m.trackingErrorPercent(), 1e-9);
    assertNull(m.informationRatio());
    assertEquals(1.0, m.beta(), 1e-9);
    assertEquals(1.0, m.correlation(), 1e-9);
  }

  @Test
  @DisplayName("19 observations show no deviation based figure, 20 do")
  void minimumObservations() {
    BenchmarkMetrics nineteen = Case.varying(19).metrics(null);
    assertEquals(19, nineteen.riskObservations());
    assertNull(nineteen.trackingErrorPercent());
    assertNull(nineteen.beta());
    assertNull(nineteen.benchmarkVolatilityAnnualizedPercent());
    BenchmarkMetrics twenty = Case.varying(20).metrics(null);
    assertNotNull(twenty.trackingErrorPercent());
    assertNotNull(twenty.beta());
  }

  @Test
  @DisplayName("Capture ratios need 10 up or down intervals: 9 rising intervals give none, 10 give 100 * G(r) / G(q)")
  void captureRatios() {
    Case nine = new Case();
    Case ten = new Case();
    for (int i = 1; i <= 25; i++) {
      // 9 or 10 rising benchmark intervals, the others falling
      double qq = i <= 9 ? 0.01 + 0.001 * i : -0.005 - 0.0002 * i;
      nine.add(0.8 * qq, qq);
      double q10 = i <= 10 ? 0.01 + 0.001 * i : -0.005 - 0.0002 * i;
      ten.add(0.8 * q10 + (i <= 10 ? 0.001 : 0), q10);
    }
    assertNull(nine.metrics(null).upCapturePercent());
    double gr = 1;
    double gq = 1;
    for (int i = 0; i < 10; i++) {
      gr *= 1 + ten.r.get(i);
      gq *= 1 + ten.q.get(i);
    }
    double expected = 100 * (Math.pow(gr, 0.1) - 1) / (Math.pow(gq, 0.1) - 1);
    assertEquals(expected, ten.metrics(null).upCapturePercent(), PCT);
    assertNotNull(ten.metrics(null).downCapturePercent(), "15 falling intervals");
  }

  @Test
  @DisplayName("Jensen's alpha from a regression of excess returns; a missing opening rate leaves it empty")
  void jensenAlpha() {
    Case c = Case.varying(30);
    double rate = 0.02;
    double f = Math.pow(1 + rate, 1 / 365.0) - 1;
    double[] x = c.q.stream().mapToDouble(v -> v - f).toArray();
    double[] y = c.r.stream().mapToDouble(v -> v - f).toArray();
    double betaPrime = cov(x, y) / cov(x, x);
    double alpha = mean(y) - betaPrime * mean(x);
    assertEquals(100 * 252 * alpha, c.metrics(_ -> rate).jensenAlphaAnnualizedPercent(), PCT);
    assertNull(c.metrics(day -> day.equals(D0) ? null : rate).jensenAlphaAnnualizedPercent());
  }

  @Test
  @DisplayName("A carried benchmark close takes both intervals touching it out of the risk sample")
  void carriedCloseIsNoObservation() {
    Case c = Case.varying(30);
    c.observed.set(9, false);
    assertEquals(28, c.metrics(null).riskObservations());
  }

  @Test
  @DisplayName("Benchmark 1000 -> 1100 -> 990 -> 1039.5: the deepest decline is -10 %")
  void benchmarkMaxDrawdown() {
    Case c = new Case().add(0.01, 0.10).add(0.01, -0.10).add(0.01, 0.05);
    assertEquals(-10.0, c.metrics(null).benchmarkMaxDrawdownPercent(), 1e-9);
    assertEquals(0.0, new Case().add(0.01, 0.01).add(0.01, 0.02).metrics(null).benchmarkMaxDrawdownPercent(), 1e-9);
  }
}
