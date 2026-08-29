package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.Tenant;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class TenantResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/users.json";

  private List<TenantEditFixture> tenantEdits;

  @BeforeAll
  void setUp() throws IOException {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    tenantEdits = loadIntegrationTenantEdits();
  }

  @Test
  @Order(1)
  @DisplayName("Update integration tenants with country and the Grafioschtrader import template opt-in through REST")
  void updateTenantSettings() {
    Assertions.assertThat(tenantEdits).isNotEmpty();
    tenantEdits.forEach(this::updateTenantSettings);
  }

  @Test
  @Order(2)
  @DisplayName("Personal data export disables and restores foreign-key checks around its inserts")
  void exportPersonalDataWrapsForeignKeyChecks() throws IOException {
    byte[] zip = authenticatedClient(RestTestHelper.USER).get()
        .uri(RequestGTMappings.TENANT_MAP + "/exportpersonaldataaszip").exchange().expectStatus().isOk()
        .expectBody(byte[].class).returnResult().getResponseBody();
    assertNotNull(zip);

    String sql = readZipEntry(zip, "gt_data.sql");
    int rememberChecks = sql.indexOf("SET @old_foreign_key_checks = @@SESSION.foreign_key_checks;");
    int disableChecks = sql.indexOf("SET SESSION foreign_key_checks = 0;");
    int firstInsert = sql.indexOf("INSERT INTO");
    int restoreChecks = sql.lastIndexOf("SET SESSION foreign_key_checks = @old_foreign_key_checks;");
    int restoreSqlMode = sql.lastIndexOf("SET SESSION sql_mode = @old_sql_mode;");

    Assertions.assertThat(rememberChecks).isGreaterThanOrEqualTo(0).isLessThan(disableChecks);
    Assertions.assertThat(disableChecks).isLessThan(firstInsert);
    Assertions.assertThat(firstInsert).isGreaterThanOrEqualTo(0).isLessThan(restoreChecks);
    Assertions.assertThat(restoreChecks).isLessThan(restoreSqlMode);
  }

  private void updateTenantSettings(TenantEditFixture fixture) {
    Tenant tenant = getTenant(fixture.loginNickname);
    String expectedTenantName = tenant.getTenantName();
    String expectedCurrency = tenant.getCurrency();
    boolean expectedExcludeDivTax = tenant.isExcludeDivTax();

    String countryCode = resolveCountryCode(fixture.country);
    tenant.setCountry(countryCode);
    tenant.setUseGtImportTemplates(fixture.useGtImportTemplates);

    Tenant updated = authenticatedClient(fixture.loginNickname).put().uri(RequestGTMappings.TENANT_MAP).body(tenant)
        .exchange().expectStatus().isOk().expectBody(Tenant.class).returnResult().getResponseBody();

    assertNotNull(updated);
    assertTenantSettings(updated, fixture, countryCode, expectedTenantName, expectedCurrency, expectedExcludeDivTax);
    assertTenantSettings(getTenant(fixture.loginNickname), fixture, countryCode, expectedTenantName, expectedCurrency,
        expectedExcludeDivTax);
  }

  private Tenant getTenant(String loginNickname) {
    Tenant tenant = authenticatedClient(loginNickname).get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus()
        .isOk().expectBody(Tenant.class).returnResult().getResponseBody();
    assertNotNull(tenant);
    return tenant;
  }

  private void assertTenantSettings(Tenant tenant, TenantEditFixture fixture, String countryCode,
      String expectedTenantName, String expectedCurrency, boolean expectedExcludeDivTax) {
    Assertions.assertThat(tenant.getCountry()).as("country for %s", fixture.loginNickname).isEqualTo(countryCode);
    Assertions.assertThat(tenant.isUseGtImportTemplates())
        .as("Grafioschtrader import template opt-in for %s", fixture.loginNickname)
        .isEqualTo(fixture.useGtImportTemplates);
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
        .filter(countryCode -> Locale.of("", countryCode).getDisplayCountry(Locale.ENGLISH).equals(country)).findFirst()
        .orElseThrow(() -> new AssertionError("Country not found: " + country));
  }

  private String readZipEntry(byte[] zip, String expectedEntry) throws IOException {
    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zipInput.getNextEntry()) != null) {
        if (expectedEntry.equals(entry.getName())) {
          return new String(zipInput.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    throw new AssertionError("ZIP entry not found: " + expectedEntry);
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
              tenantEdit.required("country").asText(), tenantEdit.required("useGtImportTemplates").asBoolean()));
        }
      }
      return fixtures;
    }
  }

  private record TenantEditFixture(String loginNickname, String country, boolean useGtImportTemplates) {
  }
}
