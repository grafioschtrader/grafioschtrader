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

import grafiosch.entities.ProposeChangeField;
import grafioschtrader.entities.ImportTransactionPlatform;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class ImportTransactionPlatformResourceTest extends BaseIntegrationTest {

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/generated/imptransplatform.csv", encoding = "UTF-8", nullValues = { "\\N" }, delimiter = '|')
  @DisplayName("Users create some ImportTransactionPlatforms")
  void createTest(String name, String idCsvImportImplementation)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    ImportTransactionPlatform itp = new ImportTransactionPlatform();
    itp.setName(name);
    itp.setIdCsvImportImplementation(idCsvImportImplementation);

    ImportTransactionPlatform created = authenticatedClient(RestTestHelper.ALLEDIT)
        .post()
        .uri(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP)
        .body(itp)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ImportTransactionPlatform.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    assertThat(created.getIdTransactionImportPlatform()).isGreaterThan(0);
    List<ProposeChangeField> diff = RestTestHelper.getDiffPropertiesOfTwoObjects(itp, created);
    assertThat(diff).isEmpty();
  }

}
