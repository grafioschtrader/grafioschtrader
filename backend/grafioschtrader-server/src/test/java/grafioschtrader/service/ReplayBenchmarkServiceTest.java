package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.reportviews.performance.BenchmarkDescriptor;
import grafioschtrader.reportviews.performance.BenchmarkResultFactory;
import grafioschtrader.reportviews.performance.BenchmarkSeries;
import grafioschtrader.reportviews.performance.BenchmarkSeries.Quote;
import grafioschtrader.reportviews.performance.PerformanceBenchmarkResult;
import grafioschtrader.types.BenchmarkReturnBasis;
import grafioschtrader.types.BenchmarkSource;

/**
 * The comparison of a replay curve with a benchmark, without database: the valuation points of the curve, the flow
 * timing of the replay in the shadow portfolio, the annualization from the first day and percentages scaled once.
 */
class ReplayBenchmarkServiceTest {

  /** Friday, so the third point falls on a Monday after a weekend. */
  private static final LocalDate FRI = LocalDate.of(2025, 1, 3);

  private static PerformanceBenchmarkResult compare(List<SimulationRunEquityPoint> curve, double... levels) {
    List<ValuationPoint> points = ReplayBenchmarkService.valuationPoints(curve, (_, _) -> true);
    List<LocalDate> dates = points.stream().map(ValuationPoint::date).toList();
    List<Quote> quotes = new ArrayList<>();
    for (int i = 0; i < levels.length; i++) {
      quotes.add(new Quote(dates.get(i), levels[i]));
    }
    List<DatedFlow> flows = new ArrayList<>();
    for (int i = 1; i < curve.size(); i++) {
      double flow = curve.get(i).investedCapital() - curve.get(i - 1).investedCapital();
      if (flow != 0) {
        flows.add(new DatedFlow(curve.get(i).date(), flow));
      }
    }
    return BenchmarkResultFactory.build(
        new BenchmarkDescriptor(1, "Index", "CHF", BenchmarkSource.BENCHMARK_SOURCE_PARENT_TENANT, "CHF", null, false,
            BenchmarkReturnBasis.AS_SUPPLIED, List.of()),
        BenchmarkSeries.align(new BenchmarkSeries.Input(dates, quotes, null, "CHF", "CHF")),
        ReturnSeries.of(points, Convention.REPLAY), points, flows, dates.getFirst(), dates.getLast(),
        new BenchmarkResultFactory.Settings(Convention.REPLAY, 0, null)).result();
  }

  @Test
  @DisplayName("Opening 100, deposit 50 and withdrawal 20 on the next day, benchmark +10 %: the shadow ends at 143")
  void shadowFollowsTheFlowTimingOfTheReplay() {
    // The invested capital rises by 30 net; under the replay convention every flow counts at the start of the day
    List<SimulationRunEquityPoint> curve = List.of(new SimulationRunEquityPoint(FRI, 100, 100),
        new SimulationRunEquityPoint(FRI.plusDays(1), 140, 130));
    PerformanceBenchmarkResult result = compare(curve, 200, 220);
    assertEquals(143, result.shadow().valueEndMC(), 1e-9, "(100 + 50 - 20) * 1.1, both flows at the start");
    assertEquals(10.0, result.metrics().benchmarkTwrPercent(), 1e-9, "in percent, scaled exactly once");
  }

  @Test
  @DisplayName("A replay of three days is annualized, because the replay annualizes from the first day on")
  void annualizationFromTheFirstDay() {
    List<SimulationRunEquityPoint> curve = List.of(new SimulationRunEquityPoint(FRI, 100, 100),
        new SimulationRunEquityPoint(FRI.plusDays(3), 101, 100));
    PerformanceBenchmarkResult result = compare(curve, 100, 100.5);
    assertNotNull(result.metrics().portfolioTwrAnnualizedPercent());
    assertNotNull(result.metrics().benchmarkTwrAnnualizedPercent());
  }

  @Test
  @DisplayName("A weekend terminal point is no expected session, so its interval is no daily risk interval")
  void weekendTerminalPointIsIrregular() {
    LocalDate saturday = FRI.plusDays(1);
    List<SimulationRunEquityPoint> curve = List.of(new SimulationRunEquityPoint(FRI, 100, 100),
        new SimulationRunEquityPoint(saturday, 100, 100));
    List<LocalDate> sessions = List.of(FRI, FRI.plusDays(3));
    List<ValuationPoint> points = ReplayBenchmarkService.valuationPoints(curve,
        (before, current) -> sessions.contains(before) && sessions.contains(current)
            && sessions.indexOf(current) == sessions.indexOf(before) + 1);
    assertFalse(points.get(1).regular());
    assertTrue(points.getFirst().regular());
    assertEquals(0, points.get(1).inflow(), 1e-12);
  }
}
