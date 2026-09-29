package grafiosch.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import grafiosch.BaseConstants;

class ClientClockTest {
  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void missingInvalidAndBackgroundZonesUseUtc() {
    assertEquals(ZoneOffset.UTC, ClientClock.zone());
    for (String zone : new String[] { null, "", "invalid/zone", "GMT+99" }) {
      request(zone);
      assertEquals(ZoneOffset.UTC, ClientClock.zone());
    }
    request("Asia/Tokyo");
    assertEquals(ZoneOffset.UTC, CompletableFuture.supplyAsync(ClientClock::zone).join());
    LocalDate before = LocalDate.now(ZoneOffset.UTC);
    clearRequest();
    LocalDate today = ClientClock.today();
    assertTrue(today.equals(before) || today.equals(LocalDate.now(ZoneOffset.UTC)));
  }

  @Test
  void resolvesClientCalendarAndDaylightSavingOnEveryRequest() {
    request("America/Los_Angeles");
    ZoneId la = ClientClock.zone();
    assertEquals(LocalDate.of(2026, 9, 15), Instant.parse("2026-09-16T03:00:00Z").atZone(la).toLocalDate());
    assertEquals(ZoneOffset.ofHours(-8), la.getRules().getOffset(Instant.parse("2026-01-15T08:00:00Z")));
    assertEquals(ZoneOffset.ofHours(-7), la.getRules().getOffset(Instant.parse("2026-09-15T08:00:00Z")));
    request("Asia/Tokyo");
    assertEquals(LocalDate.of(2026, 9, 16),
        Instant.parse("2026-09-15T22:00:00Z").atZone(ClientClock.zone()).toLocalDate());
    LocalDate before = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    LocalDate today = ClientClock.today();
    assertTrue(today.equals(before) || today.equals(LocalDate.now(ZoneId.of("Asia/Tokyo"))));
  }

  @Test
  void dateHelpersRespectTheSuppliedCalendarDay() {
    LocalDate monday = LocalDate.of(2026, 9, 14);
    assertTrue(DateHelper.isTodayOrAfter(monday, monday));
    assertFalse(DateHelper.isTodayOrAfter(monday, monday.plusDays(1)));
    assertTrue(DateHelper.isUntilDateEqualNowOrAfterOrInActualWeekend(monday.minusDays(2), monday));
    assertFalse(DateHelper.isUntilDateEqualNowOrAfterOrInActualWeekend(monday.minusDays(2), monday.plusDays(1)));
  }

  private void request(String zone) {
    var request = new MockHttpServletRequest();
    if (zone != null) {
      request.addHeader(BaseConstants.TIME_ZONE_HEADER, zone);
    }
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
