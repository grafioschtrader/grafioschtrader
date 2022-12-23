package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.error.SecurityBreachError;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class StockexchangeResourceTest {

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

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/stockexchanges.csv", encoding = "UTF-8")
  @DisplayName("Users create some Stockexchanges")
  void createTest(String name, String mic, LocalTime timeClose, String timeZone, boolean noMarketValue,
      boolean secondaryMarket) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Stockexchange s = new Stockexchange(name, mic, timeClose, timeZone, noMarketValue, secondaryMarket);
    ResponseEntity<Stockexchange> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.STOCKEXCHANGE_MAP + "/", port), HttpMethod.POST,
        RestTestHelper.getHttpEntity(RestTestHelper.getRadomUser(), s), Stockexchange.class);
    assertNotNull(response);
    Stockexchange sNew = response.getBody();
    assertThat(sNew.getIdStockexchange()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(s, sNew);
    assertThat(pcf).isEmpty();
  }

  @Test
  @Order(5)
  @DisplayName("Limited user can not delete Stockexchange from other user")
  void deleteByIdTest() {
    ResponseEntity<Stockexchange[]> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.STOCKEXCHANGE_MAP + "/", port), HttpMethod.GET,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Stockexchange[].class);
    assertThat(response.getBody().length).isGreaterThan(0);
    Stockexchange s = response.getBody()[0];

    String entityUrl = RestTestHelper.createURLWithPort(RequestMappings.STOCKEXCHANGE_MAP + "/", port)
        + s.getIdStockexchange();
    ResponseEntity<?> error = restTemplate.exchange(entityUrl, HttpMethod.DELETE,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Object.class);
    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(error.getBody().toString()).contains(SecurityBreachError.class.getSimpleName());

  }

}
