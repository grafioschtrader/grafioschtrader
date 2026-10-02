package grafioschtrader.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.Drawdown;
import grafioschtrader.common.ReturnSeries.ValuationPoint;

/**
 * Fixes the time-weighted return conventions against hand-derived numbers. Every point is written out with its inflow
 * and outflow, so the test shows which flow timing produced which figure.
 */
class ReturnSeriesTest {

  private static final LocalDate DAY_1 = LocalDate.of(2025, 3, 3);
  private static final double EPS = 1e-12;

  private static ValuationPoint point(int day, double value) {
    return new ValuationPoint(DAY_1.plusDays(day - 1), value, 0, 0, true);
  }

  private static ValuationPoint point(int day, double value, double inflow, double outflow) {
    return new ValuationPoint(DAY_1.plusDays(day - 1), value, inflow, outflow, true);
  }

  @Test
  @DisplayName("100, 110, 99 without flows: -1 %, drawdown -10 % from day 2 to day 3, not recovered")
  void returnAndDrawdownWithoutFlows() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 110), point(3, 99)), Convention.REPORT);
    assertEquals(-0.01, series.totalReturn(), EPS);
    Drawdown drawdown = series.drawdown();
    assertEquals(-0.1, drawdown.depth(), EPS);
    assertEquals(DAY_1.plusDays(1), drawdown.peakDate());
    assertEquals(DAY_1.plusDays(2), drawdown.troughDate());
    assertNull(drawdown.recoveryDate());
    assertEquals(-0.1, drawdown.current(), EPS);
  }

  @Test
  @DisplayName("The longest phase below a high wins, even when it is not the deepest one")
  void drawdownDurationIsTheLongestPhaseNotTheDeepest() {
    // Day 1 to 3: 10 % deep, regained after 2 days. Day 3 to 7: 1 % deep, regained after 4 days.
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 90), point(3, 100), point(4, 99),
        point(5, 99.5), point(6, 99.8), point(7, 101)), Convention.REPLAY);
    assertEquals(-0.1, series.drawdown().depth(), EPS);
    assertEquals(4, series.maxDrawdownDurationDays());
    assertEquals(0, ReturnSeries.of(List.of(point(1, 100), point(2, 100), point(3, 105)), Convention.REPLAY)
        .maxDrawdownDurationDays(), "a series that never falls has no phase below its high");
    assertNull(ReturnSeries.of(List.of(point(1, 100)), Convention.REPLAY).maxDrawdownDurationDays());
  }

  @Test
  @DisplayName("A deposit counts as flowed at the start of the day: 100 + 100 -> 210 is 5 %")
  void inflowAtStart() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 210, 100, 0)), Convention.REPORT);
    assertEquals(0.05, series.linked(0, 1), EPS);
  }

  @Test
  @DisplayName("Gain to 110 and withdrawal of 110 on the same day is 10 % with REPORT, unusable with REPLAY")
  void fullWithdrawalAtEnd() {
    List<ValuationPoint> points = List.of(point(1, 100), point(2, 0, 0, 110));
    assertEquals(0.1, ReturnSeries.of(points, Convention.REPORT).totalReturn(), EPS);
    assertNull(ReturnSeries.of(points, Convention.REPLAY).totalReturn(), "capital base 100 - 110 is negative");
  }

  @Test
  @DisplayName("Withdrawal of 90 on a day with +5 % is 5 %, not 50 %")
  void partialWithdrawalAtEnd() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 15, 0, 90)), Convention.REPORT);
    assertEquals(0.05, series.totalReturn(), EPS);
  }

  @Test
  @DisplayName("Zero base: the first usable return is the one after the first deposit")
  void zeroBase() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 0), point(2, 0), point(3, 1010, 1000, 0)),
        Convention.REPORT);
    assertNull(series.linked(0, 1));
    assertArrayEquals(new double[] { 0.01 }, series.usableReturns(), EPS);
    assertEquals(0.01, series.totalReturn(), EPS);
  }

  @Test
  @DisplayName("Total loss and restart: the later span keeps its own return, the whole period stays at -100 %")
  void totalLossAndRestart() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 0), point(3, 55, 50, 0)), Convention.REPORT);
    assertEquals(-1.0, series.linked(0, 1), EPS);
    assertEquals(0.1, series.linked(1, 2), EPS);
    assertEquals(-1.0, series.totalReturn(), EPS);
    assertEquals(-1.0, series.drawdown().depth(), EPS);
    assertEquals(-1.0, series.drawdown().current(), EPS);
  }

  @Test
  @DisplayName("A negative value counts as total loss with REPORT")
  void negativeValueIsTotalLoss() {
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, -20)), Convention.REPORT);
    assertEquals(-1.0, series.totalReturn(), EPS);
    assertEquals(-1.0, series.drawdown().depth(), EPS);
  }

  @Test
  @DisplayName("100, 110, 99, 105, 111: the previous high is regained on day 5")
  void recovery() {
    ReturnSeries series = ReturnSeries
        .of(List.of(point(1, 100), point(2, 110), point(3, 99), point(4, 105), point(5, 111)), Convention.REPORT);
    Drawdown drawdown = series.drawdown();
    assertEquals(DAY_1.plusDays(4), drawdown.recoveryDate());
    assertEquals(0.0, drawdown.current(), EPS);
  }

  @Test
  @DisplayName("Annualization starts at 360 calendar days; the replay passes a minimum of 0")
  void annualization() {
    assertNull(ReturnSeries.annualize(0.1, 359, ReturnSeries.MIN_ANNUALIZATION_DAYS));
    assertEquals(Math.pow(1.1, 365.0 / 360) - 1, ReturnSeries.annualize(0.1, 360, ReturnSeries.MIN_ANNUALIZATION_DAYS),
        EPS);
    assertNotNull(ReturnSeries.annualize(0.01, 10, 0));
    assertNull(ReturnSeries.annualize(-1.0, 400, ReturnSeries.MIN_ANNUALIZATION_DAYS));
  }

  @Test
  @DisplayName("Volatility needs 20 regular returns; a return across a gap is not counted")
  void volatilityObservations() {
    assertNull(alternatingSeries(19, false).annualizedVolatility(20));
    ReturnSeries twenty = alternatingSeries(20, false);
    assertEquals(20, twenty.volatilityObservations());
    assertNotNull(twenty.annualizedVolatility(20));
    ReturnSeries withGap = alternatingSeries(20, true);
    assertEquals(19, withGap.volatilityObservations());
    assertEquals(1, withGap.gapIntervals());
    assertNull(withGap.annualizedVolatility(20));
  }

  /** Values alternating 100/101; with a gap the last interval is marked irregular. */
  private static ReturnSeries alternatingSeries(int returns, boolean lastIsGap) {
    List<ValuationPoint> points = new ArrayList<>();
    points.add(point(1, 100));
    for (int i = 1; i <= returns; i++) {
      boolean regular = !(lastIsGap && i == returns);
      points.add(new ValuationPoint(DAY_1.plusDays(i), i % 2 == 0 ? 100 : 101, 0, 0, regular));
    }
    return ReturnSeries.of(points, Convention.REPORT);
  }

  @Test
  @DisplayName("Average capital weights the capital base of each interval with its calendar days")
  void averageCapital() {
    // Base of the first interval (1 day) is 100, of the second (2 days) 110 + 100 deposit.
    ReturnSeries series = ReturnSeries.of(List.of(point(1, 100), point(2, 110), point(4, 210, 100, 0)),
        Convention.REPORT);
    assertEquals((100.0 * 1 + 210.0 * 2) / 3, series.averageCapital(), EPS);
  }
}
