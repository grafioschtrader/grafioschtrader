package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.BaseID;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.UserEntityChangeCountJpaRepository;
import grafioschtrader.repository.UserEntityChangeLimitJpaRepository;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class DeleteALLDataTest {

  @Autowired
  private TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  @Autowired
  private UserEntityChangeLimitJpaRepository userEntityChangeLimitJpaRepository;
  
  
  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
    System.out.println("**********************");
  }

  @Test
  @Order(1)
  @DisplayName("Delete user entity change counter for all users")
  void deleteUserEntityChangeCounter() {
    userEntityChangeCountJpaRepository.deleteAll();
    assertThat(userEntityChangeCountJpaRepository.count()).isEqualTo(0);
  }

  @Test
  @Order(2)
  @DisplayName("Delete user entity change limit for all users")
  void deleteUserEntityChangeLimit() {
    userEntityChangeLimitJpaRepository.deleteAll();
    assertThat(userEntityChangeLimitJpaRepository.count()).isEqualTo(0);
  }

  @ParameterizedTest
  @MethodSource("resoureClass")
  @Order(3)
  @DisplayName("Admin user deletes all data of most entities")
  <T extends BaseID> void deleteAllEntities(String resourceMap, final Class<T> clazz,
      JpaRepository<?, ?> jpaRepository) {

    if (jpaRepository == null) {
      // Get all entities
      ResponseEntity<List<T>> response = restTemplate.exchange(
          RestTestHelper.createURLWithPort(resourceMap + "/", port), HttpMethod.GET,
          RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, null),
          ParameterizedTypeReference.forType(ResolvableType.forClassWithGenerics(List.class, clazz).getType()));
      // Delete every single entity
      for (T baseID : response.getBody()) {
        String entityUrl = RestTestHelper.createURLWithPort(resourceMap + "/", port) + ((BaseID) baseID).getId();
        restTemplate.exchange(entityUrl, HttpMethod.DELETE, RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, null),
            BaseID.class);
      }

      response = restTemplate.exchange(RestTestHelper.createURLWithPort(resourceMap + "/", port), HttpMethod.GET,
          RestTestHelper.getHttpEntity(RestTestHelper.ADMIN, null),
          ParameterizedTypeReference.forType(ResolvableType.forClassWithGenerics(List.class, clazz).getType()));
      assertThat(response.getBody().size()).isEqualByComparingTo(0);
    } else {
      jpaRepository.deleteAll();
      long count = jpaRepository.count();
      assertThat(count).isEqualByComparingTo(0L);
      
    }

  }

  private Stream<Arguments> resoureClass() {
    return Stream.of(Arguments.of(RequestMappings.SECURITY_MAP, Security.class, securityJpaRepository),
        Arguments.of(RequestMappings.STOCKEXCHANGE_MAP, Stockexchange.class, null),
        Arguments.of(RequestMappings.ASSETCLASS_MAP, Assetclass.class, null));
  }

}
