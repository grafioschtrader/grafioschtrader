package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.entities.VerificationToken;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.VerificationTokenJpaRepository;
import grafiosch.rest.RequestMappings;
import grafiosch.rest.UserResource;
import grafioschtrader.entities.Tenant;
import grafioschtrader.rest.RestTestHelper.UserRegister;
import grafioschtrader.types.TenantKindType;
import jakarta.transaction.Transactional;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class UserResourceTest extends BaseIntegrationTest  {


  @Autowired
  VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Autowired
  RoleJpaRepository roleJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Order(1)
  @Test
  @DisplayName("Crate all users")
  void createUserForVerification() {
    for (UserRegister user : RestTestHelper.users) {
      User created = restTestClient.post()
          .uri(RequestMappings.USER_MAP)
          .header("referer", "http://localhost:" + port + "/grafioschtrader/register")
          .contentType(MediaType.APPLICATION_JSON)
          .body(user)
          .exchange()
          .expectStatus().isOk()
          .expectBody(User.class)
          .returnResult()
          .getResponseBody();
      assertThat(created.getIdUser()).isGreaterThan(0);
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
      assertThat(body).isEqualTo(UserResource.TOKEN_SUCCESS);
    }

  }

  @Order(3)
  @Test
  @Transactional
  @DisplayName("Adjust user rights but admin")
  void adjustUserRights() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    Map<String, Role> roleMap = roleJpaRepository.findAll().stream()
        .collect(Collectors.toMap(Role::getRolename, Function.identity()));
    User updated = adjustUserRightsByNickname(RestTestHelper.ALLEDIT, Role.ROLE_ALL_EDIT, roleMap);
    assertThat(updated.getMostPrivilegedRole()).isEqualTo(Role.ROLE_ALL_EDIT);

    updated = adjustUserRightsByNickname(RestTestHelper.USER, Role.ROLE_USER, roleMap);
    assertThat(updated.getMostPrivilegedRole()).isEqualTo(Role.ROLE_USER);
  }

  @Order(4)
  @Test
  @DisplayName("Create tenant")
  void createTenant() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    for (UserRegister user : RestTestHelper.users) {
      Tenant tenant = new Tenant("Tenant " + user.nickname, user.currency, user.idUser, TenantKindType.MAIN, false);
      Tenant created = authenticatedClient(user.nickname)
          .post()
          .uri(RequestGTMappings.TENANT_MAP)
          .body(tenant)
          .exchange()
          .expectStatus().isOk()
          .expectBody(Tenant.class)
          .returnResult()
          .getResponseBody();
      assertThat(created.getIdTenant()).isGreaterThan(0);
    }

  }

  private User adjustUserRightsByNickname(String nickname, String mostPriviledRole,
      Map<String, Role> roleMap) {
    User user = userJpaRepository.findByNickname(nickname).get();
    user.setMostPrivilegedRole(mostPriviledRole);

    return authenticatedClient(RestTestHelper.ADMIN)
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
