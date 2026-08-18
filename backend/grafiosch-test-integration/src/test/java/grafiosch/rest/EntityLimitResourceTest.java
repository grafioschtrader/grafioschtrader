package grafiosch.rest;

import java.time.LocalDate;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
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
import grafiosch.entities.EntityLimit;
import grafiosch.entities.MailSettingForward;

/**
 * Raises the daily CUD limit of the {@code LIMITEDIT} users so the following resource tests are not stopped by the
 * limit guard, and covers the admin-only {@code entitylimit} endpoint of {@code grafiosch-server-base} while doing so.
 *
 * <p>
 * The entity classes are passed as a {@code MethodSource} because a daily key is identified by the simple class name.
 * Only classes of the reusable libraries are used here; the Grafioschtrader counterpart adds its own.
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
  void createTest(final Class<?> clazz) {
    LocalDate validUntil = LocalDate.now().plusDays(1);

    Assertions.assertThat(RestTestHelper.LIMIT_USERS).isNotEmpty();
    for (String nickname : RestTestHelper.LIMIT_USERS) {
      EntityLimit entityLimit = new EntityLimit(LimitKey.dayCud(clazz.getSimpleName()), null,
          RestTestHelper.getUserByNickname(nickname).idUser, 1000, validUntil);

      authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestMappings.ENTITY_LIMIT_MAP).body(entityLimit)
          .exchange().expectStatus().isOk();
    }
  }

  private static Stream<Arguments> userLimitEntity() {
    return Stream.of(Arguments.of(MailSettingForward.class));
  }
}
