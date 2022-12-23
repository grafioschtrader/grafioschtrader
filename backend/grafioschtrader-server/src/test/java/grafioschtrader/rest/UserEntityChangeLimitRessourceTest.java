package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.BaseID;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class UserEntityChangeLimitRessourceTest {

  @Autowired
  private TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
  }

  @ParameterizedTest
  @MethodSource("userLimitEntity")
  @DisplayName("Increase change limit for limited users")
  <T extends BaseID> void createTest(final Class<T> clazz) {
    LocalDate localDate = LocalDate.now().plusDays(1);

    for (String nickname : RestTestHelper.LIMIT_USERS) {
      UserEntityChangeLimit userEntityChangeLimit = new UserEntityChangeLimit(
          RestTestHelper.getUserByNickname(nickname).idUser, clazz.getSimpleName(),
          DateHelper.getDateFromLocalDate(localDate), 1000);

      ResponseEntity<UserEntityChangeLimit> response = restTemplate.exchange(
          RestTestHelper.createURLWithPort(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP + "/", port), HttpMethod.POST,
          RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, userEntityChangeLimit), UserEntityChangeLimit.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  private static Stream<Arguments> userLimitEntity() {
    return Stream.of(Arguments.of(Stockexchange.class), Arguments.of(Assetclass.class), Arguments.of(Security.class));
  }
}
