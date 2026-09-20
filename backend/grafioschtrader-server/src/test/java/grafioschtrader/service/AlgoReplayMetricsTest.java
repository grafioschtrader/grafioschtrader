package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.service.AlgoReplayMetrics.EquityPoint;
import grafioschtrader.service.AlgoReplayMetrics.Trade;

/**
 * Fixes the calculation conventions of a historical replay against hand-derived numbers.
 *
 * <p>
 * The expectations are spelled out as formulas over the literal equity values rather than taken from a previous run, so
 * the test says what the convention is instead of only that it has not changed. What it pins down is not only the
 * arithmetic but the decision that an undefined figure stays empty: a Sharpe ratio of a portfolio that never moved is
 * not zero, and a return of a portfolio that started at nothing is not a return at all.
 * </p>
 */
class AlgoReplayMetricsTest {

  @Test
  @DisplayName("A non-trading terminal day changes return but adds no Sharpe observation")
  void terminalIncomeDoesNotAddSharpeObservation() {
    var original = AlgoReplayMetrics.of(SERIES, List.of());
    var withTerminal = AlgoReplayMetrics.of(SERIES, List.of(), priced(START.plusDays(4), 125));
    assertEquals(0.25, withTerminal.totalReturn(), 1e-12);
    assertEquals(original.sharpeRatio(), withTerminal.sharpeRatio());
  }

  @Test
  @DisplayName("A standing-order deposit on a non-trading end date remains cash-flow neutral")
  void terminalDepositIsRemovedFromReturn() {
    var metrics = AlgoReplayMetrics.of(List.of(priced(START, 100)), List.of(),
        new EquityPoint(START.plusDays(1), 125, true, 25));

    assertEquals(0.0, metrics.totalReturn(), 1e-12);
    assertEquals(0.0, metrics.maxDrawdown(), 1e-12);
  }

  private static final LocalDate START = LocalDate.of(2026, 1, 1);

  /** A day whose closing state was valued completely, which is the ordinary case. */
  private static EquityPoint priced(LocalDate date, double equity) {
    return new EquityPoint(date, equity, true);
  }

  /**
   * A day on which at least one position or currency had no usable closing price. Its equity is whatever happened to be
   * priced, so it is recorded but must not be treated as an observation.
   */
  private static EquityPoint unpriced(LocalDate date, double equity) {
    return new EquityPoint(date, equity, false);
  }

  /** 100, 110, 99, 121 on four consecutive days: one full recovery with a dip in the middle. */
  private static final List<EquityPoint> SERIES = List.of(priced(START, 100), priced(START.plusDays(1), 110),
      priced(START.plusDays(2), 99), priced(START.plusDays(3), 121));

  @Test
  @DisplayName("Total return, drawdown and Sharpe follow the recorded conventions")
  void figuresOfAMovingPortfolio() {
    var metrics = AlgoReplayMetrics.of(SERIES, List.of());

    assertEquals(0.21, metrics.totalReturn(), 1e-12, "121 against an opening equity of 100");
    // The peak is 110 on the second day, so the third day is 10 percent below it; the recovery adds no drawdown.
    assertEquals(-0.1, metrics.maxDrawdown(), 1e-12);

    double first = 110.0 / 100 - 1;
    double second = 99.0 / 110 - 1;
    double third = 121.0 / 99 - 1;
    double mean = (first + second + third) / 3;
    double variance = ((first - mean) * (first - mean) + (second - mean) * (second - mean)
        + (third - mean) * (third - mean)) / 2;
    assertEquals(mean / Math.sqrt(variance) * Math.sqrt(252), metrics.sharpeRatio(), 1e-12,
        "sample deviation of the daily returns, annualized with the square root of 252, risk free rate zero");
  }

  @Test
  @DisplayName("A year long run annualizes to its own total return")
  void annualizationUsesCalendarDays() {
    var metrics = AlgoReplayMetrics
        .of(List.of(priced(LocalDate.of(2025, 1, 1), 100), priced(LocalDate.of(2026, 1, 1), 110)), List.of());

    assertEquals(0.1, metrics.totalReturn(), 1e-12);
    assertEquals(0.1, metrics.annualizedReturn(), 1e-12, "exactly 365 days, so scaling changes nothing");
  }

  @Test
  @DisplayName("A closed round trip counts once, and a break even one neither wins nor loses")
  void tradeCounts() {
    var metrics = AlgoReplayMetrics.of(SERIES, List.of(new Trade(5), new Trade(-3), new Trade(0)));

    assertEquals(3, metrics.totalTrades());
    assertEquals(1, metrics.winningTrades());
    assertEquals(1, metrics.losingTrades());
  }

  @Test
  @DisplayName("A portfolio that never moved has no Sharpe ratio rather than one of zero")
  void constantEquityHasNoDeviation() {
    var metrics = AlgoReplayMetrics
        .of(List.of(priced(START, 100), priced(START.plusDays(1), 100), priced(START.plusDays(2), 100)), List.of());

    assertEquals(0.0, metrics.totalReturn(), 1e-12);
    assertNull(metrics.sharpeRatio(), "dividing by a deviation of zero is undefined, not neutral");
    assertEquals(0, metrics.totalTrades());
  }

