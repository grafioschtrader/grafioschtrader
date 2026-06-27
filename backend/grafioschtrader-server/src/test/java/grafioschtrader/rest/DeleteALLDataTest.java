package grafioschtrader.rest;

import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.BaseID;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.repository.UserEntityChangeLimitJpaRepository;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.SecurityJpaRepository;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class DeleteALLDataTest extends BaseIntegrationTest {

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  @Autowired
  private UserEntityChangeLimitJpaRepository userEntityChangeLimitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(1)
  @DisplayName("Delete user entity change counter for all users")
  void deleteUserEntityChangeCounter() {
    userEntityChangeCountJpaRepository.deleteAll();
    Assertions.assertThat(userEntityChangeCountJpaRepository.count()).isZero();
  }

  @Test
  @Order(2)
  @DisplayName("Delete user entity change limit for all users")
  void deleteUserEntityChangeLimit() {
    userEntityChangeLimitJpaRepository.deleteAll();
    Assertions.assertThat(userEntityChangeLimitJpaRepository.count()).isZero();
  }

  @ParameterizedTest
  @MethodSource("resoureClass")
  @Order(3)
  @DisplayName("Admin user deletes all data of most entities")
  <T extends BaseID<Integer>> void deleteAllEntities(String resourceMap, Class<T> clazz,
      JpaRepository<?, ?> jpaRepository) {

    if (jpaRepository == null) {
      ParameterizedTypeReference<List<T>> listRef = listTypeRef(clazz);

      List<T> all = authenticatedClient(RestTestHelper.ADMIN)
          .get()
          .uri(resourceMap + "/")
          .exchange()
          .expectStatus().isOk()
          .expectBody(listRef)
          .returnResult()
          .getResponseBody();

      for (T baseID : all) {
        authenticatedClient(RestTestHelper.ADMIN)
            .delete()
            .uri(resourceMap + "/" + baseID.getId())
            .exchange()
            .expectStatus().isOk();
      }

      List<T> remaining = authenticatedClient(RestTestHelper.ADMIN)
          .get()
          .uri(resourceMap + "/")
          .exchange()
          .expectStatus().isOk()
          .expectBody(listRef)
          .returnResult()
          .getResponseBody();

      Assertions.assertThat(remaining).isEmpty();
    } else {
      jpaRepository.deleteAll();
      Assertions.assertThat(jpaRepository.count()).isZero();
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static <T> ParameterizedTypeReference<List<T>> listTypeRef(Class<T> clazz) {
    return (ParameterizedTypeReference) ParameterizedTypeReference
        .forType(org.springframework.core.ResolvableType.forClassWithGenerics(List.class, clazz).getType());
  }

  private Stream<Arguments> resoureClass() {
    return Stream.of(
      Arguments.of(RequestGTMappings.SECURITY_MAP, Security.class, securityJpaRepository),
      Arguments.of(RequestGTMappings.STOCKEXCHANGE_MAP, Stockexchange.class, null),
      Arguments.of(RequestGTMappings.ASSETCLASS_MAP, Assetclass.class, null)
    );
  }
}
