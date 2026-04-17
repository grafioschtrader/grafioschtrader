package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import grafiosch.types.Language;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.types.TradingPlatformFeePlan;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class TradingPlatformPlanResourceTest extends BaseIntegrationTest {

  private Map<String, ImportTransactionPlatform> platformByName;

  /**
   * Fetches the ImportTransactionPlatform rows created by the sibling {@code ImportTransactionPlatformResourceTest} so
   * this test can resolve by name. Relies on Surefire's alphabetical class ordering ('I' &lt; 'T') for that prerequisite
   * to run first.
   */
  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    ImportTransactionPlatform[] existing = authenticatedClient(RestTestHelper.ALLEDIT)
        .get().uri(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP)
        .exchange().expectStatus().isOk()
        .expectBody(ImportTransactionPlatform[].class).returnResult().getResponseBody();
    platformByName = new HashMap<>();
    if (existing != null) {
      for (ImportTransactionPlatform p : existing) {
        platformByName.put(p.getName(), p);
      }
    }
  }

  @Order(4)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/generated/tradingplatformplan.csv", encoding = "UTF-8", nullValues = { "\\N" }, delimiter = '|')
  @DisplayName("Users create TradingPlatformPlans (e2e='i' rows; 'e' rows skipped for frontend Playwright test)")
  void createTest(Byte transactionFeePlan, String importPlatformName, String platformPlanNameDE,
      String platformPlanNameEN, String e2e)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Assumptions.assumeTrue("i".equals(e2e), "Row reserved for frontend e2e test: " + platformPlanNameEN);

    TradingPlatformPlan tpp = new TradingPlatformPlan();
    tpp.setTransactionFeePlan(TradingPlatformFeePlan.getTradingPlatformFeePlan(transactionFeePlan));
    if (importPlatformName != null) {
      ImportTransactionPlatform platform = platformByName.get(importPlatformName);
      assertNotNull(platform, "Import platform '" + importPlatformName + "' missing. "
          + "Ensure ImportTransactionPlatformResourceTest runs before this test (class ordering: I < T).");
      tpp.setImportTransactionPlatform(platform);
    }
    tpp.setPlatformPlanNameByLanguage(platformPlanNameDE, Language.GERMAN);
    tpp.setPlatformPlanNameByLanguage(platformPlanNameEN, Language.ENGLISH);

    TradingPlatformPlan created = authenticatedClient(RestTestHelper.ALLEDIT)
        .post()
        .uri(RequestGTMappings.TRADINGPLATFORMPLAND_MAP)
        .body(tpp)
        .exchange()
        .expectStatus().isOk()
        .expectBody(TradingPlatformPlan.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    assertThat(created.getIdTradingPlatformPlan()).isGreaterThan(0);
    List<ProposeChangeField> diff = RestTestHelper.getDiffPropertiesOfTwoObjects(tpp, created);
    assertThat(diff).isEmpty();
  }

}