  @Test
  @DisplayName("Deposits and withdrawals change capital but not investment performance")
  void externalCashFlowsAreRemovedFromReturns() {
    var series = List.of(new EquityPoint(START, 100, true), new EquityPoint(START.plusDays(1), 121, true, 10),
        new EquityPoint(START.plusDays(2), 122.1, true, -10));
    var metrics = AlgoReplayMetrics.of(series, List.of());

    assertEquals(0.21, metrics.totalReturn(), 1e-12, "both periods earned ten percent after their capital flow");
    assertEquals(0.0, metrics.maxDrawdown(), 1e-12);
  }

  @Test
  @DisplayName("A first deposit establishes the capital base of an empty simulation")
  void depositCanEstablishTheFirstPositiveCapitalBase() {
    var metrics = AlgoReplayMetrics
        .of(List.of(new EquityPoint(START, 0, true), new EquityPoint(START.plusDays(1), 100, true, 100)), List.of());

    assertEquals(0.0, metrics.totalReturn(), 1e-12);
    assertEquals(0.0, metrics.maxDrawdown(), 1e-12);
  }

  @Test
  @DisplayName("A single day yields no return series at all")
  void oneObservation() {
    var metrics = AlgoReplayMetrics.of(List.of(priced(START, 100)), List.of());

    assertNull(metrics.totalReturn());
    assertNull(metrics.annualizedReturn());
    assertNull(metrics.sharpeRatio());
    assertEquals(0.0, metrics.maxDrawdown(), 1e-12, "a single observation is its own peak");
  }

  @Test
  @DisplayName("An environment that opens with nothing has no relative return")
  void nonPositiveOpeningEquity() {
    var metrics = AlgoReplayMetrics.of(List.of(priced(START, 0), priced(START.plusDays(1), 50)), List.of());

    assertNull(metrics.totalReturn(), "a relative return needs something to be relative to");
    assertNull(metrics.annualizedReturn());
    assertNull(metrics.sharpeRatio(), "the first day contributes no return, so a single one is left");
  }

  @Test
  @DisplayName("A run that opens before its price data measures only the days it could value")
  void leadingUnpricedDaysAreNoObservation() {
    // Two days on which nothing could be priced, then the same movement as SERIES over the following four.
    var metrics = AlgoReplayMetrics
        .of(List.of(unpriced(START, 0), unpriced(START.plusDays(1), 0), priced(START.plusDays(2), 100),
            priced(START.plusDays(3), 110), priced(START.plusDays(4), 99), priced(START.plusDays(5), 121)), List.of());

    assertEquals(0.21, metrics.totalReturn(), 1e-12, "measured from the first day that could be valued");
    assertEquals(-0.1, metrics.maxDrawdown(), 1e-12, "the dead days are no peak and no trough");

    double first = 110.0 / 100 - 1;
    double second = 99.0 / 110 - 1;
    double third = 121.0 / 99 - 1;
    double mean = (first + second + third) / 3;
    double variance = ((first - mean) * (first - mean) + (second - mean) * (second - mean)
        + (third - mean) * (third - mean)) / 2;
    assertEquals(mean / Math.sqrt(variance) * Math.sqrt(252), metrics.sharpeRatio(), 1e-12,
        "the zero returns of the dead days would otherwise flatten the mean and the deviation alike");
  }

  @Test
  @DisplayName("Annualization spans the valued days, not the days the run was asked for")
  void annualizationIgnoresTheDeadStretch() {
    var metrics = AlgoReplayMetrics.of(List.of(unpriced(LocalDate.of(2024, 1, 1), 0),
        priced(LocalDate.of(2025, 1, 1), 100), priced(LocalDate.of(2026, 1, 1), 110)), List.of());

    assertEquals(0.1, metrics.totalReturn(), 1e-12);
    assertEquals(0.1, metrics.annualizedReturn(), 1e-12,
        "365 valued days, so scaling changes nothing; counting the dead year would halve the rate");
  }

  @Test
  @DisplayName("A run that could value nothing reports no figure rather than a figure of zero")
  void everyDayUnpriced() {
    var metrics = AlgoReplayMetrics
        .of(List.of(unpriced(START, 0), unpriced(START.plusDays(1), 0), unpriced(START.plusDays(2), 0)), List.of());

    assertNull(metrics.totalReturn());
    assertNull(metrics.annualizedReturn());
    assertNull(metrics.sharpeRatio());
    assertNull(metrics.maxDrawdown(), "there was no observation to draw down from");
  }

  @Test
  @DisplayName("A run that only ever lost reports its worst point, not its last")
  void drawdownIsMeasuredFromTheRunningPeak() {
    var metrics = AlgoReplayMetrics
        .of(List.of(priced(START, 100), priced(START.plusDays(1), 50), priced(START.plusDays(2), 80)), List.of());

    assertEquals(-0.5, metrics.maxDrawdown(), 1e-12);
    assertEquals(-0.2, metrics.totalReturn(), 1e-12);
  }
}
