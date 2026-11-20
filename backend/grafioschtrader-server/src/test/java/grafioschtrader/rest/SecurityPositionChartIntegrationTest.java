package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.UserJpaRepository;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.reports.SecurityPositionSeriesReport;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;

/**
 * Checks the security position chart over HTTP on the populated fixture of the test database: for every open position
 * of the tenant of {@code alledit}, in each of the three scopes (tenant, portfolio, security account), the value of the
 * chart on a past valuation day equals the holding value of that scope on the same day and from the same price source.
 * The daily-values queries of the chart return the value of {@code getDailySecurityValuesByTenant} for the same
 * security, so a later fix of the value formula in one place is noticed in the other. A portfolio or security account
 * of another tenant is refused, and so is a request that names both scope parameters.
 *
 * <p>
 * Run explicitly with {@code -Dgt.report.integration=true} after a roundtrip: the security transactions are created by
 * the Playwright specs from 175 on (for {@code alledit} spec 255, Banco Santander in the account CT), which run after
 * the last numbered {@code ResourceTestSuite_*} phase. Stop the e2e backend first so only this test application owns
 * {@code grafioschtrader_t}, like {@link SecurityPeriodPerformanceIntegrationTest}. The hand-calculated cases of the
 * series are covered by {@code SecurityPositionSeriesTest} and {@code MoneyDrawdownTest} in grafioschtrader-common.
 * </p>
 */
