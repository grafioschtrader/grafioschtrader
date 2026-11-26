package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.reportviews.performance.BenchmarkSeries.Quote;
import grafioschtrader.types.BenchmarkCoverage;
import grafioschtrader.types.BenchmarkNoticeCode;

/**
 * Fixes the alignment of a benchmark and its comparison with a portfolio against hand-derived numbers. The valuation
 * dates are consecutive calendar days, so the carry limit of five days only matters where a test says so.
 */
class BenchmarkComparisonTest {

  private static final LocalDate DAY_0 = LocalDate.of(2025, 6, 2);
  private static final double EPS = 1e-12;

  private static LocalDate day(int i) {
    return DAY_0.plusDays(i);
  }

  private static List<LocalDate> days(int count) {
    List<LocalDate> dates = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      dates.add(day(i));
    }
    return dates;
  }

  private static List<Quote> quotes(int firstDay, double... values) {
    List<Quote> quotes = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      quotes.add(new Quote(day(firstDay + i), values[i]));
    }
    return quotes;
  }

  private static ReturnSeries portfolio(double... values) {
    List<ValuationPoint> points = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      points.add(new ValuationPoint(day(i), values[i], 0, 0, true));
    }
    return ReturnSeries.of(points, Convention.REPORT);
  }

  private static BenchmarkSeries.Result align(int count, List<Quote> instrument, List<Quote> rate) {
    return BenchmarkSeries.align(new BenchmarkSeries.Input(days(count), instrument, rate, "USD", "CHF"));
  }

  private static boolean hasNotice(BenchmarkSeries.Result result, BenchmarkNoticeCode code) {
    return result.notices().stream().anyMatch(n -> n.code() == code);
  }

  @Test
  @DisplayName("Benchmark from day 2: the headline compares only the common days, 0 % against +10 %, -10 pp")
  void lateBenchmarkComparesTheCommonIntervalsOnly() {
    ReturnSeries series = portfolio(100, 110, 120, 120, 120);
    BenchmarkSeries.Result aligned = align(5, quotes(2, 100, 105, 110), null);
    assertEquals(2, aligned.k());
    assertEquals(4, aligned.e());
    assertTrue(hasNotice(aligned, BenchmarkNoticeCode.BENCHMARK_START_LATER));

    BenchmarkComparison comparison = new BenchmarkComparison(series, aligned);
    assertEquals(0.2, series.totalReturn(), EPS, "the portfolio figure of the whole report stays +20 %");
    assertEquals(0.0, comparison.portfolioReturn(2, 4), EPS);
    assertEquals(0.1, comparison.benchmarkReturn(2, 4), EPS);
    BenchmarkMetrics metrics = comparison.metrics();
    assertEquals(0.0, metrics.portfolioTwrPercent(), EPS);
    assertEquals(10.0, metrics.benchmarkTwrPercent(), EPS);
    assertEquals(-10.0, metrics.excessReturnPercent(), EPS);
    assertEquals(2, metrics.includedIntervals());

    assertNull(comparison.benchmarkReturn(0, 4), "a window reaching before the start has no comparison");
    assertEquals(BenchmarkCoverage.BENCHMARK_PARTIAL, comparison.coverage(0, 4));
    assertEquals(BenchmarkCoverage.BENCHMARK_NONE, comparison.coverage(0, 1));
    assertEquals(BenchmarkCoverage.BENCHMARK_COVERED, comparison.coverage(3, 4));

    List<BenchmarkChartPoint> chart = comparison.chart(new Double[5]);
    assertNull(chart.get(1).benchmarkTwrPercent());
    assertEquals(0.0, chart.get(2).portfolioTwrPercent(), EPS);
    assertEquals(0.0, chart.get(2).benchmarkTwrPercent(), EPS);
    assertEquals(10.0, chart.get(4).benchmarkTwrPercent(), EPS);
  }

  @Test
  @DisplayName("Portfolio 100 -> 110 against a benchmark 200 -> 220: both +10 %, difference 0 pp")
  void priceLevelsDoNotMatter() {
    BenchmarkComparison comparison = new BenchmarkComparison(portfolio(100, 110),
        align(2, quotes(0, 200, 220), null));
    BenchmarkMetrics metrics = comparison.metrics();
    assertEquals(10.0, metrics.portfolioTwrPercent(), EPS);
    assertEquals(10.0, metrics.benchmarkTwrPercent(), EPS);
    assertEquals(0.0, metrics.excessReturnPercent(), EPS);
  }

  @Test
  @DisplayName("Constant USD price, CHF per USD 0.90 -> 0.99: +10 % in CHF")
  void currencyReturnOfAConstantPrice() {
    BenchmarkSeries.Result aligned = align(2, quotes(0, 100, 100), quotes(0, 0.90, 0.99));
    BenchmarkComparison comparison = new BenchmarkComparison(portfolio(100, 100), aligned);
    assertEquals(0.1, comparison.benchmarkReturn(0, 1), 1e-12);
    // The inverse pair CHF/USD delivers 1/rate, which the loader inverts back; the levels are the same.
    BenchmarkSeries.Result inverted = align(2, quotes(0, 100, 100), quotes(0, 1 / (1 / 0.90), 1 / (1 / 0.99)));
    assertEquals(0.1, new BenchmarkComparison(portfolio(100, 100), inverted).benchmarkReturn(0, 1), 1e-12);
  }

  @Test
  @DisplayName("A quote covers five calendar days, the sixth day ends the span with a stale notice")
  void carryLimitEndsTheSpan() {
    // Quote on day 0 only; days 1 to 5 are carried, day 6 is not covered any more.
    BenchmarkSeries.Result aligned = align(7, quotes(0, 100), null);
    assertEquals(0, aligned.k());
    assertEquals(5, aligned.e());
    assertTrue(aligned.instrumentObserved()[0]);
    assertFalse(aligned.instrumentObserved()[1], "a carried close is no observation");
    assertNull(aligned.levels()[6]);
    assertTrue(hasNotice(aligned, BenchmarkNoticeCode.BENCHMARK_STALE));
    assertEquals(day(0), aligned.lastInstrumentDate());
  }

  @Test
  @DisplayName("A seed quote before the first date covers it, a later carried close with a fresh rate still moves")
  void seedQuoteAndCurrencyMoveOnACarriedClose() {
    // Seed on day -3, rate observed every day: the close is carried, the level follows the rate.
    List<Quote> instrument = List.of(new Quote(day(-3), 50));
    BenchmarkSeries.Result aligned = align(3, instrument, quotes(0, 1.0, 1.1, 1.21));
    assertEquals(0, aligned.k());
    assertEquals(2, aligned.e());
    assertEquals(60.5, aligned.levels()[2], 1e-9);
    assertFalse(aligned.instrumentObserved()[1]);
    assertTrue(aligned.rateObserved()[1]);
  }

  @Test
  @DisplayName("A zero value of a formula benchmark is invalid data: no span and a notice with date and leg")
  void nonPositiveValueSuppressesTheComparison() {
    BenchmarkSeries.Result aligned = align(3, quotes(0, 2, 0, 1), null);
    assertFalse(aligned.hasSpan());
    BenchmarkNotice notice = aligned.notices().getFirst();
    assertEquals(BenchmarkNoticeCode.BENCHMARK_INVALID_PRICE, notice.code());
    assertEquals(day(1).toString(), notice.parameters().get("date"));
    assertEquals(BenchmarkNotice.LEG_INSTRUMENT, notice.parameters().get("leg"));
    assertNull(new BenchmarkComparison(portfolio(100, 101, 102), aligned).metrics());
  }

  @Test
  @DisplayName("Prices without any rate give the notice about the missing conversion history")
  void missingRateHistory() {
    BenchmarkSeries.Result aligned = align(2, quotes(0, 100, 101), List.of());
    assertFalse(aligned.hasSpan());
    assertEquals(BenchmarkNoticeCode.BENCHMARK_NO_FX_HISTORY, aligned.notices().getFirst().code());
    assertEquals("USD", aligned.notices().getFirst().parameters().get("from"));
  }

  @Test
  @DisplayName("Days without portfolio capital are skipped on both sides and suppress the annualized figures")
  void gapInTheExposureIsSkippedOnBothSides() {
    // Day 2: everything sold at the close (outflow 110), day 3 holds nothing, day 4 a new deposit of 50.
    List<ValuationPoint> points = List.of(new ValuationPoint(day(0), 100, 0, 0, true),
        new ValuationPoint(day(1), 110, 0, 0, true), new ValuationPoint(day(2), 0, 0, 110, true),
        new ValuationPoint(day(3), 0, 0, 0, true), new ValuationPoint(day(4), 50, 50, 0, true),
        new ValuationPoint(day(5), 55, 0, 0, true));
    ReturnSeries series = ReturnSeries.of(points, Convention.REPORT);
    BenchmarkComparison comparison = new BenchmarkComparison(series,
        align(6, quotes(0, 100, 101, 102, 103, 104, 105), null));
    assertTrue(comparison.partialExposure());
    assertFalse(comparison.isCommon(3), "the day without capital is no common interval");
    assertEquals(4, comparison.includedIntervals());
    assertEquals(1.1 * 1.1 - 1, comparison.portfolioReturn(0, 5), EPS);
    assertEquals(102.0 / 100 * 105.0 / 103 - 1, comparison.benchmarkReturn(0, 5), EPS);
    assertNull(comparison.metrics().portfolioTwrAnnualizedPercent());
  }
}
