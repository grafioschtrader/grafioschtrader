package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;

/**
 * The stored benchmark of the period performance: a default of the client view that every portfolio inherits, the
 * override of a single portfolio, and that neither is touched by a normal save of tenant or portfolio. It needs the
 * portfolios of {@link PortfolioResourceTest} and removes every assignment again, so the later tests calculate without
 * a benchmark.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class PerformanceBenchmarkResourceTest extends BaseIntegrationTest {

  private static final String SOURCE_PORTFOLIO = "BENCHMARK_SOURCE_PORTFOLIO";
  private static final String SOURCE_TENANT = "BENCHMARK_SOURCE_TENANT";
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
      new ParameterizedTypeReference<>() {
      };

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  private Integer idTenant;
  private Integer idPortfolio;
  private Integer idDefault;
  private Integer idOverride;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    idTenant = (Integer) authenticatedClient(RestTestHelper.USER).get().uri(RequestGTMappings.TENANT_MAP).exchange()
        .expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody().get("idTenant");
    List<Map<String, Object>> portfolios = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant").exchange().expectStatus().isOk().expectBody(JSON_ARRAY)
        .returnResult().getResponseBody();
    assertThat(portfolios).as("run PortfolioResourceTest first").isNotEmpty();
    idPortfolio = (Integer) portfolios.getFirst().get("idPortfolio");
    List<Currencypair> pairs = currencypairJpaRepository.findAll();
    assertThat(pairs).as("the test data carries currency pairs").hasSizeGreaterThanOrEqualTo(2);
    idDefault = pairs.get(0).getIdSecuritycurrency();
    idOverride = pairs.get(1).getIdSecuritycurrency();
    clearBoth();
  }

  @AfterAll
  void tearDown() {
    clearBoth();
  }

  /** Removes both assignments, so that a run which failed halfway does not break the next one. */
  private void clearBoth() {
    delete(idPortfolio);
    delete(null);
  }

  @Test
  @Order(1)
  @DisplayName("Without any assignment neither scope has a benchmark")
  void noBenchmark() {
    get(null).expectStatus().isNoContent();
    get(idPortfolio).expectStatus().isNoContent();
  }

  @Test
  @Order(2)
  @DisplayName("The default of the client view is inherited by a portfolio without its own benchmark")
  void tenantDefaultIsInherited() {
    Map<String, Object> saved = put(null, idDefault);
    assertThat(saved.get("idSecuritycurrency")).isEqualTo(idDefault);
    assertThat(saved.get("source")).isEqualTo(SOURCE_TENANT);

    Map<String, Object> inherited = getBody(idPortfolio);
    assertThat(inherited.get("idSecuritycurrency")).isEqualTo(idDefault);
    assertThat(inherited.get("source")).isEqualTo(SOURCE_TENANT);
  }

  @Test
  @Order(3)
  @DisplayName("A portfolio override wins over the default and leaves the client view alone")
  void portfolioOverride() {
    Map<String, Object> saved = put(idPortfolio, idOverride);
    assertThat(saved.get("source")).isEqualTo(SOURCE_PORTFOLIO);
    assertThat(getBody(idPortfolio).get("idSecuritycurrency")).isEqualTo(idOverride);
    assertThat(getBody(null).get("idSecuritycurrency")).isEqualTo(idDefault);
  }

  @Test
  @Order(4)
  @DisplayName("Saving tenant and portfolio through their own endpoints keeps both benchmarks")
  void normalSaveKeepsTheBenchmark() {
    Map<String, Object> portfolio = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/" + idPortfolio).exchange().expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    portfolio.put("idBenchmark", null);
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PORTFOLIO_MAP).body(portfolio).exchange()
        .expectStatus().isOk();
    Map<String, Object> tenant = authenticatedClient(RestTestHelper.USER).get().uri(RequestGTMappings.TENANT_MAP)
        .exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    tenant.put("idBenchmark", null);
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.TENANT_MAP).body(tenant).exchange()
        .expectStatus().isOk();

    Portfolio stored = portfolioJpaRepository.findByIdTenantAndIdPortfolio(idTenant, idPortfolio);
    assertThat(stored.getIdBenchmark()).isEqualTo(idOverride);
    assertThat(tenantJpaRepository.findById(idTenant).orElseThrow().getIdBenchmark()).isEqualTo(idDefault);
  }

  @Test
  @Order(5)
  @DisplayName("Removing the override restores the inheritance of the default")
  void removingTheOverrideRestoresInheritance() {
    delete(idPortfolio);
    Map<String, Object> inherited = getBody(idPortfolio);
    assertThat(inherited.get("idSecuritycurrency")).isEqualTo(idDefault);
    assertThat(inherited.get("source")).isEqualTo(SOURCE_TENANT);
  }

  @Test
  @Order(6)
  @DisplayName("An unknown or negative instrument and a portfolio of another tenant are refused")
  void invalidAssignmentsAreRefused() {
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP)
        .body(Map.of("idSecuritycurrency", Integer.MAX_VALUE)).exchange().expectStatus().isBadRequest();
    authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP)
        .body(Map.of("idSecuritycurrency", -1)).exchange().expectStatus().isBadRequest();
    portfolioJpaRepository.findAll().stream().filter(p -> !p.getIdTenant().equals(idTenant)).findFirst()
        .ifPresent(foreign -> authenticatedClient(RestTestHelper.USER).put()
            .uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP)
            .body(Map.of("idPortfolio", foreign.getIdPortfolio(), "idSecuritycurrency", idDefault)).exchange()
            .expectStatus().is4xxClientError());
    assertThat(getBody(null).get("idSecuritycurrency")).isEqualTo(idDefault);
  }

  private ResponseSpec get(Integer scope) {
    return authenticatedClient(RestTestHelper.USER).get().uri(uri(scope)).exchange();
  }

  private Map<String, Object> getBody(Integer scope) {
    return get(scope).expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
  }

  private Map<String, Object> put(Integer scope, Integer idSecuritycurrency) {
    Map<String, Object> body = new HashMap<>();
    body.put("idPortfolio", scope);
    body.put("idSecuritycurrency", idSecuritycurrency);
    return authenticatedClient(RestTestHelper.USER).put().uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP).body(body)
        .exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
  }

  private void delete(Integer scope) {
    authenticatedClient(RestTestHelper.USER).delete().uri(uri(scope)).exchange().expectStatus().isNoContent();
  }

  private static String uri(Integer scope) {
    return RequestGTMappings.PERFORMANCE_BENCHMARK_MAP + (scope == null ? "" : "?idPortfolio=" + scope);
  }
}
