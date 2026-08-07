package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.types.Language;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class PortfolioResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/portfolios.json";

  private List<PortfolioFixture> integrationPortfolios;
  private List<TradingPlatformPlan> tradingPlatformPlans;

  @BeforeAll
  void setUp() throws IOException {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    integrationPortfolios = loadIntegrationPortfolios();
    TradingPlatformPlan[] plans = authenticatedClient(RestTestHelper.ADMIN)
        .get()
        .uri(RequestGTMappings.TRADINGPLATFORMPLAND_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(TradingPlatformPlan[].class)
        .returnResult()
        .getResponseBody();
    tradingPlatformPlans = plans == null ? List.of() : Arrays.asList(plans);
  }

  @Test
  @Order(1)
  @DisplayName("Create integration portfolios and their accounts through REST")
  void createPortfoliosAndAccounts() {
    Assertions.assertThat(integrationPortfolios).isNotEmpty();
    integrationPortfolios.forEach(this::createPortfolioAndAccounts);
  }

  private void createPortfolioAndAccounts(PortfolioFixture fixture) {
    Portfolio request = new Portfolio(null, fixture.name, fixture.currency);
    Portfolio portfolio = authenticatedClient(fixture.loginNickname)
        .post()
        .uri(RequestGTMappings.PORTFOLIO_MAP)
        .body(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Portfolio.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(portfolio);
    Assertions.assertThat(portfolio.getIdPortfolio()).isPositive();
    Assertions.assertThat(portfolio.getName()).isEqualTo(fixture.name);
    Assertions.assertThat(portfolio.getCurrency()).isEqualTo(fixture.currency);

    Map<String, Securityaccount> securityaccounts = createSecurityaccounts(fixture, portfolio);
    createCashaccounts(fixture, portfolio, securityaccounts);
    verifyPortfolioThroughRest(fixture, portfolio, securityaccounts);
  }

  private Map<String, Securityaccount> createSecurityaccounts(PortfolioFixture fixture, Portfolio portfolio) {
    Map<String, Securityaccount> createdByName = new HashMap<>();
    for (SecurityAccountFixture accountFixture : fixture.securityAccounts) {
      TradingPlatformPlan plan = findTradingPlatformPlan(accountFixture.tradingPlatformPlan);
      List<SecaccountTradingPeriod> tradingPeriods = createTradingPeriods(accountFixture.tradingPeriods);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("name", accountFixture.name);
      payload.put("portfolio", portfolio);
      payload.put("tradingPlatformPlan", plan);
      payload.put("lowestTransactionCost", accountFixture.lowestTransactionCost);
      payload.put("tradingPeriods", tradingPeriods);

      Securityaccount created = authenticatedClient(fixture.loginNickname)
          .post()
          .uri(RequestGTMappings.SECURITYACCOUNT_MAP)
          .body(payload)
          .exchange()
          .expectStatus().isOk()
          .expectBody(Securityaccount.class)
          .returnResult()
          .getResponseBody();

      assertNotNull(created);
      Assertions.assertThat(created.getIdSecuritycashAccount()).isPositive();
      Assertions.assertThat(created.getName()).isEqualTo(accountFixture.name);
      Assertions.assertThat(created.getLowestTransactionCost()).isEqualTo(accountFixture.lowestTransactionCost);
      Assertions.assertThat(created.getTradingPlatformPlan().getIdTradingPlatformPlan())
          .isEqualTo(plan.getIdTradingPlatformPlan());
      Assertions.assertThat(toTradingPeriodKeys(created.getTradingPeriods()))
          .containsExactlyInAnyOrderElementsOf(toTradingPeriodKeys(tradingPeriods));
      createdByName.put(created.getName(), created);
    }
    return createdByName;
  }

  private void createCashaccounts(PortfolioFixture fixture, Portfolio portfolio,
      Map<String, Securityaccount> securityaccounts) {
    for (CashAccountFixture accountFixture : fixture.cashAccounts) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("name", accountFixture.name);
      payload.put("currency", accountFixture.currency);
      payload.put("portfolio", portfolio);
      if (accountFixture.connectSecurityAccount != null) {
        Securityaccount connectedAccount = securityaccounts.get(accountFixture.connectSecurityAccount);
        assertNotNull(connectedAccount,
            "Unknown connected securities account '" + accountFixture.connectSecurityAccount + "'");
        payload.put("connectIdSecurityaccount", connectedAccount.getIdSecuritycashAccount());
      }

      Cashaccount created = authenticatedClient(fixture.loginNickname)
          .post()
          .uri(RequestGTMappings.CASHACCOUNT_MAP)
          .body(payload)
          .exchange()
          .expectStatus().isOk()
          .expectBody(Cashaccount.class)
          .returnResult()
          .getResponseBody();

      assertNotNull(created);
      Assertions.assertThat(created.getIdSecuritycashAccount()).isPositive();
      Assertions.assertThat(created.getName()).isEqualTo(accountFixture.name);
      Assertions.assertThat(created.getCurrency()).isEqualTo(accountFixture.currency);
      Integer expectedConnection = accountFixture.connectSecurityAccount == null ? null
          : securityaccounts.get(accountFixture.connectSecurityAccount).getIdSecuritycashAccount();
      Assertions.assertThat(created.getConnectIdSecurityaccount()).isEqualTo(expectedConnection);
    }
  }

  private void verifyPortfolioThroughRest(PortfolioFixture fixture, Portfolio portfolio,
      Map<String, Securityaccount> securityaccounts) {
    String responseBody = authenticatedClient(fixture.loginNickname)
        .get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/" + portfolio.getIdPortfolio())
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(responseBody);
    JsonNode response = parseJson(responseBody);
    Assertions.assertThat(response.path("name").asText()).isEqualTo(fixture.name);
    Assertions.assertThat(response.path("currency").asText()).isEqualTo(fixture.currency);
    JsonNode securityaccountList = response.path("securityaccountList");
    JsonNode cashaccountList = response.path("cashaccountList");
    Assertions.assertThat(securityaccountList).hasSize(fixture.securityAccounts.size());
    Assertions.assertThat(cashaccountList).hasSize(fixture.cashAccounts.size());

    Set<String> securityaccountNames = valuesOf(securityaccountList, "name");
    Set<String> cashaccountNames = valuesOf(cashaccountList, "name");
    Assertions.assertThat(securityaccountNames)
        .containsExactlyInAnyOrderElementsOf(fixture.securityAccounts.stream().map(a -> a.name).toList());
    Assertions.assertThat(cashaccountNames)
        .containsExactlyInAnyOrderElementsOf(fixture.cashAccounts.stream().map(a -> a.name).toList());

    fixture.cashAccounts.stream()
        .filter(account -> account.connectSecurityAccount != null)
        .forEach(account -> {
          JsonNode cashaccount = findByName(cashaccountList, account.name);
          Assertions.assertThat(cashaccount.path("connectIdSecurityaccount").asInt())
              .isEqualTo(securityaccounts.get(account.connectSecurityAccount).getIdSecuritycashAccount());
        });
  }

  private TradingPlatformPlan findTradingPlatformPlan(String name) {
    return tradingPlatformPlans.stream()
        .filter(plan -> name.equals(plan.getPlatformPlanNameByLanguage(Language.GERMAN))
            || name.equals(plan.getPlatformPlanNameByLanguage(Language.ENGLISH)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Trading platform plan not found through REST: " + name));
  }

  private List<SecaccountTradingPeriod> createTradingPeriods(List<TradingPeriodFixture> fixtures) {
    List<SecaccountTradingPeriod> periods = new ArrayList<>();
    for (TradingPeriodFixture fixture : fixtures) {
      SecaccountTradingPeriod period = new SecaccountTradingPeriod();
      period.setSpecInvestInstrument(SpecialInvestmentInstruments.valueOf(fixture.specInvestInstrument));
      period.setCategoryType(fixture.categoryType == null ? null : AssetclassType.valueOf(fixture.categoryType));
      period.setDateFrom(LocalDate.parse(fixture.dateFrom));
      periods.add(period);
    }
    return periods;
  }

  private Set<String> toTradingPeriodKeys(List<SecaccountTradingPeriod> periods) {
    return periods.stream()
        .map(period -> period.getSpecInvestInstrument() + "|" + period.getCategoryType() + "|"
            + period.getDateFrom() + "|" + period.getDateTo())
        .collect(Collectors.toSet());
  }

  private Set<String> valuesOf(JsonNode array, String field) {
    List<String> values = new ArrayList<>();
    array.forEach(node -> values.add(node.path(field).asText()));
    return Set.copyOf(values);
  }

  private JsonNode findByName(JsonNode array, String name) {
    for (JsonNode node : array) {
      if (name.equals(node.path("name").asText())) {
        return node;
      }
    }
    throw new AssertionError("Account not returned through REST: " + name);
  }

  private JsonNode parseJson(String responseBody) {
    try {
      return new ObjectMapper().readTree(responseBody);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to parse portfolio REST response", e);
    }
  }

  private List<PortfolioFixture> loadIntegrationPortfolios() throws IOException {
    try (InputStream input = PortfolioResourceTest.class.getResourceAsStream(FIXTURE)) {
      assertNotNull(input, "Missing fixture " + FIXTURE);
      PortfolioFixtureFile fixtureFile = new ObjectMapper().readValue(input, PortfolioFixtureFile.class);
      return fixtureFile.portfolios.stream().filter(portfolio -> "i".equals(portfolio.e2e)).toList();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PortfolioFixtureFile {
    public List<PortfolioFixture> portfolios;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PortfolioFixture {
    public String loginNickname;
    public String name;
    public String currency;
    public List<CashAccountFixture> cashAccounts;
    public List<SecurityAccountFixture> securityAccounts;
    public String e2e;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CashAccountFixture {
    public String name;
    public String currency;
    public String connectSecurityAccount;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SecurityAccountFixture {
    public String name;
    public String tradingPlatformPlan;
    public Float lowestTransactionCost;
    public List<TradingPeriodFixture> tradingPeriods;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TradingPeriodFixture {
    public String specInvestInstrument;
    public String categoryType;
    public String dateFrom;
  }
}
