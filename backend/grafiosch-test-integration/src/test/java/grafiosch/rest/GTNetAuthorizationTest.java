package grafiosch.rest;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.http.HttpMethod;

import grafiosch.integration.gtnet.IntegrationExchangeKindType;

/**
 * Guards who may change GTNet and who may only look at it.
 *
 * <p>
 * GTNet is instance-wide network state rather than tenant data, so every create, update and delete belongs to an
 * administrator, while reading which peers this instance talks to does not. Nothing in the entities enforces that: they
 * extend plain {@code BaseID}, so {@code UpdateCreate} routes them to {@code updateSpecialEntity}, which is a
 * find-by-id plus a save with no privilege check of its own. The rule therefore lives entirely in the request matchers
 * of {@code SecurityConfig}, registered before the broad {@code /api/**} matcher that admits every self-registered user
 * through {@code ROLE_LIMITEDIT}.
 * </p>
 *
 * <p>
 * Only refusals and permitted reads are exercised here. Driving a write as the administrator would create peer rows and
 * messages that the rest of the suite would then have to clean up; the two-peer suites cover that path against real
 * peers.
 * </p>
 */
@TestInstance(Lifecycle.PER_CLASS)
class GTNetAuthorizationTest extends BaseIntegrationTest {

  /** The write methods, which are administrator-only on every GTNet path. */
  private static final List<HttpMethod> WRITE_METHODS = List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
      HttpMethod.DELETE);

  /** Every GTNet path whose writes are restricted. The bare form and one below it, because both are matched. */
  private static final List<String> GTNET_PATHS = List.of(RequestMappings.GTNET_MAP, RequestMappings.GTNET_MAP + "/1",
      RequestMappings.GTNET_MESSAGE_MAP, RequestMappings.GTNET_MESSAGE_MAP + "/1/markasread",
      RequestMappings.GTNET_MESSAGE_ANSWER_MAP, RequestMappings.GTNET_MESSAGE_ANSWER_MAP + "/1",
      RequestMappings.GTNETCONFIG_MAP, RequestMappings.GTNETCONFIGENTITY_MAP, RequestMappings.GTNETEXCHANGELOG_MAP,
      RequestMappings.GTNETDATAEXPORT_MAP, RequestMappings.GTNETDATAEXPORT_MAP + "/import");

  /**
   * Reads that stay open, because seeing the network this instance is part of is not an administrative act. The
   * exchange-log tree carries its mandatory {@code entityKind} query parameter: without it the request never reaches
   * the authorization decision this test is about, it fails in the argument binding.
   */
  private static final List<String> OPEN_READS = List.of(RequestMappings.GTNET_MAP + "/gtnetwithmessage",
      RequestMappings.GTNET_MESSAGE_MAP + "/msgformdefinition", RequestMappings.GTNET_MESSAGE_MAP + "/protocol",
      RequestMappings.GTNET_MESSAGE_MAP + "/admin", RequestMappings.GTNET_MESSAGE_MAP + "/admin/count",
      RequestMappings.GTNETEXCHANGELOG_MAP + "/trees?entityKind=" + IntegrationExchangeKindType.INTEGRATION_STREAM);

  /**
   * Reads that do not. The auto-answer rules publish the terms on which this instance admits a peer, and the export is
   * a bulk dump of the whole GTNet state.
   */
  private static final List<String> ADMIN_READS = List.of(RequestMappings.GTNET_MESSAGE_ANSWER_MAP,
      RequestMappings.GTNETDATAEXPORT_MAP + "/export", RequestMappings.GTNET_MAP + "/messageattempts/1");

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @DisplayName("A user without the administrator role may not write to any GTNet endpoint")
  void writesAreRefusedForNonAdministrators() {
    for (String nickname : List.of(RestTestHelper.USER, RestTestHelper.LIMITED, RestTestHelper.ALLEDIT)) {
      for (String path : GTNET_PATHS) {
        for (HttpMethod method : WRITE_METHODS) {
          expectForbidden(nickname, method, path);
        }
      }
    }
  }

  @Test
  @DisplayName("The auto-answer rules and the data export may not be read without the administrator role")
  void administratorOnlyReadsAreRefusedForNonAdministrators() {
    for (String nickname : List.of(RestTestHelper.USER, RestTestHelper.LIMITED, RestTestHelper.ALLEDIT)) {
      for (String path : ADMIN_READS) {
        expectForbidden(nickname, HttpMethod.GET, path);
      }
    }
  }

  @Test
  @DisplayName("Every other GTNet read stays open to an ordinary user")
  void openReadsAreAllowedForEveryAuthenticatedUser() {
    for (String nickname : List.of(RestTestHelper.USER, RestTestHelper.LIMITED, RestTestHelper.ADMIN)) {
      for (String path : OPEN_READS) {
        authenticatedClient(nickname).get().uri(path).exchange().expectStatus().isOk();
      }
    }
  }

  @Test
  @DisplayName("The administrator reads the auto-answer rules")
  void administratorReadsTheAutoAnswerRules() {
    authenticatedClient(RestTestHelper.ADMIN).get().uri(RequestMappings.GTNET_MESSAGE_ANSWER_MAP).exchange()
        .expectStatus().isOk();
  }

  /**
   * Asserts that the request is refused by the authorization layer. A body is deliberately not sent: the matcher
   * decides before the request reaches a controller, so a refusal must not depend on the payload being well formed.
   *
   * @param nickname the user to authenticate as
   * @param method   the HTTP method to attempt
   * @param path     the path to attempt it on
   */
  private void expectForbidden(String nickname, HttpMethod method, String path) {
    authenticatedClient(nickname).method(method).uri(path).exchange().expectStatus().isForbidden();
  }
}
