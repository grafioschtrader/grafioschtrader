package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Runs the SIX, Xetra and OpenFIGI adapters against recorded provider responses served by a local HTTP server. The
 * fixtures under {@code src/test/resources/securitysearch/} are real responses captured without any key.
 */
class SecuritySearchProviderFixtureTest {

  private static final String SIX_PATH = "/itf/fqs/delayed/movie.json";
  private static final String XETRA_SEARCH_PATH = "/tradingview/search";
  private static final String XETRA_PRICE_PATH = "/live/data/price_information/single";
  private static final String OPENFIGI_PATH = "/v3/mapping";

  private static final String NOVARTIS = "CH0012005267";
  private static final String APPLE = "US0378331005";
  private static final String SAP = "DE0007164600";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private SixSecuritySearch six() {
    return new SixSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  private XetraSecuritySearch xetra() {
    return new XetraSecuritySearch(server.baseUrl(), server.baseUrl() + "live/", Duration.ofSeconds(5));
  }

  private OpenFigiSecuritySearch openFigi() {
    return new OpenFigiSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("SIX: one listing delivers ISIN, currency and MIC")
  void sixSingleListing() {
    server.fixture(SIX_PATH, "six/single-listing.json");
    SecuritySearchResult result = six().search(SecuritySearchQuery.of(NOVARTIS, "CHF", "XSWX", "NOVN"));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto c = result.uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow();
    assertEquals("NOVN", c.providerSymbol());
    assertEquals("CH0012005267CHF4", c.providerInstrumentId());
    assertEquals(IdentityEvidence.RETURNED, c.isinEvidence());
    assertEquals(IdentityEvidence.RETURNED, c.currencyEvidence());
    assertEquals("XSWX", c.mic());
  }

  @Test
  @DisplayName("SIX: two trading currencies stay ambiguous until the currency is queried")
  void sixMultiCurrency() {
    server.fixture(SIX_PATH, "six/multi-currency.json");
    SecuritySearchResult open = six().search(SecuritySearchQuery.of(APPLE, null, "XSWX", "AAPL"));
    assertEquals(2, open.candidates().size());
    assertTrue(open.uniqueCandidateOnMic("XSWX", "AAPL").isEmpty());

    SecuritySearchResult usd = six().search(SecuritySearchQuery.of(APPLE, "USD", "XSWX", "AAPL"));
    assertEquals("US0378331005USD4", usd.uniqueCandidateOnMic("XSWX", "AAPL").orElseThrow().providerInstrumentId());
    assertEquals(1, usd.rejectedCandidates());
  }

  @Test
  @DisplayName("SIX: an empty response is NO_MATCH; another exchange or an invalid ISIN is not asked at all")
  void sixEmptyAndUnsupported() {
    server.fixture(SIX_PATH, "six/empty.json");
    assertEquals(SecuritySearchStatus.NO_MATCH, six().search(SecuritySearchQuery.of("CH0002075403", null, null, null))
        .status());
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY,
        six().search(SecuritySearchQuery.of(APPLE, null, "XNAS", null)).status());
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY,
        six().search(SecuritySearchQuery.of("CH001200526'", null, null, null)).status());
    assertEquals(1, server.requests().size());
  }

  @Test
  @DisplayName("Xetra: the listing on the queried exchange gets its currency, the Frankfurt listing is rejected")
  void xetraTwoListings() {
    server.fixture(XETRA_SEARCH_PATH, "xetra/search-two-listings.json").fixture(XETRA_PRICE_PATH,
        "xetra/price-information.json");
    SecuritySearchResult result = xetra().search(SecuritySearchQuery.of(SAP, "EUR", "XETR", "SAP"));
    assertEquals(SecuritySearchStatus.OK, result.status());
    assertEquals(1, result.rejectedCandidates());
    SecuritySearchCandidateDto c = result.uniqueCandidateOnMic("XETR", null).orElseThrow();
    assertEquals("XETR:" + SAP, c.providerSymbol());
    assertEquals("EUR", c.currency());
    assertEquals(IdentityEvidence.RETURNED, c.currencyEvidence());
    assertEquals(1, server.requests().stream().filter(r -> r.startsWith(XETRA_PRICE_PATH)).count());
  }

  @Test
  @DisplayName("Xetra: a failing price request leaves the currency unknown but keeps the listing")
  void xetraCurrencyUnknown() {
    server.fixture(XETRA_SEARCH_PATH, "xetra/search-two-listings.json").status(XETRA_PRICE_PATH, 400,
        "{\"status\":400}");
    SecuritySearchCandidateDto c = xetra().search(SecuritySearchQuery.of(SAP, null, "XETR", null))
        .uniqueCandidateOnMic("XETR", null).orElseThrow();
    assertNull(c.currency());
    assertEquals(IdentityEvidence.UNKNOWN, c.currencyEvidence());
  }

