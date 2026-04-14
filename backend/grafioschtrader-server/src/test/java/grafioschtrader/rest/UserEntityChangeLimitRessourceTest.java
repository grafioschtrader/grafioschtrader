package grafioschtrader.rest;

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

import grafiosch.entities.BaseID;
import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.rest.RequestMappings;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Stockexchange;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class UserEntityChangeLimitRessourceTest extends BaseIntegrationTest  {


  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @ParameterizedTest
  @MethodSource("userLimitEntity")
  @DisplayName("Increase change limit for limited users")
  <T extends BaseID<Integer>> void createTest(final Class<T> clazz) {
    LocalDate localDate = LocalDate.now().plusDays(1);

    for (String nickname : RestTestHelper.LIMIT_USERS) {
      UserEntityChangeLimit userEntityChangeLimit = new UserEntityChangeLimit(
          RestTestHelper.getUserByNickname(nickname).idUser, clazz.getSimpleName(),
          localDate, 1000);

      authenticatedClient(RestTestHelper.ADMIN)
          .post()
          .uri(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP)
          .body(userEntityChangeLimit)
          .exchange()
          .expectStatus().isOk();
    }
  }

  private static Stream<Arguments> userLimitEntity() {
    return Stream.of(Arguments.of(Stockexchange.class), Arguments.of(Assetclass.class), Arguments.of(Security.class));
  }
}
