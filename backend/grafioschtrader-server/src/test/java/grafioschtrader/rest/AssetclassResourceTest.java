package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import grafiosch.entities.ProposeChangeField;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class AssetclassResourceTest extends BaseIntegrationTest  {

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/generated/assetclasses.csv", encoding = "UTF-8", delimiter = '|')
  @DisplayName("Users create some Assetclasses (e2e='i' rows; 'e' rows skipped for frontend Playwright test)")
  void createTest(Byte assetClassType, Byte specialInvestmentInstrument, String subCategoryDE, String subCategoryEN,
      String e2e) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Assumptions.assumeTrue("i".equals(e2e), "Row reserved for frontend e2e test: " + subCategoryEN);

    Assetclass a = new Assetclass(AssetclassType.getAssetClassTypeByValue(assetClassType),
        SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument), subCategoryDE,
        subCategoryEN);

    Assetclass aNew = authenticatedClient(RestTestHelper.getRadomUser())
        .post()
        .uri(RequestGTMappings.ASSETCLASS_MAP)
        .body(a)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Assetclass.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(aNew);
    assertThat(aNew.getIdAssetClass()).isGreaterThan(0);
    List<ProposeChangeField> pcf = RestTestHelper.getDiffPropertiesOfTwoObjects(a, aNew);
    assertThat(pcf).isEmpty();
  }

}
