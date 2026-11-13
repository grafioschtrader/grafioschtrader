package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.types.CostBasisMethod;

/**
 * The cost basis method as a setting: what the tenant and a portfolio accept through REST, and which method the
 * reports then resolve for a security account. It needs the portfolios of {@link PortfolioResourceTest} and leaves
 * tenant and portfolio on average cost, the state the later tests calculate with.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class CostBasisMethodResourceTest extends BaseIntegrationTest {

  private static final String FIELD = "costBasisMethod";
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
      new ParameterizedTypeReference<>() {
      };

  @Autowired
  private CostBasisMethodService costBasisMethodService;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    resetMethods();
  }

  /**
   * Puts the tenant on average cost and removes every portfolio override, so that a run which failed halfway does not
   * break the next one.
   */
  private void resetMethods() {
    Map<String, Object> tenant = getTenant();
    if (!"AVERAGE_COST".equals(tenant.get(FIELD))) {
      tenant.put(FIELD, "AVERAGE_COST");
      putTenant(tenant);
    }
    for (Map<String, Object> listed : getPortfolios()) {
      Map<String, Object> portfolio = getPortfolio((Integer) listed.get("idPortfolio"));
      if (portfolio.get(FIELD) != null) {
        portfolio.put(FIELD, null);
        putPortfolio(portfolio);
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("The options are the enum names in the order of the enum")
  void options() {
    List<Map<String, Object>> options = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.GLOBALPARAMETERS_GT_MAP + "/costbasismethods").exchange().expectStatus().isOk()
        .expectBody(JSON_ARRAY).returnResult().getResponseBody();
    assertNotNull(options);
    assertThat(options).extracting(option -> option.get("key")).containsExactly("AVERAGE_COST", "FIFO", "LIFO");
  }

  @Test
  @Order(2)
  @DisplayName("A tenant stores a valid method, falls back to average cost without one and rejects an unknown one")
  void tenantMethod() {
    Map<String, Object> tenant = getTenant();
    assertThat(tenant.get(FIELD)).isEqualTo("AVERAGE_COST");

    tenant.put(FIELD, "FIFO");
    assertThat(putTenant(tenant).get(FIELD)).isEqualTo("FIFO");
    assertThat(getTenant().get(FIELD)).isEqualTo("FIFO");

    tenant.put(FIELD, "HIFO");
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.TENANT_MAP).body(tenant).exchange()
        .expectStatus().isBadRequest();
    assertThat(getTenant().get(FIELD)).isEqualTo("FIFO");

    tenant.put(FIELD, null);
    assertThat(putTenant(tenant).get(FIELD)).isEqualTo("AVERAGE_COST");

    tenant.remove(FIELD);
    assertThat(putTenant(tenant).get(FIELD)).isEqualTo("AVERAGE_COST");
  }

  @Test
  @Order(3)
  @DisplayName("A portfolio overrides the tenant for its security accounts until the override is removed")
  void portfolioOverride() {
    Map<String, Object> tenant = getTenant();
    Integer idTenant = (Integer) tenant.get("idTenant");
    Map<String, Object> portfolio = getPortfolio(firstPortfolioWithSecurityaccount());
    List<Integer> idsSecurityaccount = idsSecurityaccount(portfolio);
    assertThat(portfolio.get(FIELD)).isNull();
    assertThat(costBasisMethodService.forTenant(idTenant).byIdSecurityaccount())
        .doesNotContainKeys(idsSecurityaccount.toArray(Integer[]::new));

    portfolio.put(FIELD, "LIFO");
    assertThat(putPortfolio(portfolio).get(FIELD)).isEqualTo("LIFO");
    CostBasisMethods methods = costBasisMethodService.forTenant(idTenant);
    assertThat(methods.tenantMethod()).isEqualTo(CostBasisMethod.AVERAGE_COST);
    idsSecurityaccount.forEach(id -> assertThat(methods.forAccount(id)).isEqualTo(CostBasisMethod.LIFO));

    // An explicit average cost is an override too: it does not follow a later change of the tenant
    portfolio.put(FIELD, "AVERAGE_COST");
    putPortfolio(portfolio);
    tenant.put(FIELD, "FIFO");
    putTenant(tenant);
    CostBasisMethods explicit = costBasisMethodService.forTenant(idTenant);
    assertThat(explicit.tenantMethod()).isEqualTo(CostBasisMethod.FIFO);
    idsSecurityaccount.forEach(id -> assertThat(explicit.forAccount(id)).isEqualTo(CostBasisMethod.AVERAGE_COST));

    portfolio.put(FIELD, "HIFO");
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PORTFOLIO_MAP).body(portfolio).exchange()
        .expectStatus().isBadRequest();

    portfolio.put(FIELD, null);
    assertThat(putPortfolio(portfolio).get(FIELD)).isNull();
    CostBasisMethods inherited = costBasisMethodService.forTenant(idTenant);
    idsSecurityaccount.forEach(id -> assertThat(inherited.forAccount(id)).isEqualTo(CostBasisMethod.FIFO));

    tenant.put(FIELD, "AVERAGE_COST");
    putTenant(tenant);
  }

  private Map<String, Object> getTenant() {
    Map<String, Object> tenant = authenticatedClient(RestTestHelper.USER).get().uri(RequestGTMappings.TENANT_MAP)
        .exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertNotNull(tenant);
    return tenant;
  }

  private Map<String, Object> putTenant(Map<String, Object> tenant) {
    Map<String, Object> updated = authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.TENANT_MAP)
        .body(tenant).exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertNotNull(updated);
    return updated;
  }

  private Map<String, Object> getPortfolio(Integer idPortfolio) {
    Map<String, Object> portfolio = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/" + idPortfolio).exchange().expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertNotNull(portfolio);
    return portfolio;
  }

  private Map<String, Object> putPortfolio(Map<String, Object> portfolio) {
    Map<String, Object> updated = authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PORTFOLIO_MAP)
        .body(portfolio).exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertNotNull(updated);
    return updated;
  }

  private List<Map<String, Object>> getPortfolios() {
    List<Map<String, Object>> portfolios = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant").exchange().expectStatus().isOk().expectBody(JSON_ARRAY)
        .returnResult().getResponseBody();
    assertNotNull(portfolios);
    return portfolios;
  }

  private Integer firstPortfolioWithSecurityaccount() {
    return getPortfolios().stream().filter(portfolio -> !idsSecurityaccount(portfolio).isEmpty())
        .map(portfolio -> (Integer) portfolio.get("idPortfolio")).findFirst()
        .orElseThrow(() -> new AssertionError("No portfolio with a security account, run PortfolioResourceTest first"));
  }

  @SuppressWarnings("unchecked")
  private List<Integer> idsSecurityaccount(Map<String, Object> portfolio) {
    List<Map<String, Object>> securityaccounts = (List<Map<String, Object>>) portfolio.get("securityaccountList");
    return securityaccounts == null ? List.of()
        : securityaccounts.stream().map(account -> (Integer) account.get("idSecuritycashAccount")).toList();
  }
}
