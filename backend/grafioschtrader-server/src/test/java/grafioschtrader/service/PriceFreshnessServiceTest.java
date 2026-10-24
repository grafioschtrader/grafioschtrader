package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Stockexchange;

/**
 * Checks which sessions after the youngest historical price count as missing. A session only counts once the last end
 * of day load of its exchange ran at least the waiting time after its close, so the night between the close and the
 * load never colours a cell.
 */
class PriceFreshnessServiceTest {

  private static final LocalDate THU = LocalDate.of(2026, 10, 22);
  private static final LocalDate WED = LocalDate.of(2026, 10, 21);
  private static final LocalDate TUE = LocalDate.of(2026, 10, 20);
  private static final LocalDate MON = LocalDate.of(2026, 10, 19);
  private static final LocalDate FRI = LocalDate.of(2026, 10, 16);
  private static final LocalDate PREV_THU = LocalDate.of(2026, 10, 15);

  private static final List<LocalDate> SESSIONS = List.of(THU, WED, TUE, MON, FRI, PREV_THU);

  private final PriceFreshnessService service = new PriceFreshnessService();

  @Test
  @DisplayName("New York: a load before close + 180 minutes does not expect that session yet")
  void loadTooEarlyAfterCloseExpectsPreviousSession() {
    // 21:00 UTC is 17:00 EDT, one hour after the close and two hours before the waiting time ends.
    Stockexchange xnys = exchange("America/New_York", LocalDateTime.of(2026, 10, 21, 21, 0));
    LocalDate expected = service.expectedEodSession(SESSIONS, xnys, null);
    assertThat(expected).isEqualTo(TUE);
    assertThat(service.sessionsMissed(SESSIONS, TUE, expected)).isZero();
  }

  @Test
  @DisplayName("New York: a load after close + 180 minutes expects the session, a weekend does not count")
  void loadAfterWaitingTimeCountsSessionsAcrossWeekend() {
    // 23:00 UTC is 19:00 EDT, exactly close + 180 minutes.
    Stockexchange xnys = exchange("America/New_York", LocalDateTime.of(2026, 10, 21, 23, 0));
    LocalDate expected = service.expectedEodSession(SESSIONS, xnys, null);
    assertThat(expected).isEqualTo(WED);
    assertThat(service.sessionsMissed(SESSIONS, TUE, expected)).isEqualTo(1);
    // Monday, Tuesday and Wednesday; Saturday and Sunday are no sessions.
    assertThat(service.sessionsMissed(SESSIONS, FRI, expected)).isEqualTo(3);
  }

  @Test
  @DisplayName("A holiday missing from the session list is not reported as a missing price")
  void holidayIsNoMissingSession() {
    List<LocalDate> withHolidayMonday = List.of(THU, WED, TUE, FRI, PREV_THU);
    Stockexchange xnys = exchange("America/New_York", LocalDateTime.of(2026, 10, 22, 6, 0));
    LocalDate expected = service.expectedEodSession(withHolidayMonday, xnys, null);
    assertThat(service.sessionsMissed(withHolidayMonday, FRI, expected)).isEqualTo(2);
  }

  @Test
  @DisplayName("No session after the last trading day of the security is expected")
  void activeToDateBoundsTheExpectation() {
    Stockexchange xnys = exchange("America/New_York", LocalDateTime.of(2026, 10, 22, 6, 0));
    LocalDate expected = service.expectedEodSession(SESSIONS, xnys, MON);
    assertThat(expected).isEqualTo(MON);
    assertThat(service.sessionsMissed(SESSIONS, MON, expected)).isZero();
    assertThat(service.sessionsMissed(SESSIONS, FRI, expected)).isEqualTo(1);
    // Delisted before the loaded window: the age cannot be told.
    assertThat(service.expectedEodSession(SESSIONS, xnys, LocalDate.of(2026, 6, 30))).isNull();
  }

  @Test
  @DisplayName("Sydney: the nightly 05:54 UTC run lies before close + 180 minutes of the same day")
  void sydneyTimeZoneIsConverted() {
    // AEDT is UTC+11 since 4 October 2026; close 16:00 + 180 minutes = 19:00 AEDT = 08:00 UTC.
    Stockexchange xasx = exchange("Australia/Sydney", LocalDateTime.of(2026, 10, 21, 5, 54));
    assertThat(service.expectedEodSession(SESSIONS, xasx, null)).isEqualTo(TUE);
    xasx.setLastDirectPriceUpdate(LocalDateTime.of(2026, 10, 21, 8, 0));
    assertThat(service.expectedEodSession(SESSIONS, xasx, null)).isEqualTo(WED);
  }

  @Test
  @DisplayName("Without any recorded load, or with a gap filled into the future, nothing is reported as missing")
  void unknownOrFutureYieldsNoColour() {
    Stockexchange never = exchange("America/New_York", null);
    assertThat(service.sessionsMissed(SESSIONS, FRI, service.expectedEodSession(SESSIONS, never, null))).isNull();
    assertThat(service.sessionsMissed(SESSIONS, LocalDate.of(2026, 10, 23), WED)).isZero();
  }

  @Test
  @DisplayName("Currency pairs tolerate the newest completed session of the worldwide calendar")
  void globalCalendarToleratesNewestSession() {
    LocalDate expected = service.expectedGlobalEodSession(SESSIONS, THU);
    assertThat(expected).isEqualTo(TUE);
    assertThat(service.sessionsMissed(SESSIONS, TUE, expected)).isZero();
    assertThat(service.sessionsMissed(SESSIONS, MON, expected)).isEqualTo(1);
  }

  private static Stockexchange exchange(String timeZone, LocalDateTime lastDirectPriceUpdateUtc) {
    Stockexchange stockexchange = new Stockexchange("TEST", "Test exchange", "US", false, false, LocalTime.of(9, 30),
        LocalTime.of(16, 0), timeZone, null);
    stockexchange.setLastDirectPriceUpdate(lastDirectPriceUpdateUtc);
    return stockexchange;
  }
}
