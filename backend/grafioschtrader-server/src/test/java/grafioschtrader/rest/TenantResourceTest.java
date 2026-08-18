package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.Tenant;

@TestInstance(Lifecycle.PER_CLASS)
class TenantResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/users.json";

  private List<TenantEditFixture> tenantEdits;
  private List<ImportTransactionPlatform> importTransactionPlatforms;

  @BeforeAll
  void setUp() throws IOException {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    tenantEdits = loadIntegrationTenantEdits();

    ImportTransactionPlatform[] platforms = authenticatedClient(RestTestHelper.ALLEDIT)
        .get()
        .uri(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ImportTransactionPlatform[].class)
        .returnResult()
        .getResponseBody();
    importTransactionPlatforms = platforms == null ? List.of() : Arrays.asList(platforms);
  }

  @Test
  @DisplayName("Update integration tenants with country and Grafioschtrader import platform through REST")
  void updateTenantSettings() {
    Assertions.assertThat(tenantEdits).isNotEmpty();
    tenantEdits.forEach(this::updateTenantSettings);
  }

  private void updateTenantSettings(TenantEditFixture fixture) {
    Tenant tenant = getTenant(fixture.loginNickname);
    String expectedTenantName = tenant.getTenantName();
    String expectedCurrency = tenant.getCurrency();
    boolean expectedExcludeDivTax = tenant.isExcludeDivTax();

    String countryCode = resolveCountryCode(fixture.country);
    ImportTransactionPlatform platform = resolveImportTransactionPlatform(fixture.idGtImportPlatform);
    tenant.setCountry(countryCode);
    tenant.setIdGtImportPlatform(platform.getIdTransactionImportPlatform());

    Tenant updated = authenticatedClient(fixture.loginNickname)
        .put()
        .uri(RequestGTMappings.TENANT_MAP)
        .body(tenant)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Tenant.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(updated);
    assertTenantSettings(updated, fixture, countryCode, platform, expectedTenantName, expectedCurrency,
        expectedExcludeDivTax);
    assertTenantSettings(getTenant(fixture.loginNickname), fixture, countryCode, platform, expectedTenantName,
        expectedCurrency, expectedExcludeDivTax);
  }

  private Tenant getTenant(String loginNickname) {
    Tenant tenant = authenticatedClient(loginNickname)
        .get()
        .uri(RequestGTMappings.TENANT_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Tenant.class)
        .returnResult()
        .getResponseBody();
    assertNotNull(tenant);
    return tenant;
  }

  private void assertTenantSettings(Tenant tenant, TenantEditFixture fixture, String countryCode,
      ImportTransactionPlatform platform, String expectedTenantName, String expectedCurrency,
      boolean expectedExcludeDivTax) {
    Assertions.assertThat(tenant.getCountry()).as("country for %s", fixture.loginNickname).isEqualTo(countryCode);
    Assertions.assertThat(tenant.getIdGtImportPlatform()).as("GT import platform for %s", fixture.loginNickname)
        .isEqualTo(platform.getIdTransactionImportPlatform());
    Assertions.assertThat(tenant.getTenantName()).isEqualTo(expectedTenantName);
    Assertions.assertThat(tenant.getCurrency()).isEqualTo(expectedCurrency);
    Assertions.assertThat(tenant.isExcludeDivTax()).isEqualTo(expectedExcludeDivTax);
  }

  private String resolveCountryCode(String country) {
    String normalizedCountry = country.toUpperCase(Locale.ROOT);
    if (normalizedCountry.length() == 2 && Arrays.asList(Locale.getISOCountries()).contains(normalizedCountry)) {
      return normalizedCountry;
    }
    return Arrays.stream(Locale.getISOCountries())
        .filter(countryCode -> Locale.of("", countryCode).getDisplayCountry(Locale.ENGLISH).equals(country))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Country not found: " + country));
  }

  private ImportTransactionPlatform resolveImportTransactionPlatform(String name) {
    List<ImportTransactionPlatform> matches = importTransactionPlatforms.stream()
        .filter(platform -> name.equals(platform.getName()))
        .toList();
    Assertions.assertThat(matches).as("import transaction platform resolved by name: %s", name).hasSize(1);
    return matches.getFirst();
  }

  private List<TenantEditFixture> loadIntegrationTenantEdits() throws IOException {
    try (InputStream input = TenantResourceTest.class.getResourceAsStream(FIXTURE)) {
      assertNotNull(input, "Missing fixture " + FIXTURE);
      JsonNode users = new ObjectMapper().readTree(input);
      List<TenantEditFixture> fixtures = new ArrayList<>();
      for (JsonNode user : users) {
        JsonNode tenantEdit = user.path("tenantEdit");
        if ("i".equals(user.path("e2e").asText()) && tenantEdit.isObject()) {
          fixtures.add(new TenantEditFixture(user.required("nickname").asText(),
              tenantEdit.required("country").asText(), tenantEdit.required("idGtImportPlatform").asText()));
        }
      }
      return fixtures;
    }
  }

  private record TenantEditFixture(String loginNickname, String country, String idGtImportPlatform) {
  }
}
