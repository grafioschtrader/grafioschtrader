package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

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
import org.junit.jupiter.params.provider.CsvFileSource;

import grafiosch.entities.ProposeChangeField;
import grafioschtrader.entities.ImportTransactionPlatform;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class ImportTransactionPlatformResourceTest extends BaseIntegrationTest {

  private static final String GT_PLATFORM_NAME = "Grafioschtrader";
  private static final String ALTERNATIVE_PLATFORM_NAME = "Migros Bank";
  private static final String GT_PLATFORM_URI = RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP + "/gtplatform";

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/generated/imptransplatform.csv", encoding = "UTF-8", nullValues = {
      "\\N" }, delimiter = '|')
  @DisplayName("Users create some ImportTransactionPlatforms")
  void createTest(String name, String idCsvImportImplementation)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    ImportTransactionPlatform itp = new ImportTransactionPlatform();
    itp.setName(name);
    itp.setIdCsvImportImplementation(idCsvImportImplementation);

    ImportTransactionPlatform created = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP).body(itp).exchange().expectStatus().isOk()
        .expectBody(ImportTransactionPlatform.class).returnResult().getResponseBody();

    assertNotNull(created);
    Assertions.assertThat(created.getIdTransactionImportPlatform()).isGreaterThan(0);
    List<ProposeChangeField> diff = RestTestHelper.getDiffPropertiesOfTwoObjects(itp, created);
    Assertions.assertThat(diff).isEmpty();
  }

  @Order(5)
  @Test
  @DisplayName("Only an administrator may configure the instance-wide Grafioschtrader import platform")
  void configureGtImportPlatform() {
    ImportTransactionPlatform[] platforms = authenticatedClient(RestTestHelper.ALLEDIT).get()
        .uri(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP).exchange().expectStatus().isOk()
        .expectBody(ImportTransactionPlatform[].class).returnResult().getResponseBody();
    Assertions.assertThat(platforms).isNotNull();

    Integer idGtPlatform = resolvePlatformId(platforms, GT_PLATFORM_NAME);
    Integer idAlternativePlatform = resolvePlatformId(platforms, ALTERNATIVE_PLATFORM_NAME);

    authenticatedClient(RestTestHelper.LIMIT1).put()
        .uri(GT_PLATFORM_URI + "?idTransactionImportPlatform=" + idGtPlatform).exchange().expectStatus()
        .isForbidden();

    authenticatedClient(RestTestHelper.ADMIN).put()
        .uri(GT_PLATFORM_URI + "?idTransactionImportPlatform=" + Integer.MAX_VALUE).exchange().expectStatus()
        .isBadRequest();

    authenticatedClient(RestTestHelper.ADMIN).put().uri(GT_PLATFORM_URI).exchange().expectStatus().isOk();
    authenticatedClient(RestTestHelper.USER).get().uri(GT_PLATFORM_URI).exchange().expectStatus().isOk().expectBody()
        .isEmpty();

    authenticatedClient(RestTestHelper.ADMIN).put()
        .uri(GT_PLATFORM_URI + "?idTransactionImportPlatform=" + idAlternativePlatform).exchange().expectStatus().isOk()
        .expectBody(Integer.class).isEqualTo(idAlternativePlatform);
    authenticatedClient(RestTestHelper.USER).get().uri(GT_PLATFORM_URI).exchange().expectStatus().isOk()
        .expectBody(Integer.class).isEqualTo(idAlternativePlatform);
  }

  private Integer resolvePlatformId(ImportTransactionPlatform[] platforms, String name) {
    List<ImportTransactionPlatform> matches = List.of(platforms).stream()
        .filter(platform -> name.equals(platform.getName())).toList();
    Assertions.assertThat(matches).as("import transaction platform resolved by name: %s", name).hasSize(1);
    return matches.getFirst().getIdTransactionImportPlatform();
  }

}