@GTIntegrationTestContext
@EnabledIfSystemProperty(named = "gt.report.integration", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class SecurityPositionChartIntegrationTest extends BaseIntegrationTest {

  /** The chart and the holding summary each round once to the precision of the currency. */
  private static final double VALUE_TOLERANCE = 0.02;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private SecurityPositionSeriesReport securityPositionSeriesReport;

  private Integer idTenant;
  private LocalDate today;

  /** One scope of the chart: its request parameter and the holding summary of the same scope. */
  private record Scope(String name, String chartParam, String summaryUri) {
  }

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    idTenant = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser)
        .orElseThrow().getIdTenant();
    today = LocalDate.now(ZoneId.of("UTC"));
  }

  @Test
  @DisplayName("The chart value equals the holding value of each scope on the same day and price source")
  void valueAgreesWithHoldingsInEveryScope() throws Exception {
    List<Scope> scopes = scopes();
    int compared = 0;
    for (Scope scope : scopes) {
      Map<Integer, JsonNode> positions = openPositions(scope, today);
      for (Integer idSecuritycurrency : positions.keySet()) {
        compared += compareOnPastDay(scope, idSecuritycurrency) ? 1 : 0;
      }
    }
    assertTrue(compared > 0, "An open position of alledit valued from a closing price is required (Playwright 255)");
  }

  @Test
  @DisplayName("The daily-values queries of the chart agree with getDailySecurityValuesByTenant")
  void dailyValuesAgreeWithPeriodPerformanceQuery() throws Exception {
    Map<Integer, JsonNode> positions = openPositions(scopes().getFirst(), today);
    assertFalse(positions.isEmpty(), "An open position of alledit is required (Playwright 255)");
    List<Integer> idsSecurityaccount = securityaccountJpaRepository.findByIdTenant(idTenant).stream()
        .map(Securityaccount::getIdSecuritycashAccount).toList();
    for (Integer idSecuritycurrency : positions.keySet()) {
      JsonNode chart = chart(idSecuritycurrency, "");
      if (chart.path("marginInstrument").asBoolean()) {
        // The chart values a margin instrument from its open positions, not from these queries
        continue;
      }
      LocalDate from = LocalDate.parse(chart.path("phaseFrom").asText());
      Map<String, double[]> reference = new HashMap<>();
      for (Object[] row : holdSecurityaccountSecurityJpaRepository.getDailySecurityValuesByTenant(idTenant, from,
          today)) {
        if (idSecuritycurrency.equals(((Number) row[0]).intValue())) {
          reference.put(row[1].toString(), new double[] { number(row[2]), number(row[3]) });
        }
      }
      assertFalse(reference.isEmpty(), "Holding rows of security " + idSecuritycurrency);
      assertSameValues(reference,
          holdSecurityaccountSecurityJpaRepository.getDailyValuesOfSecurityByTenant(idTenant, idSecuritycurrency,
              from, today),
          "tenant query, security " + idSecuritycurrency);
      assertSameValues(reference,
          holdSecurityaccountSecurityJpaRepository.getDailyValuesOfSecurityBySecurityaccounts(idTenant,
              idsSecurityaccount, idSecuritycurrency, from, today),
          "security account query, security " + idSecuritycurrency);
    }
  }

  @Test
  @DisplayName("A portfolio or a security account of another tenant is refused")
  void foreignScopeRefused() {
    // Called on the report, not over HTTP: the REST handler would count a security breach against the test user
    Portfolio foreignPortfolio = portfolioJpaRepository.findAll().stream()
        .filter(p -> !p.getIdTenant().equals(idTenant)).findFirst().orElseThrow();
    Securityaccount foreignAccount = securityaccountJpaRepository.findAll().stream()
        .filter(s -> !s.getIdTenant().equals(idTenant)).findFirst().orElseThrow();
    Integer idSecuritycurrency = anySecurity();
    assertThrows(SecurityException.class, () -> securityPositionSeriesReport.getSeries(idTenant, idSecuritycurrency,
        foreignPortfolio.getIdPortfolio(), null, null, today));
    assertThrows(SecurityException.class, () -> securityPositionSeriesReport.getSeries(idTenant, idSecuritycurrency,
        null, foreignAccount.getIdSecuritycashAccount(), null, today));
  }

  @Test
  @DisplayName("A request with both a portfolio and a security account is rejected")
  void bothScopeParametersRejected() {
    Securityaccount account = securityaccountJpaRepository.findByIdTenant(idTenant).getFirst();
    Integer idSecuritycurrency = anySecurity();
    authenticatedClient(RestTestHelper.ALLEDIT).get()
        .uri(RequestGTMappings.HOLDING_MAP + "/securityposition/" + idSecuritycurrency + "?idPortfolio="
            + account.getPortfolio().getIdPortfolio() + "&idSecurityaccount=" + account.getIdSecuritycashAccount())
        .exchange().expectStatus().isBadRequest();
    assertThrows(DataViolationException.class, () -> securityPositionSeriesReport.getSeries(idTenant,
        idSecuritycurrency, account.getPortfolio().getIdPortfolio(), account.getIdSecuritycashAccount(), null, today));
  }

  /**
   * Compares the chart of one security in one scope with the holding summary of the same scope on the last point of
   * the chart before today. A day before today is valued from its closing price in both reports; a summary row priced
   * with an intraday last price is a different price source and is left out, and so is a margin instrument, whose chart
   * value is entry value plus open result while the holding summary shows the open result alone.
   *
   * @return whether a comparison was made
   */
  private boolean compareOnPastDay(Scope scope, Integer idSecuritycurrency) throws Exception {
    JsonNode chart = chart(idSecuritycurrency, scope.chartParam());
    JsonNode dates = chart.path("dates");
    int index = dates.size() - 1;
    while (index >= 0 && !LocalDate.parse(dates.get(index).asText()).isBefore(today)) {
      index--;
    }
    if (index < 0 || chart.path("phaseTo").isTextual() || chart.path("marginInstrument").asBoolean()) {
      return false;
    }
    LocalDate day = LocalDate.parse(dates.get(index).asText());
    JsonNode chartValue = chart.path("security").path("value").get(index);
    JsonNode holding = openPositions(scope, day).get(idSecuritycurrency);
    if (holding == null || chartValue == null || chartValue.isNull()
        || "INTRADAY".equals(holding.path("closePriceOrigin").asText())
        || holding.path("priceMissing").asBoolean(false)) {
      return false;
    }
    assertEquals(holding.path("valueSecurity").asDouble(), chartValue.asDouble(), VALUE_TOLERANCE,
        scope.name() + ", security " + idSecuritycurrency + ", " + day);
    return true;
  }

  /** The tenant, every portfolio and every security account of the tenant of alledit. */
  private List<Scope> scopes() {
    String params = "/currency?includeClosedPosition=false&untilDate=";
    List<Scope> scopes = new ArrayList<>();
    scopes.add(new Scope("tenant", "", RequestGTMappings.SECURITYACCOUNT_MAP + "/tenantsecurityaccountsummary"
        + params));
    for (Portfolio portfolio : portfolioJpaRepository.findByIdTenantOrderByName(idTenant)) {
      scopes.add(new Scope("portfolio " + portfolio.getName(), "?idPortfolio=" + portfolio.getIdPortfolio(),
          RequestGTMappings.SECURITYACCOUNT_MAP + "/" + portfolio.getIdPortfolio() + "/portfoliosecurityaccountsummary"
              + params));
    }
    for (Securityaccount account : securityaccountJpaRepository.findByIdTenant(idTenant)) {
      scopes.add(new Scope("security account " + account.getName(),
          "?idSecurityaccount=" + account.getIdSecuritycashAccount(), RequestGTMappings.SECURITYACCOUNT_MAP + "/"
              + account.getIdSecuritycashAccount() + "/securityaccountsummary" + params));
    }
    return scopes;
  }

  /** The open positions of the holding summary of a scope on a day, by security. */
  private Map<Integer, JsonNode> openPositions(Scope scope, LocalDate untilDate) throws Exception {
    Map<Integer, JsonNode> positions = new LinkedHashMap<>();
    JsonNode grand = get(scope.summaryUri() + untilDate);
    for (JsonNode group : grand.path("securityPositionGroupSummaryList")) {
      for (JsonNode position : group.path("securityPositionSummaryList")) {
        if (Math.abs(position.path("units").asDouble()) > 1e-9) {
          positions.put(position.path("security").path("idSecuritycurrency").asInt(), position);
        }
      }
    }
    return positions;
  }

  /** Any security of an open position of the tenant; the scope checks fail before the security is read. */
  private Integer anySecurity() {
    try {
      return openPositions(scopes().getFirst(), today).keySet().stream().findFirst().orElseThrow();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode chart(Integer idSecuritycurrency, String scopeParam) throws Exception {
    JsonNode chart = get(RequestGTMappings.HOLDING_MAP + "/securityposition/" + idSecuritycurrency + scopeParam);
    assertNotNull(chart.path("phaseFrom").textValue(), "phase of security " + idSecuritycurrency + scopeParam);
    return chart;
  }

  /** Each row of a daily-values query of the chart must carry the units and the value of the reference row. */
  private static void assertSameValues(Map<String, double[]> reference, List<Object[]> rows, String message) {
    assertEquals(reference.size(), rows.size(), message + ": number of days");
    for (Object[] row : rows) {
      double[] expected = reference.get(row[0].toString());
      assertNotNull(expected, message + ": day " + row[0]);
      assertEquals(expected[0], number(row[1]), 1e-6, message + ": units on " + row[0]);
      assertEquals(expected[1], number(row[2]), 1e-6, message + ": value on " + row[0]);
    }
  }

  private static double number(Object value) {
    return value == null ? 0 : ((Number) value).doubleValue();
  }

  private JsonNode get(String uri) throws Exception {
    String json = authenticatedClient(RestTestHelper.ALLEDIT).get().uri(uri).exchange().expectStatus().isOk()
        .expectBody(String.class).returnResult().getResponseBody();
    return MAPPER.readTree(json);
  }
}
