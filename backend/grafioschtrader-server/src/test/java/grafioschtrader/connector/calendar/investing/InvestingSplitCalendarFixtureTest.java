package grafioschtrader.connector.calendar.investing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.connector.calendar.SplitCalendarAppender;

/**
 * Split rows of investing.com recorded for 2026-10-16 (Canada, USA, Switzerland, Germany). On that day the Canadian
 * depositary receipts of AMD and CrowdStrike split 7:1 and 5:1 under the tickers of the US stocks, and were matched
 * with the US stocks before the listing country was taken into account.
 */
class InvestingSplitCalendarFixtureTest {

  private static final String[] COUNTRY_CODES = { "CA", "CH", "DE", "US" };

  private static Map<String, TickerSecuritysplit> readFixture() throws IOException {
    try (InputStream in = InvestingSplitCalendarFixtureTest.class
        .getResourceAsStream("/connector/investing/split-calendar-2026-10-16.html")) {
      String rows = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return new InvestingSplitCalendar().readSplitTable(Jsoup.parseBodyFragment("<table>" + rows + "</table>"),
          LocalDate.of(2026, 10, 16), InvestingSplitCalendar.createCountryCodeByFlag(COUNTRY_CODES));
    }
  }

  @Test
  @DisplayName("Each split carries the ISO country of the flag of its row")
  void countryOfListing() throws IOException {
    Map<String, TickerSecuritysplit> splits = readFixture();

    assertEquals("CA", splits.get("AMD").countryCode);
    assertEquals("CA", splits.get("CRWD").countryCode);
    assertEquals("US", splits.get("KNDI").countryCode);
    assertEquals("DE", splits.get("AUZ").countryCode);
    assertEquals(1, splits.get("AMD").securitysplit.getFromFactor());
    assertEquals(7, splits.get("AMD").securitysplit.getToFactor());
  }

  @Test
  @DisplayName("The CDR split of AMD is not matched with the AMD stock on NASDAQ")
  void cdrSplitIsNotTheUsStock() throws IOException {
    Map<String, TickerSecuritysplit> splits = readFixture();

    assertFalse(SplitCalendarAppender.isSameListing(splits.get("AMD"), "US", ""));
    assertFalse(SplitCalendarAppender.isSameListing(splits.get("CRWD"), "US", ""));
    assertTrue(SplitCalendarAppender.isSameListing(splits.get("KNDI"), "US", ""));
  }

  @Test
  @DisplayName("A Yahoo suffix must match the suffix of the security's exchange")
  void yahooSuffix() {
    TickerSecuritysplit cdr = new TickerSecuritysplit("Advanced Micro Devices", null, ".NE", null);
    TickerSecuritysplit nestle = new TickerSecuritysplit("Nestle", null, ".SW", null);

    assertFalse(SplitCalendarAppender.isSameListing(cdr, "US", ""));
    assertTrue(SplitCalendarAppender.isSameListing(nestle, "CH", ".SW"));
  }

}
