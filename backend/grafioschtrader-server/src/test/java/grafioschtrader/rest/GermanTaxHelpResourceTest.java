package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The access rules of the German tax help: which tenant and which tax year {@code GET /api/portfolio/taxgermany/{y}}
 * accepts, and who may set the base interest rate of a tax year through {@code PUT
 * /api/taxdata/year/{id}/fundbaserate}. It needs the registered users of {@link UserResourceTest} and the tenant of
 * {@code limit1}, the only one that reports in euro with the country Germany. The base interest rate it sets is
 * restored. Missing tax countries and years are created through the REST API and removed afterwards.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class GermanTaxHelpResourceTest extends BaseIntegrationTest {

  private static final String TAX_GERMANY = RequestGTMappings.PORTFOLIO_MAP + "/taxgermany/";
  private static final String TAX_YEAR = TaxDataResource.TAX_DATA_MAP + "/year";
  /** The country the test creates when the tax data has none besides Germany. */
  private static final String OTHER_COUNTRY = "CH";
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY = new ParameterizedTypeReference<>() {
  };
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
  };

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Integer idCreatedGermanCountry;
  private Integer idCreatedGermanYear;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    Map<String, Object> germany = countries().stream().filter(country -> "DE".equals(country.get("countryCode")))
        .findFirst().orElse(null);
    Integer idTaxCountry;
    if (germany == null) {
      idCreatedGermanCountry = createCountry("DE");
      idTaxCountry = idCreatedGermanCountry;
    } else {
      idTaxCountry = (Integer) germany.get("idTaxCountry");
    }
    if (germany == null || ((List<?>) germany.get("taxYears")).isEmpty()) {
      Map<String, Object> year = authenticatedClient(RestTestHelper.ADMIN).post().uri(TAX_YEAR)
          .body(Map.of("idTaxCountry", idTaxCountry, "taxYear", 2018)).exchange().expectStatus().isOk()
          .expectBody(JSON_OBJECT).returnResult().getResponseBody();
      assertNotNull(year);
      idCreatedGermanYear = (Integer) year.get("idTaxYear");
    }
  }

  @AfterAll
  void removeCreatedTaxData() {
    try {
      if (idCreatedGermanYear != null) {
        authenticatedClient(RestTestHelper.ADMIN).delete().uri(TAX_YEAR + "/" + idCreatedGermanYear).exchange()
            .expectStatus().isOk();
      }
    } finally {
      if (idCreatedGermanCountry != null) {
        authenticatedClient(RestTestHelper.ADMIN).delete()
            .uri(TaxDataResource.TAX_DATA_MAP + "/country/" + idCreatedGermanCountry).exchange().expectStatus().isOk();
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("A tenant that does not report in euro or does not have the country Germany is rejected")
  void tenantNotEligible() {
    int year = LocalDate.now().getYear();
    // USD and the United States on the tenant of 'user', CHF and Switzerland on the tenant of 'limit2'. The
    // administrator has no access to the portfolio endpoints at all.
    for (String nickname : List.of(RestTestHelper.USER, RestTestHelper.LIMIT2)) {
      authenticatedClient(nickname).get().uri(TAX_GERMANY + year).exchange().expectStatus().isBadRequest();
    }
  }

  @Test
  @Order(2)
  @DisplayName("A euro tenant of Germany gets the report for 2018 up to the current year and nothing outside")
  void taxYearRange() {
    int currentYear = LocalDate.now().getYear();
    for (int year : new int[] { 2018, currentYear }) {
      Map<String, Object> report = authenticatedClient(RestTestHelper.LIMIT1).get().uri(TAX_GERMANY + year).exchange()
          .expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
      assertNotNull(report, "report of " + year);
    }
    for (int year : new int[] { 2017, currentYear + 1 }) {
      authenticatedClient(RestTestHelper.LIMIT1).get().uri(TAX_GERMANY + year).exchange().expectStatus().isBadRequest();
    }
  }

  @Test
  @Order(3)
  @DisplayName("An administrator sets and clears the base interest rate of a tax year of Germany")
  void administratorSetsBaseRate() {
    Map<String, Object> germany = taxYearOf("DE");
    Integer idTaxYear = (Integer) germany.get("idTaxYear");
    Object original = germany.get("fundBaseRate");
    try {
      assertThat(putBaseRate(RestTestHelper.ADMIN, idTaxYear, 2.53).expectStatus().isOk().expectBody(JSON_OBJECT)
          .returnResult().getResponseBody()).containsEntry("fundBaseRate", 2.53);
      assertThat(taxYearOf("DE").get("fundBaseRate")).isEqualTo(2.53);

      assertThat(putBaseRate(RestTestHelper.ADMIN, idTaxYear, null).expectStatus().isOk().expectBody(JSON_OBJECT)
          .returnResult().getResponseBody()).containsEntry("fundBaseRate", null);

      putBaseRate(RestTestHelper.ADMIN, idTaxYear, 25.0).expectStatus().isBadRequest();
      assertThat(taxYearOf("DE").get("fundBaseRate")).isNull();
    } finally {
      putBaseRate(RestTestHelper.ADMIN, idTaxYear, original == null ? null : ((Number) original).doubleValue());
    }
  }

  @Test
  @Order(4)
  @DisplayName("A user without the role administrator cannot change the base interest rate")
  void userWithoutAdministratorRole() {
    Map<String, Object> germany = taxYearOf("DE");
    Integer idTaxYear = (Integer) germany.get("idTaxYear");
    Object before = germany.get("fundBaseRate");
    // The rejection is a SecurityException, which the error handler counts as a security breach of the caller. Five
    // breaches lock a user out, and every later phase and Playwright spec logs in as these fixture users, so the
    // counter is put back.
    Integer breachesBefore = securityBreachCount(RestTestHelper.USER);
    try {
      putBaseRate(RestTestHelper.USER, idTaxYear, 9.0).expectStatus().isUnauthorized();
    } finally {
      jdbcTemplate.update("UPDATE user SET security_breach_count = ? WHERE nickname = ?", breachesBefore,
          RestTestHelper.USER);
    }
    assertThat(taxYearOf("DE").get("fundBaseRate")).isEqualTo(before);
  }

  private Integer securityBreachCount(String nickname) {
    return jdbcTemplate.queryForObject("SELECT security_breach_count FROM user WHERE nickname = ?", Integer.class,
        nickname);
  }

  @Test
  @Order(5)
  @DisplayName("The base interest rate of a tax year of another country is rejected")
  void otherCountryRejected() {
    // A country this test creates is deleted again, because the tax data Playwright spec creates its own country
    // later and rejects a duplicate.
    Integer existing = idTaxCountryOtherThanGermany();
    Integer idCreatedCountry = existing == null ? createCountry(OTHER_COUNTRY) : null;
    Integer idTaxCountry = existing != null ? existing : idCreatedCountry;
    Integer idTaxYear = null;
    try {
      Map<String, Object> created = authenticatedClient(RestTestHelper.ADMIN).post().uri(TAX_YEAR)
          .body(Map.of("idTaxCountry", idTaxCountry, "taxYear", 1999)).exchange().expectStatus().isOk()
          .expectBody(JSON_OBJECT).returnResult().getResponseBody();
      assertNotNull(created);
      idTaxYear = (Integer) created.get("idTaxYear");
      putBaseRate(RestTestHelper.ADMIN, idTaxYear, 2.0).expectStatus().isBadRequest();
      assertThat(taxYearById(idTaxYear).get("fundBaseRate")).isNull();
    } finally {
      if (idTaxYear != null) {
        authenticatedClient(RestTestHelper.ADMIN).delete().uri(TAX_YEAR + "/" + idTaxYear).exchange().expectStatus()
            .isOk();
      }
      if (idCreatedCountry != null) {
        authenticatedClient(RestTestHelper.ADMIN).delete()
            .uri(TaxDataResource.TAX_DATA_MAP + "/country/" + idCreatedCountry).exchange().expectStatus().isOk();
      }
    }
  }

  private Integer createCountry(String countryCode) {
    Map<String, Object> country = authenticatedClient(RestTestHelper.ADMIN).post()
        .uri(TaxDataResource.TAX_DATA_MAP + "/country").body(Map.of("countryCode", countryCode)).exchange()
        .expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertNotNull(country);
    return (Integer) country.get("idTaxCountry");
  }

  private RestTestClient.ResponseSpec putBaseRate(String nickname, Integer idTaxYear, Double rate) {
    Map<String, Object> request = new HashMap<>();
    request.put("fundBaseRate", rate);
    return authenticatedClient(nickname).put().uri(TAX_YEAR + "/" + idTaxYear + "/fundbaserate").body(request)
        .exchange();
  }

  /** The newest tax year of a country, read from the tax data tree. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> taxYearOf(String countryCode) {
    return countries().stream().filter(country -> countryCode.equals(country.get("countryCode")))
        .flatMap(country -> ((List<Map<String, Object>>) country.get("taxYears")).stream())
        .max((a, b) -> ((Number) a.get("taxYear")).intValue() - ((Number) b.get("taxYear")).intValue())
        .orElseThrow(() -> new AssertionError("No tax year of " + countryCode + ", check the test setup"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> taxYearById(Integer idTaxYear) {
    return countries().stream().flatMap(country -> ((List<Map<String, Object>>) country.get("taxYears")).stream())
        .filter(year -> idTaxYear.equals(year.get("idTaxYear"))).findFirst().orElseThrow();
  }

  /** The id of a tax country other than Germany, or null when the tax data holds only Germany. */
  private Integer idTaxCountryOtherThanGermany() {
    return countries().stream().filter(country -> !"DE".equals(country.get("countryCode")))
        .map(country -> (Integer) country.get("idTaxCountry")).findFirst().orElse(null);
  }

  private List<Map<String, Object>> countries() {
    List<Map<String, Object>> countries = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(TaxDataResource.TAX_DATA_MAP + "/tree").exchange().expectStatus().isOk().expectBody(JSON_ARRAY)
        .returnResult().getResponseBody();
    assertNotNull(countries);
    return countries;
  }
}
