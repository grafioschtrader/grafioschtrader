package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

import grafiosch.dto.UserAccessTokenCreateRequest;
import grafiosch.dto.UserAccessTokenDto;
import grafiosch.entities.UserAccessToken;
import grafiosch.types.AccessTokenScope;

/**
 * The library side of personal access tokens on the portable host: the lifetime cap, the validation of a creation and
 * revoking, which only ever reaches the caller's own tokens.
 *
 * <p>
 * The class removes the tokens and the daily counter of its user first, so it can be rerun against the same
 * {@code grafiosch_t} without a reset. The paths that accept a token belong to the application and are tested there.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class UserAccessTokenResourceTest extends BaseIntegrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Integer idUser;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    idUser = RestTestHelper.getUserByNickname(RestTestHelper.USER).idUser;
    jdbcTemplate.update("DELETE FROM user_access_token WHERE id_user = ?", idUser);
    jdbcTemplate.update("DELETE FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'UserAccessToken'",
        idUser);
  }

  @Order(1)
  @Test
  @DisplayName("Creation is refused for an out of range lifetime, the WRITE scope and a duplicate name")
  void invalidCreationsAreRefused() {
    create("token-range", AccessTokenScope.READ, UserAccessToken.MAX_EXPIRES_IN_DAYS + 1, 400);
    create("token-write", AccessTokenScope.WRITE, 30, 400);
    create("token-dup", AccessTokenScope.READ, 30, 200);
    create("token-dup", AccessTokenScope.READ, 30, 400);
  }

  @Order(2)
  @Test
  @DisplayName("More than MAX_USER_ACCESS_TOKEN tokens are refused; the list never shows a hash or a token")
  void lifetimeCapAndListing() {
    for (int i = 2; i <= 5; i++) {
      create("token-" + i, AccessTokenScope.READ, 30, 200);
    }
    String answer = authenticatedClient(RestTestHelper.USER).post().uri(RequestMappings.USER_ACCESS_TOKEN_MAP)
        .body(new UserAccessTokenCreateRequest("token-6", AccessTokenScope.READ, 30)).exchange().expectStatus()
        .isBadRequest().expectBody(String.class).returnResult().getResponseBody();
    assertThat(answer).doesNotContain(UserAccessToken.TOKEN_PREFIX);

    String raw = authenticatedClient(RestTestHelper.USER).get().uri(RequestMappings.USER_ACCESS_TOKEN_MAP).exchange()
        .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    assertThat(raw).doesNotContain("tokenHash").doesNotContain("plainToken");
    assertThat(getTokens()).hasSize(5);
  }

  @Order(3)
  @Test
  @DisplayName("A token of another user cannot be revoked; an own token can")
  void revokeOnlyOwnTokens() {
    Integer idOwn = getTokens().getFirst().idUserAccessToken();
    authenticatedClient(RestTestHelper.ALLEDIT).delete().uri(RequestMappings.USER_ACCESS_TOKEN_MAP + "/" + idOwn)
        .exchange().expectStatus().isNotFound();
    authenticatedClient(RestTestHelper.USER).delete().uri(RequestMappings.USER_ACCESS_TOKEN_MAP + "/" + idOwn)
        .exchange().expectStatus().isNoContent();
    assertThat(getTokens()).hasSize(4).noneMatch(t -> t.idUserAccessToken().equals(idOwn));
    jdbcTemplate.update("DELETE FROM user_access_token WHERE id_user = ?", idUser);
  }

  private void create(String name, AccessTokenScope scope, int days, int expectedStatus) {
    authenticatedClient(RestTestHelper.USER).post().uri(RequestMappings.USER_ACCESS_TOKEN_MAP)
        .body(new UserAccessTokenCreateRequest(name, scope, days)).exchange().expectStatus().isEqualTo(expectedStatus);
  }

  private List<UserAccessTokenDto> getTokens() {
    return authenticatedClient(RestTestHelper.USER).get().uri(RequestMappings.USER_ACCESS_TOKEN_MAP).exchange()
        .expectStatus().isOk().expectBody(new ParameterizedTypeReference<List<UserAccessTokenDto>>() {
        }).returnResult().getResponseBody();
  }
}
