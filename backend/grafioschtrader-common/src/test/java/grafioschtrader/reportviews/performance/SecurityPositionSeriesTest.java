package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.reportviews.performance.SecurityPositionSeries.ConsumedLot;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.DailyValue;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.Event;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.Input;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.MarginPosition;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.Phase;
import grafioschtrader.types.SecurityPositionSeriesRemark;
import grafioschtrader.types.TransactionType;

/**
 * Fixes the series of the security position chart against results calculated by hand. Day {@code n} is {@link #DAY1}
 * plus n - 1 days; every case writes out its events and daily values, so the expected numbers can be followed without
 * reading the implementation. Percentages are rounded to two decimals, amounts to two.
 */
class SecurityPositionSeriesTest {

  private static final double EPS = 1e-9;
  private static final LocalDate DAY1 = LocalDate.of(2025, 3, 3);
  private static final LocalDate TODAY = day(30);

  private static LocalDate day(int n) {
    return DAY1.plusDays(n - 1L);
  }

  private static Event buy(int n, int account, int idTransaction, double units, double unitsAfter, double costBefore,
      double cost, Double rate) {
    return new Event(day(n), account, TransactionType.ACCUMULATE, idTransaction, null, units, unitsAfter, costBefore,
        costBefore + cost, cost, 0, 0, 0, null, rate, null, false, null);
  }

  private static Event sell(int n, int account, double units, double unitsAfter, double costBefore, double removed,
      double gainLoss, List<ConsumedLot> lots, Double rate) {
    return new Event(day(n), account, TransactionType.REDUCE, 900 + n, null, units, unitsAfter, costBefore,
        costBefore - removed, -(gainLoss + removed), 0, gainLoss, removed, lots, rate, null, false, null);
  }

  private static Event dividend(int n, int account, double unitsAfter, double costBase, double amount) {
    return new Event(day(n), account, TransactionType.DIVIDEND, 800 + n, null, unitsAfter, unitsAfter, costBase,
        costBase, -amount, amount, 0, 0, null, 1.0, null, false, null);
  }

  private static DailyValue held(int n, double units, double close, Double rate) {
    return new DailyValue(day(n), units, units * close, close, rate, false, false);
  }

  private static Set<LocalDate> sessions(int from, int to) {
    Set<LocalDate> days = new HashSet<>();
    for (int n = from; n <= to; n++) {
      days.add(day(n));
    }
    return days;
  }

  private static SecurityPositionSeriesResult calc(String securityCurrency, String mainCurrency, List<Event> events,
      LocalDate phaseFrom, List<DailyValue> values, Set<LocalDate> sessions) {
    return calc(securityCurrency, mainCurrency, false, events, phaseFrom, values, sessions);
  }

  private static SecurityPositionSeriesResult calc(String securityCurrency, String mainCurrency, boolean margin,
      List<Event> events, LocalDate phaseFrom, List<DailyValue> values, Set<LocalDate> sessions) {
    List<Phase> phases = SecurityPositionSeries.phases(events);
    Phase phase = SecurityPositionSeries.choose(phases, phaseFrom);
    return SecurityPositionSeries.calculate(new Input(1, "Security", securityCurrency, mainCurrency, "Scope", margin,
        2, 2, events, phases, phase, values, sessions, TODAY));
  }

  /** An event of a margin instrument with the open positions of the account after it. */
  private static Event margin(int n, TransactionType type, int idTransaction, boolean opening, double gainLoss,
      double financeCost, MarginPosition... positions) {
    double units = Arrays.stream(positions).mapToDouble(MarginPosition::openUnits).sum();
    return new Event(day(n), 1, type, idTransaction, null, 1.0, units, 0, 0, 0, financeCost, gainLoss, 0, null, 1.0,
        List.of(positions), opening, null);
  }

  private static DailyValue close(int n, double close) {
    return new DailyValue(day(n), 0, 0, close, 1.0, false, false);
  }

  private static void assertSeries(List<Double> actual, Double... expected) {
    assertEquals(expected.length, actual.size());
    for (int i = 0; i < expected.length; i++) {
      if (expected[i] == null) {
        assertNull(actual.get(i), "index " + i);
      } else {
        assertEquals(expected[i], actual.get(i), EPS, "index " + i);
      }
    }
  }

