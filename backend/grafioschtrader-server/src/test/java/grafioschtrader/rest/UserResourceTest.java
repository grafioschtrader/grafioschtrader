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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

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
      HttpHeaders headers = new HttpHeaders();
      headers.set("referer", "http://localhost:" + port + "/grafioschtrader/register");
      HttpEntity<UserRegister> httpEntity = new HttpEntity<>(user, headers);
      ResponseEntity<User> response = restTemplate.exchange(
          RestTestHelper.createURLWithPort(RequestMappings.USER_MAP, port), HttpMethod.POST, httpEntity,
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
  @Transactional
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
          RestTestHelper.createURLWithPort(RequestGTMappings.TENANT_MAP, port), HttpMethod.POST,
          RestTestHelper.getHttpEntity(user.nickname, tenant), Tenant.class);
      assertThat(response.getBody().getIdTenant()).isGreaterThan(0);
    }

  }

  private ResponseEntity<User> adjustUserRightsByNickname(String nickname, String mostPriviledRole,
      Map<String, Role> roleMap) {
    User user = userJpaRepository.findByNickname(nickname).get();
    user.setMostPrivilegedRole(mostPriviledRole);

    // Write user
    return restTemplate.exchange(RestTestHelper.createURLWithPort(RequestMappings.USERADMIN_MAP, port),
        HttpMethod.PUT, RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, user), User.class);
  }
}
