package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.WeekendAdjustType;

/**
 * Unit tests for the static date computation and cost evaluation helpers in {@link StandingOrderExecutionService}.
 * No Spring context is needed — all methods under test are pure static functions.
 */
class StandingOrderDateComputeTest {

  // ---- adjustForWeekend ----

  @Nested
  @DisplayName("adjustForWeekend")
  class AdjustForWeekendTests {

    @Test
    @DisplayName("Weekday stays unchanged")
    void weekdayUnchanged() {
      // Wednesday 2026-02-11
      LocalDate wed = LocalDate.of(2026, 2, 11);
      assertEquals(wed, StandingOrderExecutionService.adjustForWeekend(wed, WeekendAdjustType.BEFORE));
      assertEquals(wed, StandingOrderExecutionService.adjustForWeekend(wed, WeekendAdjustType.AFTER));
    }

    @Test
    @DisplayName("Saturday BEFORE -> Friday")
    void saturdayBefore() {
      LocalDate sat = LocalDate.of(2026, 2, 14);
      assertEquals(LocalDate.of(2026, 2, 13),
          StandingOrderExecutionService.adjustForWeekend(sat, WeekendAdjustType.BEFORE));
    }

    @Test
    @DisplayName("Saturday AFTER -> Monday")
    void saturdayAfter() {
      LocalDate sat = LocalDate.of(2026, 2, 14);
      assertEquals(LocalDate.of(2026, 2, 16),
          StandingOrderExecutionService.adjustForWeekend(sat, WeekendAdjustType.AFTER));
    }

    @Test
    @DisplayName("Sunday BEFORE -> Friday")
    void sundayBefore() {
      LocalDate sun = LocalDate.of(2026, 2, 15);
      assertEquals(LocalDate.of(2026, 2, 13),
          StandingOrderExecutionService.adjustForWeekend(sun, WeekendAdjustType.BEFORE));
    }

    @Test
    @DisplayName("Sunday AFTER -> Monday")
    void sundayAfter() {
      LocalDate sun = LocalDate.of(2026, 2, 15);
      assertEquals(LocalDate.of(2026, 2, 16),
          StandingOrderExecutionService.adjustForWeekend(sun, WeekendAdjustType.AFTER));
    }
  }

  // ---- toleranceCandidates ----

  @Nested
  @DisplayName("toleranceCandidates")
  class ToleranceCandidatesTests {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    @DisplayName("Tolerance 0 offers only the execution date")
    void exactDateOnly() {
      assertEquals(List.of(DATE), StandingOrderExecutionService.toleranceCandidates(DATE, (byte) 0));
    }

    @Test
    @DisplayName("Negative tolerance offers the past before the future")
    void negativePrefersPast() {
      assertEquals(List.of(DATE, DATE.minusDays(1), DATE.plusDays(1)),
          StandingOrderExecutionService.toleranceCandidates(DATE, (byte) -1));
    }

    @Test
    @DisplayName("Positive tolerance offers the future before the past")
    void positivePrefersFuture() {
      assertEquals(List.of(DATE, DATE.plusDays(1), DATE.minusDays(1), DATE.plusDays(2), DATE.minusDays(2)),
          StandingOrderExecutionService.toleranceCandidates(DATE, (byte) 2));
    }

    @Test
    @DisplayName("Full window keeps the closest date first at every distance")
    void fullWindowOrderedByDistance() {
      assertEquals(
          List.of(DATE, DATE.minusDays(1), DATE.plusDays(1), DATE.minusDays(2), DATE.plusDays(2), DATE.minusDays(3),
              DATE.plusDays(3)),
          StandingOrderExecutionService.toleranceCandidates(DATE, (byte) -3));
    }
  }

  // ---- computeNextExecutionDate ----

  @Nested
  @DisplayName("computeNextExecutionDate")
  class ComputeNextTests {

    @Test
    @DisplayName("DAYS interval")
    void daysInterval() {
      StandingOrder so = createStandingOrder(RepeatUnit.DAYS, (short) 20, PeriodDayPosition.SPECIFIC_DAY, null, null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 5));
      assertEquals(LocalDate.of(2026, 1, 25), next);
    }

