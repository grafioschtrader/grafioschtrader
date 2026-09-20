package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * The three calendar questions a replay asks, against a January whose weekdays are known by hand.
 *
 * <p>
 * 2026-01-01 is a Thursday, so 03 and 04 are the weekend and 05 is the following Monday. That is the whole fixture:
 * everything below is stated in those terms rather than in offsets, because a calendar test that computes its own
 * expectations proves nothing.
 * </p>
 */
class AlgoReplayCalendarTest {

  private static final LocalDate THURSDAY = LocalDate.of(2026, 1, 1);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 1, 2);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 1, 4);
  private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 1, 6);
  private static final LocalDate NEXT_FRIDAY = LocalDate.of(2026, 1, 9);

  private final TradingDaysMinusJpaRepository holidays = mock(TradingDaysMinusJpaRepository.class);
  private final TradingDaysPlusJpaRepository tradingDays = mock(TradingDaysPlusJpaRepository.class);
  private final AlgoReplayCalendar calendar = new AlgoReplayCalendar(holidays, tradingDays);

  private Security security(LocalDate activeTo) {
    Stockexchange exchange = mock(Stockexchange.class);
    when(exchange.getIdStockexchange()).thenReturn(3);
    Security security = mock(Security.class);
    when(security.getStockexchange()).thenReturn(exchange);
    when(security.getActiveToDate()).thenReturn(activeTo);
    return security;
  }

  /** The stubs of the individual days are complete before the repository stub starts, which Mockito requires. */
  private void closedOn(LocalDate... days) {
    List<TradingDaysMinus> closed = new ArrayList<>();
    for (LocalDate day : days) {
      TradingDaysMinus entry = mock(TradingDaysMinus.class);
      when(entry.getTradingDateMinus()).thenReturn(day);
      closed.add(entry);
    }
    when(holidays.findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(eq(3),
        any(LocalDate.class), any(LocalDate.class))).thenReturn(closed);
  }

  @Test
  @DisplayName("A weekday the exchange was open on is its own closing day")
  void openWeekday() {
    closedOn();
    assertEquals(TUESDAY, calendar.completedDate(security(null), TUESDAY));
  }

  @Test
  @DisplayName("A weekend resolves back to the Friday before it")
  void weekend() {
    closedOn();
    assertEquals(FRIDAY, calendar.completedDate(security(null), SUNDAY));
  }

  @Test
  @DisplayName("A holiday of the exchange resolves back to its previous session")
  void exchangeHoliday() {
    closedOn(TUESDAY);
    assertEquals(MONDAY, calendar.completedDate(security(null), TUESDAY));
  }

  @Test
  @DisplayName("An instrument whose exchange is unknown has no calendar to read")
  void withoutExchange() {
    Security security = mock(Security.class);
    assertEquals("MEAN_REVERSION_CALENDAR_REQUIRED",
        assertThrows(IllegalArgumentException.class, () -> calendar.completedDate(security, TUESDAY)).getMessage());
  }

  @Test
  @DisplayName("An instrument that had already expired is reported rather than valued")
  void inactiveInstrument() {
    closedOn();
    assertEquals("MEAN_REVERSION_INACTIVE_INSTRUMENT",
        assertThrows(IllegalArgumentException.class, () -> calendar.completedDate(security(THURSDAY), TUESDAY))
            .getMessage());
  }

  @Test
  @DisplayName("An order decided on Friday is filled on the following Monday, never on the Friday itself")
  void fillIsAlwaysALaterSession() {
    closedOn();
    assertEquals(Optional.of(MONDAY), calendar.nextEligibleClose(security(null), FRIDAY, NEXT_FRIDAY));
  }

  @Test
  @DisplayName("An order decided on the last day of the run stays unfilled")
  void noSessionLeftWithinTheRun() {
    closedOn();
    assertTrue(calendar.nextEligibleClose(security(null), NEXT_FRIDAY, NEXT_FRIDAY).isEmpty());
  }

  @Test
  @DisplayName("A holiday on the day after a decision moves the fill one session further")
  void fillSkipsAHoliday() {
    closedOn(MONDAY);
    assertEquals(Optional.of(TUESDAY), calendar.nextEligibleClose(security(null), FRIDAY, NEXT_FRIDAY));
  }

  @Test
  @DisplayName("A directly held bond is filled on the session before maturity, never on maturity")
  void bondIsNotFilledOnMaturityDay() {
    closedOn();
    Security bond = security(TUESDAY);
    when(bond.getAssetClass()).thenReturn(mock(grafioschtrader.entities.Assetclass.class));
    when(bond.isBondDirectInvestment()).thenReturn(true);
    assertEquals(Optional.of(MONDAY), calendar.nextEligibleClose(bond, FRIDAY, NEXT_FRIDAY));
    assertTrue(calendar.nextEligibleClose(bond, MONDAY, NEXT_FRIDAY).isEmpty());
  }

  @Test
  @DisplayName("A stock may still be filled on its last active day")
  void stockMayFillOnLastActiveDay() {
    closedOn();
    assertEquals(Optional.of(MONDAY), calendar.nextEligibleClose(security(MONDAY), FRIDAY, NEXT_FRIDAY));
  }

  @Test
  @DisplayName("A bond is inactive for mean reversion on its maturity day")
  void bondCompletedDateOnMaturityIsInactive() {
    closedOn();
    Security bond = security(TUESDAY);
    when(bond.getAssetClass()).thenReturn(mock(grafioschtrader.entities.Assetclass.class));
    when(bond.isBondDirectInvestment()).thenReturn(true);
    assertEquals("MEAN_REVERSION_INACTIVE_INSTRUMENT",
        assertThrows(IllegalArgumentException.class, () -> calendar.completedDate(bond, TUESDAY)).getMessage());
  }

  @Test
  @DisplayName("A run starts on the day after the opening date, never on the opening date itself")
  void runExcludesTheOpeningDay() {
    TradingDaysPlus day = mock(TradingDaysPlus.class);
    when(day.getTradingDate()).thenReturn(MONDAY);
    when(tradingDays.findByTradingDateBetweenOrderByTradingDate(FRIDAY, NEXT_FRIDAY)).thenReturn(List.of(day));

    assertEquals(List.of(MONDAY), calendar.runDates(THURSDAY, NEXT_FRIDAY));
    verify(tradingDays).findByTradingDateBetweenOrderByTradingDate(FRIDAY, NEXT_FRIDAY);
  }
}
