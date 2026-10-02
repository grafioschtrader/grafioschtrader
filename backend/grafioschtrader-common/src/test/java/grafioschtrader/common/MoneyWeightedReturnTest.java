package grafioschtrader.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.MoneyWeightedReturn.Result;
import grafioschtrader.types.MoneyWeightedReturnStatus;

/**
 * Checks the internal rate of return against roots derived independently by Newton's method on the daily growth factor,
 * and the status of the cases without a unique root.
 */
class MoneyWeightedReturnTest {

  private static final double EPS = 1e-9;

  /** Newton iteration for f(x) = 0 starting at 1. */
  private static double newton(DoubleUnaryOperator f, DoubleUnaryOperator derivative) {
    double x = 1;
    for (int i = 0; i < 100; i++) {
      x -= f.applyAsDouble(x) / derivative.applyAsDouble(x);
    }
    return x;
  }

  @Test
  @DisplayName("Without flows the money-weighted return is end value over start value")
  void withoutFlows() {
    Result result = MoneyWeightedReturn.solve(LocalDate.of(2024, 1, 1), 1000, LocalDate.of(2024, 3, 1), 1100,
        List.of());
    assertEquals(MoneyWeightedReturnStatus.MWR_CALCULATED, result.status());
    assertEquals(0.1, MoneyWeightedReturn.periodReturn(result), EPS);
  }

  @Test
  @DisplayName("Deposit in the middle: solves 1000 x^365 + 1000 x^183 = 2200")
  void depositInTheMiddle() {
    Result result = MoneyWeightedReturn.solve(LocalDate.of(2024, 1, 1), 1000, LocalDate.of(2024, 12, 31), 2200,
        List.of(new DatedFlow(LocalDate.of(2024, 7, 1), 1000)));
    double x = newton(v -> 1000 * Math.pow(v, 365) + 1000 * Math.pow(v, 183) - 2200,
        v -> 365000 * Math.pow(v, 364) + 183000 * Math.pow(v, 182));
    assertEquals(MoneyWeightedReturnStatus.MWR_CALCULATED, result.status());
    assertEquals(Math.pow(x, 365) - 1, MoneyWeightedReturn.periodReturn(result), EPS);
    assertEquals(Math.pow(x, 365) - 1, MoneyWeightedReturn.annualizedReturn(result, 365), EPS);
  }

  @Test
  @DisplayName("A Monday deposit after a Friday valuation works from Monday, not from Friday")
  void mondayDepositAfterFriday() {
    LocalDate friday = LocalDate.of(2024, 1, 5);
    Result result = MoneyWeightedReturn.solve(friday, 1000, friday.plusDays(7), 2020,
        List.of(new DatedFlow(friday.plusDays(3), 1000)));
    double x = newton(v -> 1000 * Math.pow(v, 7) + 1000 * Math.pow(v, 4) - 2020,
        v -> 7000 * Math.pow(v, 6) + 4000 * Math.pow(v, 3));
    assertEquals(Math.pow(x, 7) - 1, MoneyWeightedReturn.periodReturn(result), EPS);
  }

  @Test
  @DisplayName("A deposit and an equal withdrawal on different days both enter the result")
  void depositAndWithdrawalOnDifferentDays() {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 12, 31);
    double withoutFlows = MoneyWeightedReturn
        .periodReturn(MoneyWeightedReturn.solve(start, 1000, end, 1100, List.of()));
    double withFlows = MoneyWeightedReturn.periodReturn(MoneyWeightedReturn.solve(start, 1000, end, 1100,
        List.of(new DatedFlow(LocalDate.of(2024, 3, 1), 500), new DatedFlow(LocalDate.of(2024, 10, 1), -500))));
    assertNotEquals(withoutFlows, withFlows, 1e-6);
  }

  @Test
  @DisplayName("-100, +230, -132 at 0, 1 and 2 years has two roots (10 % and 20 % p.a.): ambiguous")
  void ambiguous() {
    Result result = MoneyWeightedReturn.solve(LocalDate.of(2021, 1, 1), 100, LocalDate.of(2023, 1, 1), -132,
        List.of(new DatedFlow(LocalDate.of(2022, 1, 1), -230)));
    assertEquals(MoneyWeightedReturnStatus.MWR_AMBIGUOUS, result.status());
    assertNull(MoneyWeightedReturn.periodReturn(result));
  }

  @Test
  @DisplayName("Flows on the same day that cancel out and nothing else give no return, not 0 %")
  void cancellingFlows() {
    LocalDate day = LocalDate.of(2024, 5, 2);
    Result result = MoneyWeightedReturn.solve(LocalDate.of(2024, 5, 1), 0, LocalDate.of(2024, 5, 3), 0,
        List.of(new DatedFlow(day, 100), new DatedFlow(day, -100)));
    assertEquals(MoneyWeightedReturnStatus.MWR_INSUFFICIENT_DATA, result.status());
    assertNull(MoneyWeightedReturn.periodReturn(result));
  }

  @Test
  @DisplayName("Nothing flows back to the investor: total loss of -100 %")
  void totalLoss() {
    Result result = MoneyWeightedReturn.solve(LocalDate.of(2024, 1, 1), 1000, LocalDate.of(2024, 6, 1), 0,
        List.of(new DatedFlow(LocalDate.of(2024, 2, 1), 500)));
    assertEquals(MoneyWeightedReturnStatus.MWR_TOTAL_LOSS, result.status());
    assertEquals(-1.0, MoneyWeightedReturn.periodReturn(result), EPS);
  }

  @Test
  @DisplayName("From 360 days the p.a. value is exp(s * 365 / T) - 1, below it is empty")
  void annualized() {
    LocalDate start = LocalDate.of(2022, 1, 1);
    Result result = MoneyWeightedReturn.solve(start, 1000, start.plusDays(730), 1210, List.of());
    assertEquals(Math.exp(result.periodLogReturn() * 365 / 730) - 1, MoneyWeightedReturn.annualizedReturn(result, 730),
        EPS);
    assertEquals(0.1, MoneyWeightedReturn.annualizedReturn(result, 730), EPS);
    assertNull(MoneyWeightedReturn.annualizedReturn(result, 359));
  }
}
