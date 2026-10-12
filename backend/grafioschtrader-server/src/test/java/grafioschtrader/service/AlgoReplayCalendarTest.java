package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
  @DisplayName("A weekend resolves back to the Friday before it")
  void weekend() {
    closedOn();
    assertEquals(FRIDAY, calendar.completedDate(security(null), SUNDAY));
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

}
