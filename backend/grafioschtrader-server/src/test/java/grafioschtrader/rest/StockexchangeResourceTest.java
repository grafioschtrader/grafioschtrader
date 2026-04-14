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
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/stockexchanges.csv", encoding = "UTF-8", nullValues = { "\\N" })
  @DisplayName("Users create some Stockexchanges")
  void createTest(String mic, String name, String countryCode, boolean noMarketValue,
      boolean secondaryMarket, LocalTime timeOpen, LocalTime timeClose, String timeZone, String website)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Stockexchange s = new Stockexchange(mic, name, countryCode, noMarketValue, secondaryMarket, timeOpen, timeClose,
        timeZone, website);

    Stockexchange sNew = authenticatedClient(RestTestHelper.getRadomUser())
        .post()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP)
        .body(s)
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

  @Test
  @Order(5)
  @DisplayName("Limited user can not delete Stockexchange from other user")
  void deleteByIdTest() {
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

    authenticatedClient(RestTestHelper.LIMIT1)
        .delete()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP + "/" + s.getIdStockexchange())
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains(SecurityBreachError.class.getSimpleName()));
  }

}