  /** The core phase with average cost: purchase, decline, purchase below the high, dividend, two sales. */
  private static List<Event> coreEventsAverage() {
    return List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), buy(4, 1, 104, 10, 20, 1000, 880, 1.0),
        dividend(5, 1, 20, 1880, 40), sell(6, 1, 10, 10, 1880, 940, 50, null, 1.0),
        sell(7, 1, 10, 0, 940, 940, 10, null, 1.0));
  }

  private static List<DailyValue> coreValues() {
    return List.of(held(1, 10, 100, 1.0), held(2, 10, 110, 1.0), held(3, 10, 88, 1.0), held(4, 20, 88, 1.0),
        held(5, 20, 90, 1.0), held(6, 10, 99, 1.0));
  }

  @Test
  @DisplayName("Core phase with average cost: every column by hand")
  void corePhaseAverageCost() {
    SecurityPositionSeriesResult r = calc("CHF", "CHF", coreEventsAverage(), null, coreValues(), sessions(1, 7));
    assertEquals(7, r.dates().size());
    assertEquals(day(1), r.phaseFrom());
    assertEquals(day(7), r.phaseTo());
    assertNull(r.main());
    var s = r.security();
    assertSeries(s.value(), 1000.0, 1100.0, 880.0, 1760.0, 1800.0, 990.0, 0.0);
    assertSeries(s.invested(), 1000.0, 1000.0, 1000.0, 1880.0, 1880.0, 940.0, 0.0);
    assertSeries(s.purchases(), 1000.0, 1000.0, 1000.0, 1880.0, 1880.0, 1880.0, 1880.0);
    assertSeries(s.income(), 0.0, 0.0, 0.0, 0.0, 40.0, 40.0, 40.0);
    assertSeries(s.realized(), 0.0, 0.0, 0.0, 0.0, 0.0, 50.0, 60.0);
    assertSeries(s.openResult(), 0.0, 100.0, -120.0, -120.0, -80.0, 50.0, 0.0);
    assertSeries(s.totalResult(), 0.0, 100.0, -120.0, -120.0, -40.0, 140.0, 100.0);
    assertSeries(s.openPerformance(), 0.0, 10.0, -12.0, -6.38, -4.26, 5.32, null);
    assertSeries(s.totalPerformance(), 0.0, 10.0, -12.0, -6.38, -2.13, 7.45, 5.32);
    assertSeries(s.drawdown(), 0.0, 0.0, -20.0, -11.11, -7.07, 0.0, -4.04);
    assertSeries(s.priceDrawdown(), 0.0, 0.0, -20.0, -20.0, -18.18, -10.0, null);
    assertEquals(-20.0, s.maxDrawdown().depth(), EPS);
    assertEquals(day(2), s.maxDrawdown().peakDate());
    assertEquals(day(6), s.maxDrawdown().recoveryDate());
    assertEquals(-4.04, s.currentDrawdown(), EPS);
    assertEquals(1, r.phases().size());
    assertEquals(100.0, r.phases().getFirst().totalResultSC(), EPS);
  }

  @Test
  @DisplayName("FIFO changes invested and realized but not the total performance")
  void corePhaseFifo() {
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), buy(4, 1, 104, 10, 20, 1000, 880, 1.0),
        dividend(5, 1, 20, 1880, 40),
        sell(6, 1, 10, 10, 1880, 1000, -10, List.of(new ConsumedLot(101, 1000)), 1.0),
        sell(7, 1, 10, 0, 880, 880, 70, List.of(new ConsumedLot(104, 880)), 1.0));
    var s = calc("CHF", "CHF", events, null, coreValues(), sessions(1, 7)).security();
    assertSeries(s.invested(), 1000.0, 1000.0, 1000.0, 1880.0, 1880.0, 880.0, 0.0);
    assertSeries(s.realized(), 0.0, 0.0, 0.0, 0.0, 0.0, -10.0, 60.0);
    assertEquals(12.5, s.openPerformance().get(5), EPS);
    assertSeries(s.totalPerformance(), 0.0, 10.0, -12.0, -6.38, -2.13, 7.45, 5.32);
  }

  @Test
  @DisplayName("Main currency: cost at the purchase rate, realized result includes the currency result on the cost")
  void foreignCurrencyRealized() {
    List<Event> events = List.of(buy(1, 1, 101, 1, 1, 0, 100, 0.9), sell(2, 1, 1, 0, 100, 100, 10, null, 1.0));
    SecurityPositionSeriesResult r = calc("USD", "CHF", events, null, List.of(held(1, 1, 100, 0.9)),
        sessions(1, 2));
    assertSeries(r.main().invested(), 90.0, 0.0);
    assertSeries(r.main().realized(), 0.0, 20.0);
    assertSeries(r.security().realized(), 0.0, 10.0);
    assertSeries(r.main().value(), 90.0, 0.0);
  }

  @Test
  @DisplayName("A missing rate of a purchase leaves the main currency cost series empty, the SC series complete")
  void missingRate() {
    List<Event> events = List.of(buy(1, 1, 101, 1, 1, 0, 100, null));
    SecurityPositionSeriesResult r = calc("USD", "EUR", events, null,
        List.of(held(1, 1, 100, null), held(2, 1, 110, null)), sessions(1, 2));
    assertSeries(r.security().value(), 100.0, 110.0);
    assertSeries(r.main().invested(), null, null);
    assertSeries(r.main().value(), null, null);
    assertTrue(r.remarks().contains(SecurityPositionSeriesRemark.MISSING_FX));
  }

  @Test
  @DisplayName("Two phases: the newest by default, the older on request with the dividend paid after its end")
  void twoPhases() {
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), sell(2, 1, 10, 0, 1000, 1000, 100, null, 1.0),
        dividend(3, 1, 0, 0, 30), buy(5, 1, 105, 5, 5, 0, 600, 1.0));
    List<Phase> phases = SecurityPositionSeries.phases(events);
    assertEquals(2, phases.size());
    assertEquals(day(5), phases.get(0).from());
    assertTrue(phases.get(0).open());
    assertEquals(day(1), phases.get(1).from());
    assertEquals(day(2), phases.get(1).to());
    assertEquals(130.0, phases.get(1).totalResultSC(), EPS);
    SecurityPositionSeriesResult newest = calc("CHF", "CHF", events, null, List.of(held(5, 5, 120, 1.0)),
        sessions(1, 5));
    assertEquals(day(5), newest.phaseFrom());
    SecurityPositionSeriesResult older = calc("CHF", "CHF", events, day(1), List.of(held(1, 10, 100, 1.0)),
        sessions(1, 5));
    assertSeries(older.security().income(), 0.0, 30.0);
    assertSeries(older.security().realized(), 0.0, 100.0);
  }

  @Test
  @DisplayName("A dividend with an ex-date: the drawdown takes the outflow on the ex-date, income stays on payment")
  void dividendOnExDate() {
    // Day 2 is the ex-date: the close falls from 100 to 98 by the dividend of 2 per unit, paid on day 3
    Event dividend = new Event(day(3), 1, TransactionType.DIVIDEND, 803, null, 10.0, 10, 1000, 1000, -20, 20, 0, 0,
        null, 1.0, null, false, day(2));
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), dividend);
    var s = calc("CHF", "CHF", events, null,
        List.of(held(1, 10, 100, 1.0), held(2, 10, 98, 1.0), held(3, 10, 98, 1.0)), sessions(1, 3)).security();
    assertSeries(s.drawdown(), 0.0, 0.0, 0.0);
    assertSeries(s.income(), 0.0, 0.0, 20.0);
  }

  @Test
  @DisplayName("A sale and a repurchase on the same day keep one phase")
  void sameDaySaleAndRepurchase() {
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), sell(2, 1, 10, 0, 1000, 1000, 0, null, 1.0),
        buy(2, 1, 102, 10, 10, 0, 1000, 1.0));
    assertEquals(1, SecurityPositionSeries.phases(events).size());
  }

  @Test
  @DisplayName("A transfer within the scope nets its flows and is marked; cost series follow the booking")
  void internalTransfer() {
    Event out = new Event(day(3), 1, TransactionType.REDUCE, 301, 7, 10.0, 0, 1000, 0, -1200, 0, 200, 1000, null, 1.0,
        null, false, null);
    Event in = new Event(day(3), 2, TransactionType.ACCUMULATE, 302, 7, 10.0, 10, 0, 1200, 1200, 0, 0, 0, null, 1.0,
        null, false, null);
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0), out, in);
    SecurityPositionSeriesResult r = calc("CHF", "CHF", events, null,
        List.of(held(1, 10, 100, 1.0), held(2, 10, 110, 1.0), held(3, 10, 120, 1.0)), sessions(1, 3));
    assertEquals(1, r.phases().size());
    assertTrue(r.remarks().contains(SecurityPositionSeriesRemark.INTERNAL_TRANSFER));
    assertSeries(r.security().drawdown(), 0.0, 0.0, 0.0);
    assertSeries(r.security().invested(), 1000.0, 1000.0, 1200.0);
    assertSeries(r.security().purchases(), 1000.0, 1000.0, 2200.0);
    assertSeries(r.security().realized(), 0.0, 0.0, 200.0);
  }

  @Test
  @DisplayName("Margin: long and short open in one account, entry value as invested, value minus flows is the result")
  void marginLongAndShort() {
    // Value per point 10. Day 1: open long 2 @ 100 with cost 5. Day 2: finance cost 3. Day 3: close 1 @ 110 with
    // cost 5 (result 1 * 110 * 10 - 5 - 1000 = 95) and open short 1 @ 112 without cost.
    MarginPosition longTwo = new MarginPosition(101, false, 2, 2000, 10);
    MarginPosition longOne = new MarginPosition(101, false, 1, 1000, 10);
    MarginPosition shortOne = new MarginPosition(103, true, 1, 1120, 10);
    List<Event> events = List.of(margin(1, TransactionType.ACCUMULATE, 101, true, -5, 0, longTwo),
        margin(2, TransactionType.FINANCE_COST, 102, false, 0, -3, longTwo),
        margin(3, TransactionType.REDUCE, 104, false, 95, 0, longOne),
        margin(3, TransactionType.REDUCE, 103, true, 0, 0, longOne, shortOne));
    var s = calc("CHF", "CHF", true, events, null,
        List.of(close(1, 100), close(2, 100), close(3, 110), close(4, 105)), sessions(1, 4)).security();
    // Day 3 at 110: long 1100 - 1000 = 100, short 1120 - 1100 = 20; day 4 at 105: long 50, short 70
    assertSeries(s.invested(), 2000.0, 2000.0, 2120.0, 2120.0);
    assertSeries(s.value(), 2000.0, 2000.0, 2240.0, 2240.0);
    assertSeries(s.purchases(), 2000.0, 2000.0, 3120.0, 3120.0);
    assertSeries(s.realized(), -5.0, -5.0, 90.0, 90.0);
    assertSeries(s.income(), 0.0, -3.0, -3.0, -3.0);
    assertSeries(s.openResult(), 0.0, 0.0, 120.0, 120.0);
    // Flows: +2005, +3, -1095 and +1120, so value - flows = 2240 - 2033 = 207 = realized + income + open result
    assertSeries(s.totalResult(), -5.0, -8.0, 207.0, 207.0);
  }

  @Test
  @DisplayName("A holiday leaves no point and no remark, a missing close on a session day sets NOT_VALUED_DAYS")
  void notValuedDays() {
    List<Event> events = List.of(buy(1, 1, 101, 10, 10, 0, 1000, 1.0));
    DailyValue notValued = new DailyValue(day(3), 10, 0, null, 1.0, false, true);
    List<DailyValue> values = new ArrayList<>(Arrays.asList(held(1, 10, 100, 1.0), held(2, 10, 100, 1.0), notValued,
        held(5, 10, 100, 1.0)));
    Set<LocalDate> sessions = sessions(1, 5);
    sessions.remove(day(4));
    SecurityPositionSeriesResult r = calc("CHF", "CHF", events, null, values, sessions);
    assertEquals(List.of(day(1).toString(), day(2).toString(), day(5).toString()), r.dates());
    assertTrue(r.remarks().contains(SecurityPositionSeriesRemark.NOT_VALUED_DAYS));
    sessions.remove(day(3));
    SecurityPositionSeriesResult holiday = calc("CHF", "CHF", events, null, values, sessions);
    assertFalse(holiday.remarks().contains(SecurityPositionSeriesRemark.NOT_VALUED_DAYS));
  }
}
