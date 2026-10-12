package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Nasdaq Nordic against search answers recorded on 2026-10-12: Sandvik (SE0000667891) in Stockholm, Nokia
 * (FI0009000681) with order books in Stockholm and Helsinki, and the XACT Sverige ETF (SE0001056045).
 */
class NasdaqNordicSecuritySearchTest {

  private static final String SANDVIK = "SE0000667891";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private NasdaqNordicSecuritySearch nordic() {
    return new NasdaqNordicSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("Share: the listing page from the order book id; no ticker, the Nordic symbol stays an attribute")
  void share() {
    server.fixture("/nordic/search", "nasdaqnordic/search-sandvik.json");
    SecuritySearchResult result = nordic().search(SecuritySearchQuery.of(SANDVIK, "SEK", "XSTO", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto candidate = result.candidates().getFirst();
    assertEquals("XSTO", candidate.mic());
    assertEquals("TX316", candidate.providerInstrumentId());

    SecurityBaseDataDto data = nordic().getSecurityBaseData(candidate, Locale.GERMAN).data();
    assertNull(data.tickerSymbol(), "NOVO B would not match Yahoo's NOVO-B");
    assertEquals("SEK", data.currency());
    assertEquals("Sandvik", data.name());
    assertEquals("https://www.nasdaq.com/european-market-activity/shares/sand?id=TX316", data.stockexchangeLink());
    assertEquals("SAND", data.providerAttributes().get("symbol"));
  }

  @Test
  @DisplayName("The currency of the exchange picks the order book: Nokia in SEK for Stockholm, none for Copenhagen")
  void orderBookByCurrency() {
    String body = FixtureHttpServer.readFixture("nasdaqnordic/search-nokia.json");
    List<SecuritySearchCandidateDto> stockholm = NasdaqNordicSecuritySearch.parse(body, "FI0009000681", "XSTO");
    assertEquals(1, stockholm.size());
    assertEquals("TX282292", stockholm.getFirst().providerInstrumentId());
    assertEquals("TX50063",
        NasdaqNordicSecuritySearch.parse(body, "FI0009000681", "XHEL").getFirst().providerInstrumentId());
    assertTrue(NasdaqNordicSecuritySearch.parse(body, "FI0009000681", "XCSE").isEmpty());
  }

  @Test
  @DisplayName("Index: the code is the ticker, which lets Yahoo and EODHD find it; the page lies under indexes")
  void index() {
    SecuritySearchCandidateDto omxh25 = NasdaqNordicSecuritySearch
        .parse(FixtureHttpServer.readFixture("nasdaqnordic/search-omxh25.json"), "FI0008900212", "XHEL").getFirst();
    SecurityBaseDataDto data = NasdaqNordicSecuritySearch.toBaseData(omxh25);
    assertEquals("OMXH25", data.tickerSymbol());
    assertEquals("EUR", data.currency());
    assertEquals("https://www.nasdaq.com/european-market-activity/indexes/omxh25?id=IX2165", data.stockexchangeLink());
  }

  @Test
  @DisplayName("ETF on its own path; unknown ISIN gives nothing; asked for Nordic exchanges only")
  void etfUnknownAndScope() {
    SecuritySearchCandidateDto xact = NasdaqNordicSecuritySearch
        .parse(FixtureHttpServer.readFixture("nasdaqnordic/search-xact.json"), "SE0001056045", "XSTO").getFirst();
    assertEquals("https://www.nasdaq.com/european-market-activity/etf/xact-sverige?id=TX1678",
        NasdaqNordicSecuritySearch.toBaseData(xact).stockexchangeLink());
    assertTrue(NasdaqNordicSecuritySearch.parse("{\"data\":null,\"messages\":{\"code\":\"NO_INST_FOUND\"}}", SANDVIK,
        "XSTO").isEmpty());
    assertFalse(nordic().supports(SecuritySearchQuery.of(SANDVIK, "SEK", "XSWX", null)));
    assertTrue(nordic().supports(SecuritySearchQuery.of(SANDVIK, "SEK", "XSTO", null)));
  }
}
