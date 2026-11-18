package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.reportviews.performance.SecurityPeriodSeries.DailyValue;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Flow;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Input;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Result;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Span;
import grafioschtrader.types.SecurityPeriodRemark;

/**
 * Fixes the figures of one security against results calculated by hand. Every case writes out its values, flows and
 * rates, so the expected numbers can be followed without reading the implementation.
 */
class SecurityPeriodSeriesTest {

  private static final double EPS = 1e-12;
  /** A Monday; the week runs to {@link #FRI}. */
  private static final LocalDate MON = LocalDate.of(2025, 3, 3);
  private static final LocalDate TUE = MON.plusDays(1);
  private static final LocalDate WED = MON.plusDays(2);
  private static final LocalDate THU = MON.plusDays(3);
  private static final LocalDate FRI = MON.plusDays(4);
  private static final Function<LocalDate, Double> MAIN_CURRENCY = d -> 1.0;

  private static List<LocalDate> weekdays(LocalDate from, LocalDate to) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        days.add(d);
      }
    }
    return days;
  }

  /** A valued day in the main currency. */
  private static DailyValue held(LocalDate date, double units, double value) {
    return new DailyValue(date, units, value, value, false, false, false);
  }

  private static Flow flow(LocalDate date, double amount) {
    return new Flow(date, amount, null);
  }

  private static Result calc(LocalDate from, LocalDate to, boolean margin, List<DailyValue> values, List<Span> spans,
      List<Flow> flows, Function<LocalDate, Double> rates) {
    return SecurityPeriodSeries.calculate(new Input(from, to, weekdays(from, to), margin, values, spans, flows, rates));
  }

  @Test
  @DisplayName("Same-day purchase 100 and sale 110 without cost: gain 10, return 10 %, 0 days held")
  void sameDayRoundTrip() {
    Result r = calc(MON, FRI, false, List.of(), List.of(), List.of(flow(WED, 100), flow(WED, -110)), MAIN_CURRENCY);
    assertEquals(10, r.gainPeriod(), EPS);
    assertEquals(10, r.gainPeriodMC(), EPS);
    assertEquals(0.1, r.twr(), EPS);
    assertEquals(0, r.holdingDays());
    assertEquals(100, r.inflowMC(), EPS);
    assertEquals(110, r.outflowMC(), EPS);
    assertNull(r.remark());
  }

  @Test
  @DisplayName("Purchase 100 plus cost 2, closes at 100, sale 110 minus cost 1: gain 7, return 109 / 102 - 1")
  void purchaseAndSaleWithCost() {
    List<DailyValue> values = List.of(held(TUE, 1, 100), held(WED, 1, 100), held(THU, 1, 100));
    Result r = calc(MON, FRI, false, values, List.of(new Span(TUE, THU)), List.of(flow(TUE, 102), flow(FRI, -109)),
        MAIN_CURRENCY);
    assertEquals(7, r.gainPeriodMC(), EPS);
    assertEquals(109.0 / 102.0 - 1, r.twr(), EPS);
    assertEquals(3, r.holdingDays());
    assertEquals(0, r.unitsStart(), EPS);
    assertEquals(0, r.unitsEnd(), EPS);
  }

  @Test
  @DisplayName("1 unit at constant 100 USD, USD/CHF 0.90 to 0.95: gain 0 USD and 5 CHF, return 0 % and 5.5556 %")
  void currencyMoveOnly() {
    List<DailyValue> values = List.of(new DailyValue(MON, 1, 100, 90, false, false, false),
        new DailyValue(TUE, 1, 100, 95, false, false, false));
    Result r = calc(MON, TUE, false, values, List.of(new Span(LocalDate.of(2025, 1, 2), null)), List.of(),
        Map.of(MON, 0.90, TUE, 0.95)::get);
    assertEquals(0, r.gainPeriod(), EPS);
    assertEquals(5, r.gainPeriodMC(), EPS);
    assertEquals(0, r.twrSecurityCurrency(), EPS);
    assertEquals(0.95 / 0.90 - 1, r.twr(), EPS);
    assertEquals(1, r.holdingDays());
  }

  @Test
  @DisplayName("Nothing held, dividend 10: gain 10, no return, remark NO_CAPITAL_BASE")
  void dividendWithoutHolding() {
    Result r = calc(MON, FRI, false, List.of(), List.of(), List.of(flow(WED, -10)), MAIN_CURRENCY);
    assertEquals(10, r.gainPeriodMC(), EPS);
    assertNull(r.twr());
    assertNull(r.twrSecurityCurrency());
    assertEquals(SecurityPeriodRemark.NO_CAPITAL_BASE, r.remark());
  }

  @Test
  @DisplayName("Transfer of the whole position between two accounts of the scope leaves no flow")
  void transferInsideScope() {
    List<DailyValue> values = List.of(held(MON, 10, 1000), held(TUE, 10, 1000), held(WED, 10, 1100),
        held(THU, 10, 1100), held(FRI, 10, 1100));
    List<Flow> flows = List.of(new Flow(WED, -1100, 7), new Flow(WED, 1100, 7));
    Result r = calc(MON, FRI, false, values, List.of(new Span(MON, null)), flows, MAIN_CURRENCY);
    assertEquals(0.1, r.twr(), EPS);
    assertEquals(100, r.gainPeriodMC(), EPS);
    assertEquals(0, r.inflowMC(), EPS);
    assertEquals(0, r.outflowMC(), EPS);
  }

  @Test
  @DisplayName("Transfer legs 480 out and 490 in are netted before the split: factor V_new / (V_prev + 10)")
  void transferLegsNettedBeforeSplit() {
    List<DailyValue> values = List.of(held(MON, 10, 1000), held(TUE, 10, 1030));
    List<Flow> flows = List.of(new Flow(TUE, -480, 3), new Flow(TUE, 490, 3));
    Result r = calc(MON, TUE, false, values, List.of(new Span(MON, null)), flows, MAIN_CURRENCY);
    assertEquals(1030.0 / 1010.0 - 1, r.twr(), EPS);
    assertEquals(10, r.inflowMC(), EPS);
    assertEquals(0, r.outflowMC(), EPS);
  }

  @Test
  @DisplayName("Flows without a transfer id are never netted, even on the same day")
  void ordinaryFlowsAreNotNetted() {
    List<DailyValue> values = List.of(held(MON, 10, 1000), held(TUE, 10, 1030));
    List<Flow> flows = List.of(flow(TUE, -480), flow(TUE, 490));
    Result r = calc(MON, TUE, false, values, List.of(new Span(MON, null)), flows, MAIN_CURRENCY);
    assertEquals((1030.0 + 480) / (1000.0 + 490) - 1, r.twr(), EPS);
    assertEquals(490, r.inflowMC(), EPS);
    assertEquals(480, r.outflowMC(), EPS);
  }

  @Test
  @DisplayName("1 unit held from 100 to 200, 1 unit bought at 200 on the last day: return 33.3 %, gain 100")
  void largePurchaseAtTheCloseDilutesTheDay() {
    List<DailyValue> values = List.of(held(MON, 1, 100), held(TUE, 2, 400));
    Result r = calc(MON, TUE, false, values, List.of(new Span(MON, null)), List.of(flow(TUE, 200)), MAIN_CURRENCY);
    assertEquals(400.0 / 300.0 - 1, r.twr(), EPS);
    assertEquals(100, r.gainPeriodMC(), EPS);
    assertEquals(1, r.unitsStart(), EPS);
    assertEquals(2, r.unitsEnd(), EPS);
  }

  @Test
  @DisplayName("Holding days: purchase on dateTo 1, Friday held and sold Monday 2, two accounts counted once")
  void holdingDays() {
    assertEquals(1, SecurityPeriodSeries.holdingDays(List.of(new Span(FRI, null)), MON, FRI));
    LocalDate nextMon = MON.plusDays(7);
    assertEquals(2, SecurityPeriodSeries.holdingDays(List.of(new Span(MON, FRI.plusDays(2))), FRI, nextMon));
    assertEquals(4, SecurityPeriodSeries.holdingDays(List.of(new Span(MON, null), new Span(TUE, WED)), MON, FRI));
    assertEquals(4, SecurityPeriodSeries.holdingDays(List.of(new Span(MON, TUE), new Span(WED, null)), MON, FRI));
    assertEquals(0, SecurityPeriodSeries.holdingDays(List.of(), MON, FRI));
  }

  @Test
  @DisplayName("A missing rate before the period does not affect it; a missing rate inside it blanks the MC figures")
  void missingRateOnlyCountsInsideThePeriod() {
    List<DailyValue> values = List.of(held(MON, 1, 100), held(TUE, 2, 200), held(WED, 2, 200), held(THU, 2, 200),
        held(FRI, 2, 220));
    Function<LocalDate, Double> rates = d -> d.equals(TUE) ? null : 1.0;
    List<Flow> flows = List.of(flow(TUE, 100));
    Result after = calc(TUE, FRI, false, values, List.of(new Span(MON, null)), flows, rates);
    assertNull(after.remark());
    assertEquals(0.1, after.twr(), EPS);
    Result including = calc(MON, FRI, false, values, List.of(new Span(MON, null)), flows, rates);
    assertEquals(SecurityPeriodRemark.MISSING_EXCHANGE_RATE, including.remark());
    assertNull(including.twr());
    assertNull(including.gainPeriodMC());
    assertNull(including.inflowMC());
    assertEquals(20, including.gainPeriod(), EPS);
    assertEquals(0.1, including.twrSecurityCurrency(), EPS);
  }

  @Test
  @DisplayName("A flow on a day without a price joins the next grid day; the return spans the gap")
  void flowOnDayWithoutPrice() {
    List<DailyValue> values = List.of(held(MON, 10, 1000), new DailyValue(TUE, 11, 0, 0, false, true, false),
        held(WED, 11, 1210));
    Result r = calc(MON, WED, false, values, List.of(new Span(MON, null)), List.of(flow(TUE, 105)), MAIN_CURRENCY);
    assertEquals(1210.0 / 1105.0 - 1, r.twr(), EPS);
    assertEquals(105, r.gainPeriodMC(), EPS);
  }

  @Test
  @DisplayName("A sale for nothing makes the whole period -100 %, also after a repurchase")
  void totalLossIsNotUndoneByRepurchase() {
    List<DailyValue> values = List.of(held(MON, 1, 100), held(WED, 1, 50), held(THU, 1, 60));
    List<Flow> flows = List.of(flow(TUE, 0), flow(WED, 50));
    Result r = calc(MON, THU, false, values, List.of(new Span(MON, MON), new Span(WED, null)), flows, MAIN_CURRENCY);
    assertEquals(-1.0, r.twr(), EPS);
    assertEquals(-90, r.gainPeriodMC(), EPS);
  }

  @Test
  @DisplayName("Margin position partly closed with finance cost: gain = change of open result plus settled amounts")
  void marginPosition() {
    List<DailyValue> values = List.of(held(MON, 0, 50), held(TUE, 0, 50), held(WED, 0, 30), held(THU, 0, 30),
        held(FRI, 0, 30));
    // Partial close settles a gain of 40, the finance cost of 5 is a loss paid into the position
    List<Flow> flows = List.of(flow(WED, -40), flow(THU, 5));
    Result r = calc(MON, FRI, true, values, List.of(new Span(MON, null)), flows, MAIN_CURRENCY);
    assertEquals(15, r.gainPeriodMC(), EPS);
    assertNull(r.twr());
    assertNull(r.twrSecurityCurrency());
    assertNull(r.unitsStart());
    assertEquals(SecurityPeriodRemark.MARGIN_GAIN_ONLY, r.remark());
  }

  @Test
  @DisplayName("Held on dateTo without a price: no value figures, remark MISSING_VALUATION")
  void missingValuationAtTheEnd() {
    List<DailyValue> values = List.of(held(MON, 1, 100), new DailyValue(TUE, 1, 0, 0, false, true, false));
    Result r = calc(MON, TUE, false, values, List.of(new Span(MON, null)), List.of(), MAIN_CURRENCY);
    assertEquals(SecurityPeriodRemark.MISSING_VALUATION, r.remark());
    assertNull(r.valueEndMC());
    assertNull(r.gainPeriodMC());
    assertNull(r.twr());
    assertEquals(1, r.holdingDays());
  }

  @Test
  @DisplayName("Negative units on a valuation day: no return, the gain is still formed")
  void negativeUnits() {
    List<DailyValue> values = List.of(held(MON, 1, 100), new DailyValue(TUE, -1, -110, -110, true, false, false));
    Result r = calc(MON, TUE, false, values, List.of(new Span(MON, null)), List.of(), MAIN_CURRENCY);
    assertEquals(SecurityPeriodRemark.NEGATIVE_UNITS, r.remark());
    assertNull(r.twr());
    assertEquals(-210, r.gainPeriodMC(), EPS);
  }
}
