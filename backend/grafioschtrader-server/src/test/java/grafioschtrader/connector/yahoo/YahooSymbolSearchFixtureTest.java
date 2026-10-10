package grafioschtrader.connector.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.securitysearch.FixtureHttpServer;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Runs the Yahoo symbol search against recorded Yahoo responses. The exchange mapping is a subset of the Yahoo rows of
 * {@code mic_provider_map}, including its ambiguous code {@code NGM} and the segment MIC XNMS behind {@code NMS}.
 */
class YahooSymbolSearchFixtureTest {

  private static final String PATH = "/v1/finance/search";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private YahooSymbolSearch search(List<MicProviderMap> rows) {
    MicProviderMapRepository repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(YahooHelper.YAHOO)).thenReturn(rows);
    return new YahooSymbolSearch(repository, server.baseUrl() + "v1/finance/search", Duration.ofSeconds(5));
  }

  private YahooSymbolSearch search() {
    return search(List.of(row("XSWX", "SWX", ".SW"), row("XETR", "GER", ".DE"), row("XNAS", "NGM", null),
        row("NSME", "NGM", null), row("XNMS", "NMS", null), row("XNYS", "NYQ", null), row("MTAA", "MIL", null),
        row("XVIE", "VIE", ".VI")));
  }

  private static MicProviderMap row(String mic, String code, String suffix) {
    return new MicProviderMap(YahooHelper.YAHOO, mic, code, suffix);
  }

  @Test
  @DisplayName("Novartis comes back as 'EBS'; the '.SW' suffix places it on XSWX without a ticker query")
  void suffixConfirmsMic() {
    server.fixture(PATH, "yahoo/isin-novartis.json");
    SecuritySearchResult result = search().search(SecuritySearchQuery.of("CH0012005267", "CHF", "XSWX", "NOVN"));
    assertEquals("NOVN.SW", result.uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow().providerSymbol());
    assertEquals(1, server.requests().size());
  }

  @Test
  @DisplayName("Apple comes back as 'NMS', mapped to the segment XNMS and folded into XNAS")
  void segmentMicFolded() {
    server.fixture(PATH, "yahoo/isin-apple.json");
    SecuritySearchResult result = search().search(SecuritySearchQuery.of("US0378331005", "USD", "XNAS", "AAPL"));
    assertEquals("AAPL", result.uniqueCandidateOnMic("XNAS", "AAPL").orElseThrow().providerSymbol());
  }

  @Test
  @DisplayName("A listing on another exchange is rejected and the ticker is searched as fallback")
  void wrongMicTriggersTickerFallback() {
    server.fixture(PATH, "yahoo/isin-etf-other-exchange.json").status(PATH + "?q=CSSPX", 200, """
        {"count":1,"quotes":[{"exchange":"EBS","shortname":"ISHARES CORE S&P 500","quoteType":"ETF","symbol":"CSSPX.SW"}]}""");
    SecuritySearchResult result = search().search(SecuritySearchQuery.of("IE00B5BMR087", "USD", "XSWX", "CSSPX"));
    assertEquals(1, result.rejectedCandidates());
    assertEquals("CSSPX.SW", result.uniqueCandidateOnMic("XSWX", "CSSPX").orElseThrow().providerSymbol());
    assertEquals(2, server.requests().size());
  }

  @Test
  @DisplayName("Several listings with the same suffix are narrowed by the ticker, not by their order")
  void multipleMatchesNarrowedByTicker() {
    server.fixture(PATH, "yahoo/ticker-novn.json");
    SecuritySearchResult result = search().search(SecuritySearchQuery.of(null, null, "XSWX", "NOVN"));
    assertTrue(result.candidates().stream().filter(c -> "XSWX".equals(c.mic())).count() > 1);
    assertTrue(result.uniqueCandidateOnMic("XSWX", null).isEmpty());
    assertEquals("NOVN.SW", result.uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow().providerSymbol());
  }

  @Test
  @DisplayName("A queried MIC without mapping causes no exception; a missing mapping leaves the MIC unknown")
  void absentMicMapping() {
    server.fixture(PATH, "yahoo/isin-novartis.json");
    SecuritySearchResult unmappedQuery = search().search(SecuritySearchQuery.of("CH0012005267", null, "XXXZ", "NOVN"));
    assertEquals(SecuritySearchStatus.NO_MATCH, unmappedQuery.status());
    assertEquals(1, unmappedQuery.rejectedCandidates());

    SecuritySearchResult noMapping = search(List.of())
        .search(SecuritySearchQuery.of("CH0012005267", null, "XSWX", "NOVN"));
    assertEquals(SecuritySearchStatus.OK, noMapping.status());
    assertTrue(noMapping.uniqueCandidateOnMic("XSWX", "NOVN").isEmpty());
  }

  @Test
  @DisplayName("An empty Yahoo response is NO_MATCH")
  void emptyResponse() {
    server.fixture(PATH, "yahoo/empty.json");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        search().search(SecuritySearchQuery.of("CH0373476636", null, "XSWX", null)).status());
  }
}