    @Test
    @DisplayName("MONTHS with SPECIFIC_DAY")
    void monthsSpecificDay() {
      StandingOrder so = createStandingOrder(RepeatUnit.MONTHS, (short) 1, PeriodDayPosition.SPECIFIC_DAY, (byte) 15,
          null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 15));
      assertEquals(LocalDate.of(2026, 2, 15), next);
    }

    @Test
    @DisplayName("MONTHS with FIRST_DAY")
    void monthsFirstDay() {
      StandingOrder so = createStandingOrder(RepeatUnit.MONTHS, (short) 1, PeriodDayPosition.FIRST_DAY, null, null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 1));
      assertEquals(LocalDate.of(2026, 2, 1), next);
    }

    @Test
    @DisplayName("MONTHS with LAST_DAY")
    void monthsLastDay() {
      StandingOrder so = createStandingOrder(RepeatUnit.MONTHS, (short) 1, PeriodDayPosition.LAST_DAY, null, null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 31));
      assertEquals(LocalDate.of(2026, 2, 28), next);
    }

    @Test
    @DisplayName("MONTHS with SPECIFIC_DAY=28 in Feb (non-leap year)")
    void monthsDay28InFeb() {
      StandingOrder so = createStandingOrder(RepeatUnit.MONTHS, (short) 1, PeriodDayPosition.SPECIFIC_DAY, (byte) 28,
          null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 28));
      assertEquals(LocalDate.of(2026, 2, 28), next);
    }

    @Test
    @DisplayName("MONTHS quarterly interval")
    void monthsQuarterly() {
      StandingOrder so = createStandingOrder(RepeatUnit.MONTHS, (short) 3, PeriodDayPosition.SPECIFIC_DAY, (byte) 10,
          null);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 1, 10));
      assertEquals(LocalDate.of(2026, 4, 10), next);
    }

    @Test
    @DisplayName("YEARS with SPECIFIC_DAY and month")
    void yearsSpecificDay() {
      StandingOrder so = createStandingOrder(RepeatUnit.YEARS, (short) 1, PeriodDayPosition.SPECIFIC_DAY, (byte) 15,
          (byte) 6);
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 6, 15));
      assertEquals(LocalDate.of(2027, 6, 15), next);
    }

    @Test
    @DisplayName("YEARS with LAST_DAY in Feb (leap year test)")
    void yearsLastDayFeb() {
      StandingOrder so = createStandingOrder(RepeatUnit.YEARS, (short) 1, PeriodDayPosition.LAST_DAY, null, (byte) 2);
      // 2026-02-28 -> next year 2027-02-28
      LocalDate next = StandingOrderExecutionService.computeNextExecutionDate(so, LocalDate.of(2026, 2, 28));
      assertEquals(LocalDate.of(2027, 2, 28), next);
    }
  }

  // ---- evaluateCost ----

  @Nested
  @DisplayName("evaluateCost")
  class EvaluateCostTests {

    @Test
    @DisplayName("Fixed cost takes priority")
    void fixedCost() {
      assertEquals(5.50, StandingOrderExecutionService.evaluateCost(5.50, "a * 0.01", 10, 100, 1000));
    }

    @Test
    @DisplayName("Formula evaluation")
    void formulaCost() {
      // 1% of amount: a * 0.01 where a = 10 * 100 = 1000 -> 10
      assertEquals(10.0, StandingOrderExecutionService.evaluateCost(null, "a * 0.01", 10, 100, 1000));
    }

    @Test
    @DisplayName("Null formula returns 0")
    void nullFormula() {
      assertEquals(0.0, StandingOrderExecutionService.evaluateCost(null, null, 10, 100, 1000));
    }

    @Test
    @DisplayName("Blank formula returns 0")
    void blankFormula() {
      assertEquals(0.0, StandingOrderExecutionService.evaluateCost(null, "  ", 10, 100, 1000));
    }

    @Test
    @DisplayName("Invalid formula returns 0 (error case)")
    void invalidFormula() {
      assertEquals(0.0, StandingOrderExecutionService.evaluateCost(null, "INVALID(((", 10, 100, 1000));
    }

    @Test
    @DisplayName("Formula with units variable")
    void formulaWithUnits() {
      // 2 per unit: u * 2 where u = 10 -> 20
      assertEquals(20.0, StandingOrderExecutionService.evaluateCost(null, "u * 2", 10, 100, 1000));
    }
  }

  // ---- evaluateCashAmountFormula ----

  @Nested
  @DisplayName("evaluateCashAmountFormula")
  class EvaluateCashAmountFormulaTests {

    @Test
    @DisplayName("Exchange rate plus bank spread approximates the real bank charge")
    void rateWithSpread() throws Exception {
      // CHF 3.00 account fee debited from a USD account on 2026-07-10: CHF->USD close 1.2366 plus a 1.9% spread.
      // The bank booked 3.79 USD; a constant spread on the mid-market rate cannot match that exactly, because the
      // bank's own spread varies. One cent off is the expected residual, not a defect.
      assertEquals(3.78, Math.round(StandingOrderExecutionService.evaluateCashAmountFormula("a * r * 1.019", 3.0,
          1.2366) * 100.0) / 100.0);
    }

    @Test
    @DisplayName("Formula without exchange rate uses the base amount only")
    void withoutRate() throws Exception {
      assertEquals(3.15, StandingOrderExecutionService.evaluateCashAmountFormula("a * 1.05", 3.0, null));
    }

    @Test
    @DisplayName("Invalid formula throws instead of returning 0")
    void invalidFormulaThrows() {
      assertThrows(Exception.class,
          () -> StandingOrderExecutionService.evaluateCashAmountFormula("INVALID(((", 3.0, 1.2366));
    }

    @Test
    @DisplayName("Formula using r without an exchange rate throws")
    void unboundRateThrows() {
      assertThrows(Exception.class, () -> StandingOrderExecutionService.evaluateCashAmountFormula("a * r", 3.0, null));
    }
  }

  // ---- Helper to build a minimal StandingOrder for date computation tests ----

  private static StandingOrder createStandingOrder(RepeatUnit repeatUnit, short interval,
      PeriodDayPosition dayPosition, Byte dayOfExecution, Byte monthOfExecution) {
    StandingOrderCashaccount so = new StandingOrderCashaccount();
    so.setRepeatUnit(repeatUnit);
    so.setRepeatInterval(interval);
    so.setPeriodDayPosition(dayPosition);
    so.setDayOfExecution(dayOfExecution);
    so.setMonthOfExecution(monthOfExecution);
    return so;
  }
}