  @Test
  @DisplayName("Xetra: an empty response is NO_MATCH")
  void xetraEmpty() {
    server.fixture(XETRA_SEARCH_PATH, "xetra/search-empty.json");
    assertEquals(SecuritySearchStatus.NO_MATCH, xetra().search(SecuritySearchQuery.of(SAP, null, "XETR", null))
        .status());
  }

  @Test
  @DisplayName("OpenFIGI: the MIC- and currency-filtered job yields a FILTERED candidate with its FIGI")
  void openFigiFiltered() {
    server.fixture(OPENFIGI_PATH, "openfigi/filtered-and-unfiltered.json");
    SecuritySearchResult result = openFigi().search(SecuritySearchQuery.of(NOVARTIS, "CHF", "XSWX", "NOVN"));
    SecuritySearchCandidateDto c = result.uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow();
    assertEquals("BBG00HCYS021", c.providerInstrumentId());
    assertEquals(IdentityEvidence.FILTERED, c.isinEvidence());
    assertEquals(IdentityEvidence.FILTERED, c.currencyEvidence());
    assertEquals(IdentityEvidence.FILTERED, c.micEvidence());
    assertTrue(server.requests().getFirst().contains("\"micCode\":\"XSWX\""));
  }

  @Test
  @DisplayName("OpenFIGI: an ISIN known only without the MIC filter is NO_MATCH with an explaining detail")
  void openFigiNoListingOnMic() {
    server.fixture(OPENFIGI_PATH, "openfigi/wrong-segment-then-unfiltered.json");
    SecuritySearchResult result = openFigi().search(SecuritySearchQuery.of(APPLE, null, "XSWX", null));
    assertEquals(SecuritySearchStatus.NO_MATCH, result.status());
    assertTrue(result.detail().contains("no listing on XSWX"), result.detail());
  }

  @Test
  @DisplayName("OpenFIGI: XNAS is asked through its segment MICs and the hit is reported on XNAS")
  void openFigiSegmentMics() {
    List<String> mics = OpenFigiSecuritySearch.createJobs(SecuritySearchQuery.of(APPLE, null, "XNAS", null)).stream()
        .map(j -> j.get("micCode")).toList();
    assertEquals(Arrays.asList("XNAS", "XNGS", "XNMS", "XNCM", null), mics);
    String body = """
        [{"warning":"No identifier found."},
         {"data":[{"figi":"BBG000B9XRY4","name":"APPLE INC","ticker":"AAPL","exchCode":"UW","securityType":"Common Stock"}]},
         {"warning":"No identifier found."},{"warning":"No identifier found."},
         {"data":[{"figi":"X","ticker":"AAPL","exchCode":"US"}]}]""";
    server.status(OPENFIGI_PATH, 200, body);
    SecuritySearchResult result = openFigi().search(SecuritySearchQuery.of(APPLE, null, "XNAS", "AAPL"));
    assertEquals("UW", result.uniqueCandidateOnMic("XNAS", "AAPL").orElseThrow().providerExchangeCode());
  }

  @Test
  @DisplayName("OpenFIGI: without MIC all listings come back with an unknown MIC; an invalid ISIN is NO_MATCH")
  void openFigiWithoutMicAndInvalidIsin() {
    server.fixture(OPENFIGI_PATH, "openfigi/multi-currency-unfiltered.json");
    SecuritySearchResult result = openFigi().search(SecuritySearchQuery.of(APPLE, null, null, null));
    assertEquals(3, result.candidates().size());
    assertTrue(result.candidates().stream().allMatch(c -> c.micEvidence() == IdentityEvidence.UNKNOWN));

    server.fixture(OPENFIGI_PATH, "openfigi/invalid-isin.json");
    assertEquals(SecuritySearchStatus.NO_MATCH, openFigi().search(SecuritySearchQuery.of(APPLE, null, "XSWX", null))
        .status());
  }

  @Test
  @DisplayName("HTTP 429, 403, 500, an unparsable body and a timeout become statuses, never exceptions")
  void httpFailures() {
    SecuritySearchQuery query = SecuritySearchQuery.of(NOVARTIS, null, "XSWX", null);
    server.status(SIX_PATH, 429, "");
    assertEquals(SecuritySearchStatus.RATE_LIMITED, six().search(query).status());
    server.status(SIX_PATH, 403, "");
    assertEquals(SecuritySearchStatus.NOT_ENTITLED, six().search(query).status());
    server.status(SIX_PATH, 500, "");
    assertEquals(SecuritySearchStatus.PROVIDER_ERROR, six().search(query).status());
    server.status(SIX_PATH, 200, "<html>Bad request</html>");
    assertEquals(SecuritySearchStatus.PROVIDER_ERROR, six().search(query).status());
    server.delayed(SIX_PATH, 1500);
    SecuritySearchResult timeout = new SixSecuritySearch(server.baseUrl(), Duration.ofMillis(300)).search(query);
    assertEquals(SecuritySearchStatus.TIMEOUT, timeout.status());
    assertTrue(timeout.status().isTransient());
  }
}
