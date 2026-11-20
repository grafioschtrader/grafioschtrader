package grafioschtrader.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.MoneyDrawdown.Result;
import grafioschtrader.common.MoneyDrawdown.Step;

/**
 * Fixes the money-based drawdown against results calculated by hand. Day {@code n} is {@link #DAY1} plus n - 1 days.
 */
class MoneyDrawdownTest {

  private static final double EPS = 1e-12;
  private static final LocalDate DAY1 = LocalDate.of(2025, 3, 3);

  private static LocalDate day(int n) {
    return DAY1.plusDays(n - 1L);
  }

  private static Step step(int n, Double value, double in, double out) {
    return new Step(day(n), value, in, out);
  }

  @Test
  @DisplayName("Purchase, decline, purchase below the high, dividend, partial and full sale")
  void corePhase() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1000, 0), step(2, 1100.0, 0, 0), step(3, 880.0, 0, 0),
        step(4, 1760.0, 880, 0), step(5, 1800.0, 0, 40), step(6, 990.0, 0, 990), step(7, 0.0, 0, 950)));
    // M: 1000, 1100, 1100, 1980, 1980 * 1800 / 1840, 990, 0
    assertEquals(0, r.drawdown().get(0), EPS);
    assertEquals(0, r.drawdown().get(1), EPS);
    assertEquals(880.0 / 1100 - 1, r.drawdown().get(2), EPS);
    assertEquals(1760.0 / 1980 - 1, r.drawdown().get(3), EPS);
    assertEquals(1840.0 / 1980 - 1, r.drawdown().get(4), EPS);
    assertEquals(0, r.drawdown().get(5), EPS);
    assertEquals(950.0 / 990 - 1, r.drawdown().get(6), EPS);
    assertEquals(-0.2, r.maxDrawdown().depth(), EPS);
    assertEquals(day(2), r.maxDrawdown().peakDate());
    assertEquals(day(3), r.maxDrawdown().troughDate());
    assertEquals(day(6), r.maxDrawdown().recoveryDate());
    assertEquals(4, r.maxDrawdown().durationDays());
    assertNull(r.longestUnderwater(), "the longest run is the episode of the deepest drawdown");
    assertEquals(950.0 / 990 - 1, r.current(), EPS);
  }

  @Test
  @DisplayName("A sale at the valuation price below the high leaves the drawdown unchanged on the following day")
  void saleAtValuationPrice() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1000, 0), step(2, 800.0, 0, 0), step(3, 400.0, 0, 400),
        step(4, 400.0, 0, 0)));
    assertEquals(-0.2, r.drawdown().get(1), EPS);
    assertEquals(-0.2, r.drawdown().get(2), EPS);
    assertEquals(-0.2, r.drawdown().get(3), EPS);
  }

  @Test
  @DisplayName("A purchase above the close with a fee shows a drawdown on its first day")
  void purchaseAboveClose() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1010, 0)));
    assertEquals(1000.0 / 1010 - 1, r.drawdown().get(0), EPS);
  }

  @Test
  @DisplayName("A position closed at a loss ends with that loss, not with -100 %")
  void closedAtLoss() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1000, 0), step(2, 0.0, 0, 900)));
    assertEquals(-0.1, r.drawdown().get(1), EPS);
    assertEquals(-0.1, r.current(), EPS);
  }

  @Test
  @DisplayName("A value below zero floors at -100 %")
  void negativeValueFloors() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1000, 0), step(2, -200.0, 0, 0)));
    assertEquals(-1, r.drawdown().get(1), EPS);
  }

  @Test
  @DisplayName("A step without value keeps its flows for the next valued step")
  void missingValueCarriesFlows() {
    Result r = MoneyDrawdown.of(List.of(step(1, 1000.0, 1000, 0), step(2, null, 500, 0), step(3, 1500.0, 0, 0)));
    assertNull(r.drawdown().get(1));
    assertEquals(0, r.drawdown().get(2), EPS);
  }

  @Test
  @DisplayName("The longest time below the high is reported separately when it is not the deepest episode")
  void longestUnderwaterSeparate() {
    List<Step> steps = new ArrayList<>();
    steps.add(step(1, 100.0, 100, 0));
    for (int n = 2; n <= 9; n++) {
      steps.add(step(n, 95.0, 0, 0));
    }
    steps.add(step(10, 100.0, 0, 0));
    steps.add(step(11, 70.0, 0, 0));
    steps.add(step(12, 100.0, 0, 0));
    Result r = MoneyDrawdown.of(steps);
    assertEquals(-0.3, r.maxDrawdown().depth(), EPS);
    assertEquals(day(10), r.maxDrawdown().peakDate());
    assertEquals(2, r.maxDrawdown().durationDays());
    assertEquals(day(1), r.longestUnderwater().from());
    assertEquals(day(10), r.longestUnderwater().to());
    assertEquals(9, r.longestUnderwater().durationDays());
  }
}
