package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.types.AssetclassType;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class WatchlistResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/watchlists.json";

  private List<WatchlistFixture> integrationWatchlists;

  @BeforeAll
  void setUp() throws IOException {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    integrationWatchlists = loadIntegrationWatchlists();
  }

  @Test
  @Order(1)
  @DisplayName("Create integration watchlists, add instruments, and select performance watchlist through REST")
  void createWatchlistsWithInstruments() {
    Assertions.assertThat(integrationWatchlists).isNotEmpty();
    integrationWatchlists.forEach(this::createWatchlistWithInstruments);
  }

  private void createWatchlistWithInstruments(WatchlistFixture fixture) {
    Watchlist created = authenticatedClient(fixture.loginNickname)
        .post()
        .uri(RequestGTMappings.WATCHLIST_MAP)
        .body(new Watchlist(null, fixture.name))
        .exchange()
        .expectStatus().isOk()
        .expectBody(Watchlist.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    Assertions.assertThat(created.getIdWatchlist()).isPositive();
    Assertions.assertThat(created.getName()).isEqualTo(fixture.name);

    SecuritycurrencyLists instruments = resolveInstruments(fixture, created.getIdWatchlist());
    authenticatedClient(fixture.loginNickname)
        .put()
        .uri(RequestGTMappings.WATCHLIST_MAP + "/" + created.getIdWatchlist() + "/addSecuritycurrency")
        .body(instruments)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Watchlist.class)
        .returnResult();

    if (fixture.main) {
      Tenant tenant = authenticatedClient(fixture.loginNickname)
          .patch()
          .uri(RequestGTMappings.TENANT_MAP + "/watchlistforperformance/" + created.getIdWatchlist())
          .exchange()
          .expectStatus().isOk()
          .expectBody(Tenant.class)
          .returnResult()
          .getResponseBody();
      assertNotNull(tenant);
      Assertions.assertThat(tenant.getIdWatchlistPerformance()).isEqualTo(created.getIdWatchlist());
    }

    verifyWatchlistThroughRest(fixture, created.getIdWatchlist());
  }

  private SecuritycurrencyLists resolveInstruments(WatchlistFixture fixture, Integer idWatchlist) {
    List<Security> securities = new ArrayList<>();
    for (SecurityFixture securityFixture : fixture.securities) {
      SecuritycurrencyLists result = authenticatedClient(fixture.loginNickname)
          .get()
          .uri(uriBuilder -> uriBuilder
              .path(RequestGTMappings.WATCHLIST_MAP + "/{idWatchlist}/search")
              .queryParam("isin", securityFixture.isin)
              .queryParam("currency", securityFixture.currency)
              .build(idWatchlist))
          .exchange()
          .expectStatus().isOk()
          .expectBody(SecuritycurrencyLists.class)
          .returnResult()
          .getResponseBody();
      assertNotNull(result);
      List<Security> exactMatches = result.securityList.stream()
          .filter(security -> securityFixture.isin.equals(security.getIsin())
              && securityFixture.currency.equals(security.getCurrency()))
          .toList();
      Assertions.assertThat(exactMatches)
          .as("security resolved through REST: %s/%s", securityFixture.isin, securityFixture.currency)
          .hasSize(1);
      securities.add(exactMatches.getFirst());
    }

    List<Currencypair> currencypairs = new ArrayList<>();
    for (CurrencyPairFixture currencypairFixture : fixture.currencyPairs) {
      SecuritycurrencyLists result = authenticatedClient(fixture.loginNickname)
          .get()
          .uri(uriBuilder -> uriBuilder
              .path(RequestGTMappings.WATCHLIST_MAP + "/{idWatchlist}/search")
              .queryParam("assetclassType", AssetclassType.CURRENCY_PAIR)
              .queryParam("name", currencypairFixture.fromCurrency)
              .build(idWatchlist))
          .exchange()
          .expectStatus().isOk()
          .expectBody(SecuritycurrencyLists.class)
          .returnResult()
          .getResponseBody();
      assertNotNull(result);
      List<Currencypair> exactMatches = result.currencypairList.stream()
          .filter(currencypair -> currencypairFixture.fromCurrency.equals(currencypair.getFromCurrency())
              && currencypairFixture.toCurrency.equals(currencypair.getToCurrency()))
          .toList();
      Assertions.assertThat(exactMatches)
          .as("currency pair resolved through REST: %s/%s", currencypairFixture.fromCurrency,
              currencypairFixture.toCurrency)
          .hasSize(1);
      currencypairs.add(exactMatches.getFirst());
    }
    return new SecuritycurrencyLists(securities, currencypairs);
  }

  private void verifyWatchlistThroughRest(WatchlistFixture fixture, Integer idWatchlist) {
    String responseBody = authenticatedClient(fixture.loginNickname)
        .get()
        .uri(RequestGTMappings.WATCHLIST_MAP + "/" + idWatchlist)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
    assertNotNull(responseBody);
    JsonNode response = parseJson(responseBody);

    Set<String> actualSecurities = valuesOf(response.path("securityPositionList"), "isin", "currency");
    Set<String> expectedSecurities = fixture.securities.stream()
        .map(security -> security.isin + "|" + security.currency)
        .collect(Collectors.toSet());
    Set<String> actualCurrencypairs = valuesOf(response.path("currencypairPositionList"), "fromCurrency",
        "toCurrency");
    Set<String> expectedCurrencypairs = fixture.currencyPairs.stream()
        .map(currencypair -> currencypair.fromCurrency + "|" + currencypair.toCurrency)
        .collect(Collectors.toSet());

    Assertions.assertThat(actualSecurities).containsExactlyInAnyOrderElementsOf(expectedSecurities);
    Assertions.assertThat(actualCurrencypairs).containsExactlyInAnyOrderElementsOf(expectedCurrencypairs);

    if (fixture.main) {
      Tenant tenant = authenticatedClient(fixture.loginNickname)
          .get()
          .uri(RequestGTMappings.TENANT_MAP)
          .exchange()
          .expectStatus().isOk()
          .expectBody(Tenant.class)
          .returnResult()
          .getResponseBody();
      assertNotNull(tenant);
      Assertions.assertThat(tenant.getIdWatchlistPerformance()).isEqualTo(idWatchlist);
    }
  }

  private Set<String> valuesOf(JsonNode positions, String firstField, String secondField) {
    List<String> values = new ArrayList<>();
    positions.forEach(position -> {
      JsonNode instrument = position.path("securitycurrency");
      values.add(instrument.path(firstField).asText() + "|" + instrument.path(secondField).asText());
    });
    return Set.copyOf(values);
  }

  private JsonNode parseJson(String responseBody) {
    try {
      return new ObjectMapper().readTree(responseBody);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to parse watchlist REST response", e);
    }
  }

  private List<WatchlistFixture> loadIntegrationWatchlists() throws IOException {
    try (InputStream input = WatchlistResourceTest.class.getResourceAsStream(FIXTURE)) {
      assertNotNull(input, "Missing fixture " + FIXTURE);
      WatchlistFixtureFile fixtureFile = new ObjectMapper().readValue(input, WatchlistFixtureFile.class);
      return fixtureFile.watchlists.stream().filter(watchlist -> "i".equals(watchlist.e2e)).toList();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class WatchlistFixtureFile {
    public List<WatchlistFixture> watchlists;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class WatchlistFixture {
    public String loginNickname;
    public String name;
    public boolean main;
    public List<SecurityFixture> securities;
    public List<CurrencyPairFixture> currencyPairs;
    public String e2e;
  }

  public static class SecurityFixture {
    public String isin;
    public String currency;
  }

  public static class CurrencyPairFixture {
    public String fromCurrency;
    public String toCurrency;
  }
}
