package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.context.MessageSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;

import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.rest.RequestMappings;
import grafiosch.types.TenantAccessLevel;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.AlgoReplayRunRegistry;
import grafioschtrader.types.TenantKindType;

/**
 * The rules that decide which tenant a request may operate in, and what stays unavailable while it operates in a
 * simulation environment.
 *
 * <p>
 * The environments are inserted as bare tenant rows rather than built through {@code createSimulationTenant}: kind and
 * parent are everything the context rules read, so a full copy with portfolios, accounts and an opening ledger would
 * cost the suite minutes without testing anything more.
 * </p>
 *
 * <p>
 * {@code DELETE /api/tenant} is deliberately not fired here. It is guarded by the same private helper as the export
 * below, so the export case proves the rule, while a live request would destroy the fixture user of the whole suite the
 * moment that helper ever regresses.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class SimulationContextResourceTest extends BaseIntegrationTest {

  private static final String ALGO_ALERT_MAP = RequestMappings.API + "algoalerts";
  /** The switch endpoint answers with a flat token/readOnly map. */
  private static final ParameterizedTypeReference<Map<String, String>> MAP_TYPE = new ParameterizedTypeReference<>() {
  };

  @Autowired
  private TenantJpaRepository tenantJpaRepository;
  @Autowired
  private UserJpaRepository userJpaRepository;
  @Autowired
  private TenantAccessJpaRepository tenantAccessJpaRepository;
  @Autowired
  private AlgoReplayRunRegistry replayRunRegistry;

  @Autowired
  private MessageSource messages;

  private User owner;
  private User sibling;
  private Integer ownIdTenant;
  /** A simulation environment below the owner's home tenant. */
  private Integer ownSimulation;
  /** A simulation environment below another user's home tenant. */
  private Integer foreignSimulation;
  /** An ordinary main tenant the owner has nothing to do with. */
  private Integer unrelatedIdTenant;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    owner = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser).orElseThrow();
    sibling = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.LIMIT1).idUser).orElseThrow();
    ownIdTenant = owner.getIdTenant();
    ownSimulation = createSimulation("Ctx own", ownIdTenant);
    foreignSimulation = createSimulation("Ctx foreign", sibling.getIdTenant());
    unrelatedIdTenant = tenantJpaRepository
        .save(new Tenant("Ctx unrelated", "CHF", owner.getIdUser(), TenantKindType.MAIN, false)).getIdTenant();
  }

  @AfterAll
  void tearDown() {
    for (Integer idTenant : new Integer[] { ownSimulation, foreignSimulation, unrelatedIdTenant }) {
      tenantJpaRepository.findById(idTenant).ifPresent(tenantJpaRepository::delete);
    }
  }

  private Integer createSimulation(String name, Integer idParentTenant) {
    Tenant simulation = new Tenant(name, "CHF", owner.getIdUser(), TenantKindType.SIMULATION_COPY, false);
    simulation.setIdParentTenant(idParentTenant);
    return tenantJpaRepository.save(simulation).getIdTenant();
  }

  /** A client whose token names the given tenant, without going through the switch endpoint. */
  private RestTestClient clientInTenant(User user, Integer idTenant) {
    return restTestClient.mutate()
        .defaultHeader("x-auth-token", jwtTokenHandler.createTokenForUser(user, 120, idTenant)).build();
  }

  private RestTestClient inOwnSimulation() {
    return clientInTenant(owner, ownSimulation);
  }

  @Test
  @Order(1)
  @DisplayName("A user switches into their own environment but not into one of another user")
  void switchTargetsAreOwnershipChecked() {
    Map<String, String> response = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + ownSimulation).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody();
    assertThat(response).containsEntry("readOnly", "false");
    assertThat(response.get("token")).isNotNull();

    authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + foreignSimulation).exchange().expectStatus().isForbidden();
    authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + unrelatedIdTenant).exchange().expectStatus().isForbidden();
  }

  @Test
  @Order(2)
  @DisplayName("A token naming a tenant the user may not use is refused, reads included")
  void forbiddenContextIsRejectedOnEveryMethod() {
    clientInTenant(owner, foreignSimulation).get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus()
        .isForbidden();
    clientInTenant(owner, unrelatedIdTenant).get().uri(RequestGTMappings.WATCHLIST_MAP + "/tenant").exchange()
        .expectStatus().isForbidden();
    clientInTenant(owner, unrelatedIdTenant).post().uri(RequestGTMappings.WATCHLIST_MAP).body(Map.of("name", "x"))
        .exchange().expectStatus().isForbidden();
  }

  @Test
  @Order(3)
  @DisplayName("A blocked context keeps the way home and the account-self endpoints open")
  void recoveryFromBlockedContextRemainsPossible() {
    RestTestClient blocked = clientInTenant(owner, unrelatedIdTenant);
    blocked.post().uri(RequestGTMappings.TENANT_MAP + "/switchto/" + ownIdTenant).exchange().expectStatus().isOk();
    // Switching does not become a way in: the target is authorized by the same resolver.
    blocked.post().uri(RequestGTMappings.TENANT_MAP + "/switchto/" + foreignSimulation).exchange().expectStatus()
        .isForbidden();
  }

  @Test
  @Order(4)
  @DisplayName("A revoked grant blocks the next request, a granted one decides read-only")
  void grantsAreReEvaluatedOnEveryRequest() {
    TenantAccess readGrant = tenantAccessJpaRepository
        .save(new TenantAccess(owner.getIdUser(), unrelatedIdTenant, TenantAccessLevel.READ));
    Map<String, String> response = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + unrelatedIdTenant).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody();
    assertThat(response).containsEntry("readOnly", "true");
    clientInTenant(owner, unrelatedIdTenant).get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus().isOk();

    readGrant.setAccessLevel(TenantAccessLevel.MANAGE);
    tenantAccessJpaRepository.save(readGrant);
    assertThat(authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + unrelatedIdTenant).exchange().expectStatus().isOk()
        .expectBody(MAP_TYPE).returnResult().getResponseBody()).containsEntry("readOnly", "false");

    tenantAccessJpaRepository.delete(readGrant);
    clientInTenant(owner, unrelatedIdTenant).get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus()
        .isForbidden();
  }

  @Test
  @Order(5)
  @DisplayName("A deleted environment is unusable from a token that still names it")
  void deletedEnvironmentIsNoLongerAccessible() {
    Integer disposable = createSimulation("Ctx gone", ownIdTenant);
    RestTestClient inside = clientInTenant(owner, disposable);
    inside.get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus().isOk();

    tenantJpaRepository.deleteById(disposable);
    inside.get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus().isForbidden();
  }

  @Test
  @Order(6)
  @DisplayName("Account and client management are unavailable from an environment")
  void accountOperationsRequireTheHomeTenant() {
    inOwnSimulation().get().uri(RequestGTMappings.TENANT_MAP + "/exportpersonaldataaszip").exchange().expectStatus()
        .isBadRequest();
    inOwnSimulation().post().uri(RequestGTMappings.TENANT_MAP + "/createclient")
        .body(Map.of("email", "ctx.client@test.local", "password", "A123abcd")).exchange().expectStatus().isForbidden();
    inOwnSimulation().post().uri(RequestGTMappings.TENANT_MAP + "/share")
        .body(Map.of("email", "ctx.viewer@test.local", "password", "A123abcd")).exchange().expectStatus().isForbidden();

    // The same export is the user's own business again at home.
    authenticatedClient(RestTestHelper.ALLEDIT).get().uri(RequestGTMappings.TENANT_MAP + "/exportpersonaldataaszip")
        .exchange().expectStatus().isOk();
  }

  @Test
  @Order(7)
  @DisplayName("Every alert endpoint is refused from an environment and available at home")
  void alertsAreAHomeTenantFunction() {
    inOwnSimulation().get().uri(ALGO_ALERT_MAP + "/statuses").exchange().expectStatus().isBadRequest();
    inOwnSimulation().get().uri(ALGO_ALERT_MAP + "/evaluations").exchange().expectStatus().isBadRequest();
    inOwnSimulation().get().uri(ALGO_ALERT_MAP + "/trading").exchange().expectStatus().isBadRequest();
    inOwnSimulation().get().uri(ALGO_ALERT_MAP + "/notifications?page=0&size=10").exchange().expectStatus()
        .isBadRequest();
    inOwnSimulation().post().uri(RequestGTMappings.ALGOTOP_MAP + "/evaluatealarms").exchange().expectStatus()
        .isBadRequest();

    authenticatedClient(RestTestHelper.ALLEDIT).get().uri(ALGO_ALERT_MAP + "/statuses").exchange().expectStatus()
        .isOk();
  }

  @Test
  @Order(9)
  @DisplayName("An environment being replayed can neither be entered nor used, and recovers when the run ends")
  void activeEnvironmentIsRefusedUntilTheRunEnds() {
    // Reserving directly is what a submitted run does; it keeps this test free of an actual replay.
    assertThat(replayRunRegistry.reserve(ownSimulation)).isTrue();
    try {
      String activeMessage = messages.getMessage("gt.simulation.environment.active", null,
          owner.createAndGetJavaLocale());
      authenticatedClient(RestTestHelper.ALLEDIT).post()
          .uri(RequestGTMappings.TENANT_MAP + "/switchto/" + ownSimulation).exchange().expectStatus().isForbidden()
          .expectBody().jsonPath("$.className").isEqualTo("SingleNativeMsgError").jsonPath("$.error.message")
          .isEqualTo(activeMessage);
      // A token that already names it is refused as well, for reads as much as for writes.
      inOwnSimulation().get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus().isForbidden().expectBody()
          .jsonPath("$.error.message").isEqualTo(activeMessage);
      inOwnSimulation().get().uri(RequestGTMappings.WATCHLIST_MAP + "/tenant").exchange().expectStatus().isForbidden();
      inOwnSimulation().post().uri(RequestGTMappings.WATCHLIST_MAP).body(Map.of("name", "x")).exchange().expectStatus()
          .isForbidden();
      // Home is unaffected: status and cancellation stay reachable from there.
      authenticatedClient(RestTestHelper.ALLEDIT).get().uri(RequestGTMappings.TENANT_MAP).exchange().expectStatus()
          .isOk();
    } finally {
      replayRunRegistry.release(ownSimulation);
    }
    authenticatedClient(RestTestHelper.ALLEDIT).post().uri(RequestGTMappings.TENANT_MAP + "/switchto/" + ownSimulation)
        .exchange().expectStatus().isOk();
  }

  @Test
  @Order(8)
  @DisplayName("The strategy hierarchy cannot be written through REST from an environment")
  void hierarchyWritesAreRefusedFromEnvironment() {
    inOwnSimulation().post().uri(RequestGTMappings.ALGOTOP_MAP + "/create")
        .body(Map.of("name", "Ctx refused", "percentage", 100)).exchange().expectStatus().isBadRequest();
    inOwnSimulation().put().uri(RequestGTMappings.ALGOTOP_MAP + "/normalizeallpercentages/1").exchange().expectStatus()
        .isBadRequest();
    inOwnSimulation().put().uri(RequestGTMappings.ALGOTOP_MAP + "/normalizepercentages/1").exchange().expectStatus()
        .isBadRequest();

    // Reading the hierarchy is what an environment needs, and stays available.
    inOwnSimulation().get().uri(RequestGTMappings.ALGOTOP_MAP + "/tenant").exchange().expectStatus().isOk();
  }
}
