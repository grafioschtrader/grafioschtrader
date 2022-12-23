package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
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
import org.springframework.http.ResponseEntity;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class AssetclassResourceTest {

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
  @CsvFileSource(resources = "/assetclasses.csv", encoding = "UTF-8")
  @DisplayName("Users create some Assetclasses")
  void createTest(Byte assetClassType, Byte specialInvestmentInstrument, String subCategoryDE, String subCategoryEN)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Assetclass a = new Assetclass(AssetclassType.getAssetClassTypeByValue(assetClassType),
        SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument), subCategoryDE,
        subCategoryEN);
    ResponseEntity<Assetclass> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.ASSETCLASS_MAP + "/", port), HttpMethod.POST,
        RestTestHelper.getHttpEntity(RestTestHelper.getRadomUser(), a), Assetclass.class);
    assertNotNull(response);
    Assetclass aNew = response.getBody();
    assertThat(aNew.getIdAssetClass()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(a, aNew);
    assertThat(pcf).isEmpty();
  }

}
