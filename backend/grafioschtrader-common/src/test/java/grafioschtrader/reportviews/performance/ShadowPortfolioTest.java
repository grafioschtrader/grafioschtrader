package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.reportviews.performance.BenchmarkSeries.Quote;

/**
 * Fixes the shadow portfolio against hand-calculated values: every flow of the portfolio invested in the benchmark.
 */
class ShadowPortfolioTest {

  private static final LocalDate D0 = LocalDate.of(2025, 1, 6);
  private static final double EPS = 1e-9;

  @Test
  @DisplayName("Start 100, deposit 50, benchmark +10 %, withdrawal 20: REPORT ends at 145, REPLAY at 143")
  void flowTimingOfTheConvention() {
    // Deposit on day 1 and withdrawal on day 3, both inside the one valuation interval (D0, D0+4]
    List<ValuationPoint> points = List.of(new ValuationPoint(D0, 100, 0, 0, true),
        new ValuationPoint(D0.plusDays(4), 140, 50, 20, true));
    Double[] levels = { 200.0, 220.0 };
    List<DatedFlow> flows = List.of(new DatedFlow(D0.plusDays(1), 50), new DatedFlow(D0.plusDays(3), -20));

    ShadowPortfolio.Result report = ShadowPortfolio.calculate(points, levels, 0, 1, Convention.REPORT, flows);
    assertEquals(145, report.values()[1], EPS);
    ShadowPortfolioResult summary = report.summary();
    assertEquals(100, summary.valueStartMC(), EPS);
    assertEquals(145, summary.valueEndMC(), EPS);
    assertEquals(15, summary.gainMC(), EPS, "145 - 100 - (50 - 20)");
    assertEquals(-5, summary.differenceMC(), EPS, "the portfolio ends at 140, five below its shadow");
    assertNull(summary.shortfallDate());

    ShadowPortfolio.Result replay = ShadowPortfolio.calculate(points, levels, 0, 1, Convention.REPLAY, flows);
    assertEquals(143, replay.values()[1], EPS);
  }

  @Test
  @DisplayName("A withdrawal larger than the shadow value ends only the shadow figures, with the date")
  void shortfallEndsTheShadowPortfolio() {
    // The portfolio rose to 200 and pays out 150; the shadow fell to 90 and cannot fund it
    List<ValuationPoint> points = List.of(new ValuationPoint(D0, 100, 0, 0, true),
        new ValuationPoint(D0.plusDays(1), 200, 0, 0, true), new ValuationPoint(D0.plusDays(2), 50, 0, 150, true),
        new ValuationPoint(D0.plusDays(3), 55, 0, 0, true));
    Double[] levels = { 100.0, 90.0, 90.0, 99.0 };
    ShadowPortfolio.Result result = ShadowPortfolio.calculate(points, levels, 0, 3, Convention.REPORT,
        List.of(new DatedFlow(D0.plusDays(2), -150)));
    assertEquals(90, result.values()[1], EPS);
    assertNull(result.values()[2]);
    assertNull(result.values()[3]);
    ShadowPortfolioResult summary = result.summary();
    assertEquals(D0.plusDays(2), summary.shortfallDate());
    assertEquals(100, summary.valueStartMC(), EPS);
    assertNull(summary.valueEndMC());
    assertNull(summary.mwrPercent());
  }

  @Test
  @DisplayName("Without flows the money-weighted return of the shadow equals the benchmark return")
  void moneyWeightedReturnWithoutFlowsIsTheBenchmarkReturn() {
    List<ValuationPoint> points = new ArrayList<>();
    double[] benchmark = { 100, 103, 98, 105, 112 };
    Double[] levels = new Double[benchmark.length];
    for (int i = 0; i < benchmark.length; i++) {
      points.add(new ValuationPoint(D0.plusDays(30L * i), 1000 + i, 0, 0, true));
      levels[i] = benchmark[i];
    }
    ShadowPortfolioResult summary = ShadowPortfolio
        .calculate(points, levels, 0, benchmark.length - 1, Convention.REPORT, List.of()).summary();
    assertEquals(1120, summary.valueEndMC(), EPS);
    assertNotNull(summary.mwrStatus());
    assertEquals(12.0, summary.mwrPercent(), 1e-4);
  }

  @Test
  @DisplayName("A late benchmark start: portfolio and shadow meet at the first day of the span")
  void lateStartMeetsAtTheFirstDayOfTheSpan() {
    List<ValuationPoint> points = new ArrayList<>();
    double[] values = { 100, 110, 120, 126, 132 };
    for (int i = 0; i < values.length; i++) {
      points.add(new ValuationPoint(D0.plusDays(i), values[i], 0, 0, true));
    }
    List<LocalDate> dates = points.stream().map(ValuationPoint::date).toList();
    BenchmarkSeries.Result aligned = BenchmarkSeries.align(new BenchmarkSeries.Input(dates,
        List.of(new Quote(D0.plusDays(2), 50), new Quote(D0.plusDays(3), 55), new Quote(D0.plusDays(4), 60)), null,
        "CHF", "CHF"));
    ShadowPortfolio.Result shadow = ShadowPortfolio.calculate(points, aligned.levels(), aligned.k(), aligned.e(),
        Convention.REPORT, List.of());
    List<BenchmarkChartPoint> chart = new BenchmarkComparison(ReturnSeries.of(points, Convention.REPORT), aligned)
        .chart(shadow.values());
    assertNull(chart.get(1).shadowValueMC());
    assertEquals(120, chart.get(2).portfolioValueMC(), EPS);
    assertEquals(120, chart.get(2).shadowValueMC(), EPS);
    assertEquals(144, chart.get(4).shadowValueMC(), EPS, "120 * 60 / 50");
  }
}
