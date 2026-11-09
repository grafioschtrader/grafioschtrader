package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.connector.instrument.coinmarketcap.CoinMarketCapFeedConnector;
import grafioschtrader.connector.instrument.consorsbank.ConsorsbankFeedConnector;
import grafioschtrader.connector.instrument.onvista.OnvistaFeedConnector;
import grafioschtrader.dto.CurrencypairLookupData;
import grafioschtrader.dto.CurrencypairSearchHit;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Currency pair search and URL extension derivation against answers recorded on 2026-11-04: onvista for EUR/CAD, the
 * Boursorama search for CHF/VND and the Boursorama page of EUR/CHF. The expected extensions are those that delivered
 * prices in a live request on that day.
 */
class CurrencypairSearchTest {

  private FixtureHttpServer server;

  @BeforeEach
  void setUp() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private static CurrencypairLookupData data(String from, String to, CurrencypairSearchHit... hits) {
    return new CurrencypairLookupData(from, to, List.of(hits));
  }

  @Test
  @DisplayName("onvista: the FactSet notation of EUR/CAD gives the onvista and the Consorsbank URL extension")
  void onvistaNotationServesOnvistaAndConsorsbank() {
    server.fixture("/instruments/query", "onvista/query-eurcad.json").fixture("/instruments/CURRENCY/EURCAD/snapshot",
        "onvista/snapshot-eurcad.json");
    CurrencypairSearchResult result = new OnvistaSecuritySearch(mock(MicProviderMapRepository.class), server.baseUrl(),
        Duration.ofSeconds(5)).searchCurrencypair("EUR", "CAD");

    assertEquals(SecuritySearchStatus.OK, result.status());
    CurrencypairSearchHit hit = result.hits().getFirst();
    assertEquals("8361803", hit.notation());
    assertEquals("@W$", hit.exchangeCode());
    CurrencypairLookupData data = data("EUR", "CAD", hit);
    assertEquals(Optional.of("CURRENCY/EURCAD/eod_history?idNotation=8361803"),
        new OnvistaFeedConnector().deriveCurrencyUrlExtension(data, FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("_8361803,@W$"),
        new ConsorsbankFeedConnector().deriveCurrencyUrlExtension(data, FeedSupport.FS_INTRA));
  }

  @Test
  @DisplayName("onvista: a pair only quoted the other way round is no hit, and its snapshot is not requested")
  void onvistaInversePairIsNoHit() {
    server.fixture("/instruments/query", "onvista/query-eurcad.json");
    CurrencypairSearchResult result = new OnvistaSecuritySearch(mock(MicProviderMapRepository.class), server.baseUrl(),
        Duration.ofSeconds(5)).searchCurrencypair("CAD", "EUR");

    assertEquals(SecuritySearchStatus.NO_MATCH, result.status());
    assertTrue(server.requests().stream().noneMatch(r -> r.contains("snapshot")));
    assertEquals(Optional.empty(),
        new ConsorsbankFeedConnector().deriveCurrencyUrlExtension(data("CAD", "EUR"), FeedSupport.FS_HISTORY));
  }

  @Test
  @DisplayName("Boursorama: the search links the page in the queried direction, not the inverse pair")
  void boursoramaPageOfQueriedDirection() {
    String search = FixtureHttpServer.readFixture("boursorama/search-chfvnd.html");
    assertEquals("bourse/devises/taux-de-change-francsuisse-dong-CHF-VND/",
        BoursoramaSecuritySearch.currencypairPagePath(search, "CHF", "VND"));
    assertEquals("bourse/devises/taux-de-change-dong-francsuisse-VND-CHF/",
        BoursoramaSecuritySearch.currencypairPagePath(search, "VND", "CHF"));
    assertNull(BoursoramaSecuritySearch.currencypairPagePath(search, "CHF", "SLE"));
  }

  @Test
  @DisplayName("Boursorama: the irregular symbol of EUR/CHF comes from its page and becomes the URL extension")
  void boursoramaIrregularSymbol() {
    String pagePath = "bourse/devises/taux-de-change-euro-francsuisse-EUR-CHF/";
    server.status("/recherche/ajax", 200, "<a class=\"search__list-link\" href=\"/" + pagePath + "\">EUR/CHF</a>")
        .fixture("/" + pagePath, "boursorama/devise-eurchf.html");
    CurrencypairSearchResult result = new BoursoramaSecuritySearch(mock(MicProviderMapRepository.class),
        server.baseUrl(), Duration.ofSeconds(5)).searchCurrencypair("EUR", "CHF");

    assertEquals(SecuritySearchStatus.OK, result.status());
    assertEquals(Optional.of("1xCHFVS"), new BoursoramaFeedConnector()
        .deriveCurrencyUrlExtension(data("EUR", "CHF", result.hits().getFirst()), FeedSupport.FS_HISTORY));
    assertTrue(BoursoramaSecuritySearch
        .parseCurrencypairPage(FixtureHttpServer.readFixture("boursorama/devise-eurchf.html"), "CHF", "EUR").isEmpty(),
        "the page of EUR/CHF is no hit for CHF/EUR");
  }

  @Test
  @DisplayName("Boursorama without a hit offers the regular symbol, which the probe checks")
  void boursoramaRegularSymbol() {
    assertEquals(Optional.of("3fCHF_COP"),
        new BoursoramaFeedConnector().deriveCurrencyUrlExtension(data("CHF", "COP"), FeedSupport.FS_INTRA));
  }

  @Test
  @DisplayName("CoinMarketCap: the mappings stored for BTC/CHF, CHF/BTC and BTC/COP; no fiat pair")
  void coinMarketCapMapping() {
    CoinMarketCapFeedConnector connector = new CoinMarketCapFeedConnector();
    assertEquals(Optional.of("BTC=1,CHF=2785"),
        connector.deriveCurrencyUrlExtension(data("BTC", "CHF"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("BTC=1,CHF=2785"),
        connector.deriveCurrencyUrlExtension(data("CHF", "BTC"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("BTC=1,COP=2820"),
        connector.deriveCurrencyUrlExtension(data("BTC", "COP"), FeedSupport.FS_INTRA));
    assertEquals(Optional.of("ETH=1027,BTC=1"),
        connector.deriveCurrencyUrlExtension(data("ETH", "BTC"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.empty(), connector.deriveCurrencyUrlExtension(data("EUR", "USD"), FeedSupport.FS_HISTORY));
  }
}
