package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import org.springframework.test.web.servlet.client.RestTestClient;

import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.integration.entities.Tenant;
import grafiosch.integration.repository.TenantJpaRepository;
import grafiosch.integration.rest.RequestIntegrationMappings;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.types.TenantAccessLevel;

/**
 * The library half of tenant context authorization: which tenant a token may address, and which operations belong to
 * the user's own tenant regardless of what the other tenant is.
 *
 * <p>
 * Nothing here knows about simulation environments - those are an application concept contributed through
 * {@code ITenantAccessExtension}. Without such a bean the library grants exactly the home tenant and explicit
 * {@code tenant_access} grants, which is what this suite pins down.
 * </p>
 *
 * <p>
 * The tenants are created directly through the repository. Going through a registration would give the users a home
 * tenant this test is not allowed to damage, while a bare row can be removed again in the teardown.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class TenantContextAuthorizationTest extends BaseIntegrationTest {

  private static final ParameterizedTypeReference<Map<String, String>> MAP_TYPE = new ParameterizedTypeReference<>() {
  };

  @Autowired
  private TenantJpaRepository tenantJpaRepository;
  @Autowired
  private UserJpaRepository userJpaRepository;
  @Autowired
  private TenantAccessJpaRepository tenantAccessJpaRepository;

  private User owner;
  private Integer ownIdTenant;
  /** A tenant the owner has nothing to do with, used both as a foreign target and as a grant target. */
  private Integer otherIdTenant;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    owner = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser).orElseThrow();
    ownIdTenant = owner.getIdTenant();
    Tenant other = new Tenant();
    other.setTenantName("Ctx other");
    other.setCreateIdUser(owner.getIdUser());
    otherIdTenant = tenantJpaRepository.save(other).getIdTenant();
  }

  @AfterAll
  void tearDown() {
    tenantAccessJpaRepository.findByIdTenant(otherIdTenant).forEach(tenantAccessJpaRepository::delete);
    tenantJpaRepository.findById(otherIdTenant).ifPresent(tenantJpaRepository::delete);
  }

  /** A client whose token names the given tenant, without going through the switch endpoint. */
  private RestTestClient clientInTenant(Integer idTenant) {
    return restTestClient.mutate()
        .defaultHeader("x-auth-token", jwtTokenHandler.createTokenForUser(owner, 120, idTenant)).build();
  }

  @Test
  @Order(1)
  @DisplayName("The home tenant is always reachable and reports its own read-only state")
  void homeTenantResolvesFromItsOwnFlag() {
    Map<String, String> response = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestIntegrationMappings.TENANT_MAP + "/switchto/" + ownIdTenant).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody();
    assertThat(response).containsEntry("readOnly", String.valueOf(owner.isHomeTenantReadOnly()));
  }

  @Test
  @Order(2)
  @DisplayName("A tenant without a grant is forbidden, for reads as much as for writes")
  void tenantWithoutGrantIsForbidden() {
    authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestIntegrationMappings.TENANT_MAP + "/switchto/" + otherIdTenant).exchange().expectStatus()
        .isForbidden();
    clientInTenant(otherIdTenant).get().uri(RequestIntegrationMappings.TENANT_MAP + "/deletion-eligibility").exchange()
        .expectStatus().isForbidden();
    clientInTenant(otherIdTenant).post().uri(RequestIntegrationMappings.TENANT_MAP + "/createclient")
        .body(Map.of("email", "ctx.lib@test.local", "password", "A123abcd")).exchange().expectStatus().isForbidden();
  }

  @Test
  @Order(3)
  @DisplayName("A grant decides the level and a revoked one blocks the very next request")
  void grantsAreReEvaluatedOnEveryRequest() {
    TenantAccess grant = tenantAccessJpaRepository
        .save(new TenantAccess(owner.getIdUser(), otherIdTenant, TenantAccessLevel.READ));
    assertThat(authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestIntegrationMappings.TENANT_MAP + "/switchto/" + otherIdTenant).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody()).containsEntry("readOnly", "true");

    grant.setAccessLevel(TenantAccessLevel.MANAGE);
    tenantAccessJpaRepository.save(grant);
    assertThat(authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestIntegrationMappings.TENANT_MAP + "/switchto/" + otherIdTenant).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody()).containsEntry("readOnly", "false");

    tenantAccessJpaRepository.delete(grant);
    clientInTenant(otherIdTenant).get().uri(RequestIntegrationMappings.TENANT_MAP + "/deletion-eligibility").exchange()
        .expectStatus().isForbidden();
  }

  @Test
  @Order(4)
  @DisplayName("An unusable context still permits the way home and the account-self endpoints")
  void recoveryFromBlockedContextRemainsPossible() {
    RestTestClient blocked = clientInTenant(otherIdTenant);
    blocked.post().uri(RequestIntegrationMappings.TENANT_MAP + "/switchto/" + ownIdTenant).exchange().expectStatus()
        .isOk();
    blocked.get().uri(RequestIntegrationMappings.TENANT_MAP + "/accessible").exchange().expectStatus().isOk();
  }

  @Test
  @Order(5)
  @DisplayName("Export and client management address the user's own tenant only")
  void accountOperationsRequireTheHomeTenant() throws IOException {
    TenantAccess grant = tenantAccessJpaRepository
        .save(new TenantAccess(owner.getIdUser(), otherIdTenant, TenantAccessLevel.MANAGE));
    try {
      RestTestClient managed = clientInTenant(otherIdTenant);
      managed.get().uri(RequestIntegrationMappings.TENANT_MAP + "/exportpersonaldataaszip").exchange().expectStatus()
          .isBadRequest();
      managed.post().uri(RequestIntegrationMappings.TENANT_MAP + "/createclient")
          .body(Map.of("email", "ctx.lib2@test.local", "password", "A123abcd")).exchange().expectStatus().isForbidden();
      managed.post().uri(RequestIntegrationMappings.TENANT_MAP + "/share")
          .body(Map.of("email", "ctx.lib3@test.local", "password", "A123abcd")).exchange().expectStatus().isForbidden();

      byte[] archive = authenticatedClient(RestTestHelper.ALLEDIT).get()
          .uri(RequestIntegrationMappings.TENANT_MAP + "/exportpersonaldataaszip").exchange().expectStatus().isOk()
          .expectBody().returnResult().getResponseBody();
      Map<String, String> entries = new LinkedHashMap<>();
      try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
      }
      assertThat(entries).containsOnlyKeys("gt_ddl.sql", "gt_data.sql");
      assertThat(entries.get("gt_ddl.sql"))
          .contains("CREATE TABLE `tenant`", "CREATE TABLE `user_dashboard`", "CREATE TABLE `flyway_schema_history`",
              "SET SESSION foreign_key_checks = 0;", "SET SESSION foreign_key_checks = @old_foreign_key_checks;")
          .doesNotContain("INSERT INTO", "CREATE TABLE `portfolio`", "CREATE TABLE `security`");
      assertThat(entries.get("gt_data.sql")).contains("INSERT INTO `tenant`", "INSERT INTO `user`");
    } finally {
      tenantAccessJpaRepository.delete(grant);
    }
  }
}
