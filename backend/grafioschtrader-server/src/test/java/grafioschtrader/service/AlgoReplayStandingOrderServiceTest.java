package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.service.AlgoReplayInputs.CashStandingOrder;
import grafioschtrader.service.AlgoReplayInputs.SecurityStandingOrder;
import grafioschtrader.service.AlgoReplayInputs.Snapshot;
import grafioschtrader.service.AlgoReplayStandingOrderService.ScheduledSecurityOrder;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

class AlgoReplayStandingOrderServiceTest {

  private final AlgoReplayStandingOrderService service = new AlgoReplayStandingOrderService(null, null, null, null);

  @Test
  @DisplayName("Monthly deposits are adjusted before the strategy sees the day's cash")
  void monthlyScheduleUsesEffectiveDates() {
    LocalDate opening = LocalDate.of(2026, 1, 1);
    var schedule = service.schedule(
        snapshot(order(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 3, 3), WeekendAdjustType.BEFORE)), opening,
        LocalDate.of(2026, 3, 31));

    assertThat(schedule.keySet()).containsExactly(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 3),
        LocalDate.of(2026, 3, 3));
  }

  @Test
  @DisplayName("Occurrences adjusted onto or beyond the replay boundary are omitted")
  void scheduleKeepsTheOpeningStateAndEndHorizonClosed() {
    LocalDate opening = LocalDate.of(2026, 1, 2);
    var before = service.schedule(
        snapshot(order(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 3), WeekendAdjustType.BEFORE)), opening,
        LocalDate.of(2026, 1, 31));
    var after = service.schedule(
        snapshot(order(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), WeekendAdjustType.AFTER)), opening,
        LocalDate.of(2026, 1, 31));

    assertThat(before).isEmpty();
    assertThat(after).isEmpty();
  }

  @Test
  @DisplayName("Recorded version-one replay inputs remain readable and contain no standing orders")
  void versionOneInputsRemainCompatible() {
    Snapshot inputs = AlgoReplayInputs.read("""
        {"version":1,"applyTaxModels":false,"generateBondCoupons":false,"dividendDelay":0,
         "countryModels":{},"accounts":{},"instruments":{}}
        """);

    assertThat(service.schedule(inputs, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).isEmpty();
  }

  /** Monday 5 January 2026, a holiday of the exchange in these tests. */
  private static final LocalDate HOLIDAY = LocalDate.of(2026, 1, 5);
  private static final BiPredicate<SecurityStandingOrder, LocalDate> SESSIONS = (_, date) -> !date.equals(HOLIDAY)
      && date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
  private static final BiPredicate<SecurityStandingOrder, LocalDate> ALWAYS_TRADABLE = (_, _) -> true;

  @Test
  @DisplayName("A security occurrence on an exchange holiday moves to the session in its weekend adjustment direction")
  void securityOccurrenceMovesOffTheExchangeHoliday() {
    LocalDate opening = LocalDate.of(2025, 12, 31);
    var before = service.scheduleSecurities(securitySnapshot(securityOrder(HOLIDAY, HOLIDAY, WeekendAdjustType.BEFORE)),
        opening, LocalDate.of(2026, 1, 31), SESSIONS, ALWAYS_TRADABLE);
    var after = service.scheduleSecurities(securitySnapshot(securityOrder(HOLIDAY, HOLIDAY, WeekendAdjustType.AFTER)),
        opening, LocalDate.of(2026, 1, 31), SESSIONS, ALWAYS_TRADABLE);

    // Back from Monday over the weekend to Friday, forward to Tuesday.
    assertThat(before.keySet()).containsExactly(LocalDate.of(2026, 1, 2));
    assertThat(after.keySet()).containsExactly(LocalDate.of(2026, 1, 6));
    assertThat(after.get(LocalDate.of(2026, 1, 6))).extracting(ScheduledSecurityOrder::unavailable).containsOnlyNulls();
  }

  @Test
  @DisplayName("A security occurrence moved onto or before the opening date is omitted")
  void securityOccurrenceBeforeOpeningIsOmitted() {
    var schedule = service.scheduleSecurities(
        securitySnapshot(securityOrder(HOLIDAY, HOLIDAY, WeekendAdjustType.BEFORE)), LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 1, 31), SESSIONS, ALWAYS_TRADABLE);

    assertThat(schedule).isEmpty();
  }

  @Test
  @DisplayName("A security occurrence whose instrument is stopped is kept on its session and marked not tradable")
  void stoppedInstrumentYieldsAnUnavailableOccurrence() {
    LocalDate stop = LocalDate.of(2026, 2, 1);
    var schedule = service.scheduleSecurities(
        securitySnapshot(securityOrder(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 3, 3), WeekendAdjustType.AFTER)),
        LocalDate.of(2025, 12, 31), LocalDate.of(2026, 3, 31), SESSIONS, (_, date) -> date.isBefore(stop));

    assertThat(schedule.keySet()).containsExactly(HOLIDAY.plusDays(1), LocalDate.of(2026, 2, 3),
        LocalDate.of(2026, 3, 3));
    assertThat(schedule.get(HOLIDAY.plusDays(1)).getFirst().unavailable()).isNull();
    assertThat(schedule.get(LocalDate.of(2026, 2, 3)).getFirst().unavailable())
        .isEqualTo(AlgoReplayStandingOrderService.NOT_TRADABLE);
  }

  @Test
  @DisplayName("Without any session within ten steps the occurrence stays on its weekend-adjusted date")
  void noSessionFound() {
    var schedule = service.scheduleSecurities(
        securitySnapshot(securityOrder(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 7), WeekendAdjustType.AFTER)),
        LocalDate.of(2025, 12, 31), LocalDate.of(2026, 3, 31), (_, _) -> false, ALWAYS_TRADABLE);

    assertThat(schedule.get(LocalDate.of(2026, 1, 7))).extracting(ScheduledSecurityOrder::unavailable)
        .containsExactly(AlgoReplayStandingOrderService.NO_TRADING_DAY);
  }

  @Test
  @DisplayName("The execution price comes only from the day itself or the tolerance days before it")
  void priceOnlyFromThePast() {
    LocalDate date = LocalDate.of(2026, 1, 9);
    AlgoReplayMarketData market = mock(AlgoReplayMarketData.class);
    when(market.history(7, date)).thenReturn(List.of(new Historyquote(1, date.minusDays(3), 50)));

    assertThat(AlgoReplayStandingOrderService.closeWithinPastTolerance(market, 7, date, (byte) -2)).isNull();
    assertThat(AlgoReplayStandingOrderService.closeWithinPastTolerance(market, 7, date, (byte) -3)).isEqualTo(50);
    // A positive tolerance widens the window by as many days, but still only into the past.
    assertThat(AlgoReplayStandingOrderService.closeWithinPastTolerance(market, 7, date, (byte) 3)).isEqualTo(50);
  }

  @Test
  @DisplayName("Inputs recorded before version six contain no security standing orders")
  void olderInputsHaveNoSecurityStandingOrders() {
    Snapshot inputs = AlgoReplayInputs.read("""
        {"version":5,"applyTaxModels":false,"generateBondCoupons":false,"dividendDelay":0,
         "countryModels":{},"accounts":{},"instruments":{}}
        """);

    assertThat(inputs.securityStandingOrders()).isNull();
    assertThat(service.scheduleSecurities(inputs, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), SESSIONS,
        ALWAYS_TRADABLE)).isEmpty();
  }

  @Test
  @DisplayName("Captured security standing orders survive the round trip through the stored inputs")
  void securityStandingOrdersRoundTrip() {
    Snapshot restored = AlgoReplayInputs.read(AlgoReplayInputs
        .write(securitySnapshot(securityOrder(HOLIDAY, HOLIDAY.plusMonths(6), WeekendAdjustType.AFTER))));

    assertThat(restored.version()).isEqualTo(AlgoReplayInputs.SNAPSHOT_VERSION);
    assertThat(restored.securityStandingOrders()).singleElement().satisfies(order -> {
      assertThat(order.idSecurity()).isEqualTo(7);
      assertThat(order.investAmount()).isEqualTo(500);
      assertThat(order.validTo()).isEqualTo(HOLIDAY.plusMonths(6));
    });
  }

  private static Snapshot securitySnapshot(SecurityStandingOrder order) {
    return new Snapshot(AlgoReplayInputs.SNAPSHOT_VERSION, false, false, 0, Map.of(), Map.of(), Map.of(), List.of(),
        Map.of(), null, Map.of(), Map.of(), Map.of(), List.of(order));
  }

  private static SecurityStandingOrder securityOrder(LocalDate from, LocalDate to, WeekendAdjustType adjustment) {
    return new SecurityStandingOrder(11, 7, 3, 2, "Cash", "CHF", "CHF", TransactionType.ACCUMULATE, null, 500d, true,
        false, null, null, 5d, null, null, RepeatUnit.MONTHS, (short) 1, (byte) from.getDayOfMonth(), null,
        PeriodDayPosition.SPECIFIC_DAY, adjustment, (byte) 0, from, to, null);
  }

  private static Snapshot snapshot(CashStandingOrder order) {
    return new Snapshot(2, false, false, 0, Map.of(), Map.of(), Map.of(), List.of(order));
  }

  private static CashStandingOrder order(LocalDate from, LocalDate to, WeekendAdjustType adjustment) {
    return new CashStandingOrder(1, 2, "Cash", "CHF", TransactionType.DEPOSIT, 100d, null, null, null, null,
        RepeatUnit.MONTHS, (short) 1, (byte) 3, null, PeriodDayPosition.SPECIFIC_DAY, adjustment, (byte) -3, from, to,
        null);
  }
}
