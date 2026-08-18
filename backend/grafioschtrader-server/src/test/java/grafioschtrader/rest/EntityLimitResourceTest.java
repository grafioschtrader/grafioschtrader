package grafioschtrader.rest;

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

import grafiosch.dto.LimitKey;
import grafiosch.entities.BaseID;
import grafiosch.entities.EntityLimit;
import grafiosch.rest.RequestMappings;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;

/**
 * Raises the daily CUD budget of the limited users on the shared entities the later tests operate on.
 *
 * <p>
 * Its position in {@code ResourceTestSuite_1} is significant: every subsequent test that creates shared data as
 * {@code limit1} or {@code limit2} depends on the rows written here, so this class must run right after the users are
 * registered and before anything else changes data.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class EntityLimitResourceTest extends BaseIntegrationTest {

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @ParameterizedTest
  @MethodSource("userLimitEntity")
  @DisplayName("Increase daily change limit for limited users")
  <T extends BaseID<Integer>> void createTest(final Class<T> clazz) {
    LocalDate validUntil = LocalDate.now().plusDays(1);

    for (String nickname : RestTestHelper.LIMIT_USERS) {
      EntityLimit entityLimit = new EntityLimit(LimitKey.dayCud(clazz.getSimpleName()), null,
          RestTestHelper.getUserByNickname(nickname).idUser, 1000, validUntil);

      authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestMappings.ENTITY_LIMIT_MAP).body(entityLimit)
          .exchange().expectStatus().isOk();
    }
  }

  private static Stream<Arguments> userLimitEntity() {
    return Stream.of(Arguments.of(Stockexchange.class), Arguments.of(Assetclass.class), Arguments.of(Security.class));
  }
}
