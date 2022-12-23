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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import grafioschtrader.entities.Role;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.entities.VerificationToken;
import grafioschtrader.repository.RoleJpaRepository;
import grafioschtrader.repository.VerificationTokenJpaRepository;
import grafioschtrader.rest.RestTestHelper.UserRegister;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.TenantKindType;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class UserResourceTest {

  @Autowired
  private TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;

  @Autowired
  VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Autowired
  RoleJpaRepository roleJpaRepository;

  @Order(1)
  @Test
  @DisplayName("Crate all users")
  void createUserForVerification() {
    for (UserRegister user : RestTestHelper.users) {
      HttpHeaders headers = new HttpHeaders();
      headers.set("referer", "http://localhost:" + port + "/grafioschtrader/register");
      HttpEntity<UserRegister> httpEntity = new HttpEntity<>(user, headers);
      ResponseEntity<User> response = restTemplate.exchange(
          RestTestHelper.createURLWithPort(RequestMappings.USER_MAP + "/", port), HttpMethod.POST, httpEntity,
          User.class);
      assertThat(response.getBody().getIdUser()).isGreaterThan(0);
    }
  }

  @Order(2)
  @Test
  @DisplayName("Verify users")
  void tokenverify() {
    List<VerificationToken> allTokens = verificationTokenJpaRepository.findAll();
    for (VerificationToken verificationToken : allTokens) {
      ResponseEntity<String> response = restTemplate.getForEntity(RestTestHelper.createURLWithPort(
          RequestMappings.USER_MAP + "/tokenverify/" + verificationToken.getToken(), port), String.class);
      assertThat(response.getBody()).isEqualTo(UserResource.TOKEN_SUCCESS);
    }

  }

  @Order(3)
  @Test
  @DisplayName("Adjust user rights but admin")
  void adjustUserRights() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
    Map<String, Role> roleMap = roleJpaRepository.findAll().stream()
        .collect(Collectors.toMap(Role::getRolename, Function.identity()));
    ResponseEntity<User> response = adjustUserRightsByNickname(RestTestHelper.ALLEDIT, Role.ROLE_ALL_EDIT, roleMap);
    assertThat(response.getBody().getMostPrivilegedRole()).isEqualTo(Role.ROLE_ALL_EDIT);

    response = adjustUserRightsByNickname(RestTestHelper.USER, Role.ROLE_USER, roleMap);
    assertThat(response.getBody().getMostPrivilegedRole()).isEqualTo(Role.ROLE_USER);

  }

  @Order(4)
  @Test
  @DisplayName("Create tenant")
  void createTenant() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
    for (UserRegister user : RestTestHelper.users) {
      Tenant tenant = new Tenant("Tenant " + user.nickname, user.currency, user.idUser, TenantKindType.MAIN, false);
      ResponseEntity<Tenant> response = restTemplate.exchange(
          RestTestHelper.createURLWithPort(RequestMappings.TENANT_MAP + "/", port), HttpMethod.POST,
          RestTestHelper.getHttpEntity(user.nickname, tenant), Tenant.class);
      assertThat(response.getBody().getIdTenant()).isGreaterThan(0);
    }

  }

  private ResponseEntity<User> adjustUserRightsByNickname(String nickname, String mostPriviledRole,
      Map<String, Role> roleMap) {
    // Get user
    ResponseEntity<User> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(
            RequestMappings.USERADMIN_MAP + "/" + RestTestHelper.getUserByNickname(nickname).idUser, port),
        HttpMethod.GET, RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, null), User.class);
    User user = response.getBody();
    user.setMostPrivilegedRole(mostPriviledRole);

    // Write user
    return restTemplate.exchange(RestTestHelper.createURLWithPort(RequestMappings.USERADMIN_MAP + "/", port),
        HttpMethod.PUT, RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, user), User.class);
  }
}
