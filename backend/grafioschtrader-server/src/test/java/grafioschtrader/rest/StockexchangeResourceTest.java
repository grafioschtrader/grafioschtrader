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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import grafiosch.entities.ProposeChangeField;
import grafiosch.error.SecurityBreachError;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class StockexchangeResourceTest extends BaseIntegrationTest {

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/stockexchanges.csv", encoding = "UTF-8", nullValues = { "\\N" })
  @DisplayName("Users create some Stockexchanges - WebTestClient version")
  void createTestWithWebTestClient(String mic, String name, String countryCode, boolean noMarketValue,
      boolean secondaryMarket, LocalTime timeOpen, LocalTime timeClose, String timeZone, String website)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Stockexchange s = new Stockexchange(mic, name, countryCode, noMarketValue, secondaryMarket, timeOpen, timeClose,
        timeZone, website);

    // Using WebTestClient with fluent API
    Stockexchange sNew = authenticatedClient(RestTestHelper.getRadomUser())
        .post()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP)
        .bodyValue(s)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Stockexchange.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(sNew);
    assertThat(sNew.getIdStockexchange()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(s, sNew);
    assertThat(pcf).isEmpty();
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/stockexchanges.csv", encoding = "UTF-8", nullValues = { "\\N" })
  @DisplayName("Users create some Stockexchanges - TestRestTemplate version (legacy)")
  void createTest(String mic, String name, String countryCode, boolean noMarketValue, boolean secondaryMarket,
      LocalTime timeOpen, LocalTime timeClose, String timeZone, String website)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Stockexchange s = new Stockexchange(mic, name, countryCode, noMarketValue, secondaryMarket, timeOpen, timeClose,
        timeZone, website);
    ResponseEntity<Stockexchange> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestGTMappings.STOCKEXCHANGE_MAP, port), HttpMethod.POST,
        RestTestHelper.getHttpEntity(RestTestHelper.getRadomUser(), s), Stockexchange.class);
    assertNotNull(response);
    Stockexchange sNew = response.getBody();
    assertThat(sNew.getIdStockexchange()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(s, sNew);
    assertThat(pcf).isEmpty();
  }

  @Test
  @Order(5)
  @DisplayName("Limited user can not delete Stockexchange from other user - WebTestClient version")
  void deleteByIdTestWithWebTestClient() {
    // First, fetch all stockexchanges
    Stockexchange[] stockexchanges = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(uriBuilder -> uriBuilder
            .path(RequestGTMappings.STOCKEXCHANGE_MAP)
            .queryParam("includeNameOfCalendarIndex", false)
            .build())
        .exchange()
        .expectStatus().isOk()
        .expectBody(Stockexchange[].class)
        .returnResult()
        .getResponseBody();

    assertThat(stockexchanges.length).isGreaterThan(0);
    Stockexchange s = stockexchanges[0];

    // Try to delete a stockexchange - should fail with UNAUTHORIZED
    authenticatedClient(RestTestHelper.LIMIT1)
        .delete()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP + "/" + s.getIdStockexchange())
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains(SecurityBreachError.class.getSimpleName()));
  }

  @Test
  @Order(5)
  @DisplayName("Limited user can not delete Stockexchange from other user - TestRestTemplate version (legacy)")
  void deleteByIdTest() {
    String url = RestTestHelper.createURLWithPort(RequestGTMappings.STOCKEXCHANGE_MAP, port)
        + "?includeNameOfCalendarIndex=false";

    ResponseEntity<Stockexchange[]> response = restTemplate.exchange(url, HttpMethod.GET,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Stockexchange[].class);

    assertThat(response.getBody().length).isGreaterThan(0);
    Stockexchange s = response.getBody()[0];

    String entityUrl = RestTestHelper.createURLWithPort(RequestGTMappings.STOCKEXCHANGE_MAP, port) + "/"
        + s.getIdStockexchange();

    ResponseEntity<?> error = restTemplate.exchange(entityUrl, HttpMethod.DELETE,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Object.class);

    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(error.getBody().toString()).contains(SecurityBreachError.class.getSimpleName());
  }

}
