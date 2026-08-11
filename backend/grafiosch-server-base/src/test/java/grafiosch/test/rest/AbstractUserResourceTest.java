package grafiosch.test.rest;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.entities.VerificationToken;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.VerificationTokenJpaRepository;
import grafiosch.rest.RequestMappings;
import grafiosch.rest.UserResource;
import jakarta.transaction.Transactional;

/**
 * The registration and login flow that every application built on {@code grafiosch-server-base} shares: create the
 * users of {@code testdata/users.json}, verify their mail tokens, promote them to the role their JSON object asks for
 * and finally create a tenant for each of them.
 *
 * <p>
 * Only the last step is application specific, because the tenant entity is: {@code TenantBase} is extended per
 * application, which is exactly why {@code grafiosch-test-integration} exists. Subclasses therefore implement
 * {@link #createTenantForUser(UserRegister)} and nothing else.
 *
 * <p>
 * A subclass must carry the Spring test annotations itself ({@code @SpringBootTest} with the application's boot class,
 * {@code @AutoConfigureRestTestClient}, {@code @ActiveProfiles}, plus the Flyway property source) as well as
 * {@code @TestMethodOrder(OrderAnnotation.class)} and {@code @TestInstance(Lifecycle.PER_CLASS)} — the four ordered
 * steps below build on each other. Applications bundle those into one composed annotation so the block is written once.
 *
 * <p>
 * This class must run first in the resource suite: every other resource test authenticates as one of the users it
 * creates.
 */
public abstract class AbstractUserResourceTest extends BaseIntegrationTestSupport {

  @Autowired
  protected VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Autowired
  protected UserJpaRepository userJpaRepository;

  @Order(1)
  @Test
  @DisplayName("Create all users")
  void createUserForVerification() {
    for (UserRegister user : RestTestHelperBase.users) {
      User created = restTestClient.post()
          .uri(RequestMappings.USER_MAP)
          .header("referer", getRegistrationReferer())
          .contentType(MediaType.APPLICATION_JSON)
          .body(user)
          .exchange()
          .expectStatus().isOk()
          .expectBody(User.class)
          .returnResult()
          .getResponseBody();
      Assertions.assertThat(created.getIdUser()).isGreaterThan(0);
    }
  }

  @Order(2)
  @Test
  @DisplayName("Verify users")
  void tokenverify() {
    List<VerificationToken> allTokens = verificationTokenJpaRepository.findAll();
    for (VerificationToken verificationToken : allTokens) {
      String body = restTestClient.get()
          .uri(RequestMappings.USER_MAP + "/tokenverify/" + verificationToken.getToken())
          .exchange()
          .expectStatus().isOk()
          .expectBody(String.class)
          .returnResult()
          .getResponseBody();
      Assertions.assertThat(body).isEqualTo(UserResource.TOKEN_SUCCESS);
    }
  }

  @Order(3)
  @Test
  @Transactional
  @DisplayName("Adjust user rights (driven by users.json role; ADMIN and LIMITEDIT need no promotion)")
  void adjustUserRights() {
    RestTestHelperBase.inizializeUserTokens(restTestClient, jwtTokenHandler);
    for (UserRegister u : RestTestHelperBase.users) {
      if ("ADMIN".equals(u.role) || "LIMITEDIT".equals(u.role)) {
        continue;
      }
      String roleName = Role.ROLE + u.role;
      User updated = adjustUserRightsByNickname(u.nickname, roleName);
      Assertions.assertThat(updated.getMostPrivilegedRole()).isEqualTo(roleName);
    }
  }

  @Order(4)
  @Test
  @DisplayName("Create tenant")
  void createTenant() {
    RestTestHelperBase.inizializeUserTokens(restTestClient, jwtTokenHandler);
    for (UserRegister user : RestTestHelperBase.users) {
      createTenantForUser(user);
    }
  }

  /**
   * Creates the tenant of one fixture user through the application's own tenant endpoint. The tenant name follows the
   * convention {@code "Tenant " + user.nickname}, which the browser suite relies on when it registers its own users.
   *
   * @param user the fixture row, with {@code idUser} and {@code authToken} already filled in
   */
  protected abstract void createTenantForUser(UserRegister user);

  /**
   * The {@code referer} header sent on registration. {@code UserResource.createUserForVerification} strips everything
   * after the last slash and uses the remainder as the base URL of the verification link in the mail, so the value must
   * end in the registration route of the application under test.
   *
   * @return the referer to send; override when the application is served below a context path
   */
  protected String getRegistrationReferer() {
    return "http://localhost:" + port + "/register";
  }

  /**
   * Promotes one user to the given role through the admin endpoint, acting as the first user of the fixture, which is
   * the administrator by convention.
   *
   * @param nickname          the user to promote
   * @param mostPriviledRole  the role name including the {@code ROLE_} prefix
   * @return the updated user as returned by the endpoint
   */
  private User adjustUserRightsByNickname(String nickname, String mostPriviledRole) {
    User user = userJpaRepository.findByNickname(nickname).get();
    user.setMostPrivilegedRole(mostPriviledRole);

    return authenticatedClient(RestTestHelperBase.ALL_USERS[0])
        .put()
        .uri(RequestMappings.USERADMIN_MAP)
        .body(user)
        .exchange()
        .expectStatus().isOk()
        .expectBody(User.class)
        .returnResult()
        .getResponseBody();
  }
}
