package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.service.AlgoReplayInputs.CashStandingOrder;
import grafioschtrader.service.AlgoReplayInputs.Snapshot;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

class AlgoReplayStandingOrderServiceTest {

  private final AlgoReplayStandingOrderService service = new AlgoReplayStandingOrderService(null, null, null);

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

  private static Snapshot snapshot(CashStandingOrder order) {
    return new Snapshot(2, false, false, 0, Map.of(), Map.of(), Map.of(), List.of(order));
  }

  private static CashStandingOrder order(LocalDate from, LocalDate to, WeekendAdjustType adjustment) {
    return new CashStandingOrder(1, 2, "Cash", "CHF", TransactionType.DEPOSIT, 100d, null, null, null, null,
        RepeatUnit.MONTHS, (short) 1, (byte) 3, null, PeriodDayPosition.SPECIFIC_DAY, adjustment, (byte) -3, from, to,
        null);
  }
}
