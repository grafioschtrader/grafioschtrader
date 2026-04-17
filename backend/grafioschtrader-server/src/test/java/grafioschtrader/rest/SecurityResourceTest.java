package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.CsvFileSource;

import grafiosch.types.Language;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class SecurityResourceTest extends BaseIntegrationTest  {

  private static List<Assetclass> assetclasses;
  private static List<Stockexchange> stockexchanges;
  private static Comparator<Stockexchange> comparatorSE = (s1, s2) -> s1.getName().compareTo(s2.getName());

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(2)
  void getAllAssetclassTest() {
    Assetclass[] body = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(RequestGTMappings.ASSETCLASS_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Assetclass[].class)
        .returnResult()
        .getResponseBody();

    Optional<Assetclass> assetclassOpt = Arrays.stream(body)
        .filter(a -> a.getCategoryType() == AssetclassType.EQUITIES
            && a.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
            && a.getSubCategoryByLanguage(Language.GERMAN).equals("Aktien Schweiz"))
        .findFirst();
    assertThat(assetclassOpt).isNotEmpty();
    assetclasses = Arrays.asList(body);
  }

  @Test
  @Order(3)
  void getAllStockexchangesTest() {
    Stockexchange[] body = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP + "?includeNameOfCalendarIndex=false")
        .exchange()
        .expectStatus().isOk()
        .expectBody(Stockexchange[].class)
        .returnResult()
        .getResponseBody();

    Optional<Stockexchange> stockexchangeOpt = Arrays.stream(body)
        .filter(s -> GlobalConstants.STOCK_EX_MIC_SIX.equals(s.getMic())).findFirst();
    assertThat(stockexchangeOpt).isPresent();
    stockexchanges = Arrays.asList(body);
    stockexchanges.sort(comparatorSE);
  }

  @Order(10)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/generated/securities.csv", encoding = "UTF-8", delimiter = '|', nullValues = "\\N")
  @DisplayName("Create AT/AU securities from CSV (e2e='i')")
  void createAllSecuritiesTest(ArgumentsAccessor accessor) throws ArgumentsAggregationException {
    Assumptions.assumeTrue("i".equals(accessor.getString(21)), "row is not integration-flagged");
    // Some connectors (e.g. gt.datafeed.vienna) trigger a synchronous external HTTP fetch during
    // POST /api/security when gt.security.async.historyquotes=false, which can hang past the
    // RestTestClient's default read timeout. Skip those rows to keep the suite deterministic.
    String historyConnector = accessor.getString(16);
    String intraConnector = accessor.getString(18);
    Assumptions.assumeFalse(
        "gt.datafeed.vienna".equals(historyConnector) || "gt.datafeed.vienna".equals(intraConnector),
        "skip rows whose connector performs a slow external fetch during creation");
    Security security = new SecurityAggregator().aggregateArguments(accessor, (ParameterContext) null);

    Security created = authenticatedClient(RestTestHelper.getRadomUser())
        .post()
        .uri(RequestGTMappings.SECURITY_MAP)
        .body(security)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Security.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    assertThat(created.getIdSecuritycurrency()).isGreaterThan(0);
  }

  static class SecurityAggregator implements ArgumentsAggregator {

    final Stockexchange searchStockexchange = new Stockexchange();

    @Override
    public Security aggregateArguments(ArgumentsAccessor accessor, ParameterContext context)
        throws ArgumentsAggregationException {
      Security s = new Security();
      s.setName(accessor.getString(0));
      s.setIsin(accessor.getString(1));
      s.setTickerSymbol(accessor.getString(2));
      s.setCurrency(accessor.getString(3));
      s.setActiveFromDate(accessor.get(4, LocalDate.class));
      s.setActiveToDate(accessor.get(5, LocalDate.class));
      s.setDistributionFrequency(DistributionFrequency.getDistributionFrequency(accessor.getByte(6)));
      s.setDenomination(accessor.getInteger(7));
      s.setLeverageFactor(accessor.getFloat(8));
      searchStockexchange.setName(accessor.getString(9));
      int index = Collections.binarySearch(stockexchanges, searchStockexchange, comparatorSE);
      s.setStockexchange(stockexchanges.get(index));
      s.setAssetClass(RestTestHelper.getAssetclassBy(assetclasses, accessor.getByte(10), accessor.getString(11),
          accessor.getByte(12)));
      s.setProductLink(accessor.getString(13));
      s.setIdTenantPrivate(accessor.getInteger(14));
      s.setFormulaPrices(accessor.getString(15));
      s.setIdConnectorHistory(accessor.getString(16));
      s.setUrlHistoryExtend(accessor.getString(17));
      s.setIdConnectorIntra(accessor.getString(18));
      s.setUrlIntraExtend(accessor.getString(19));
      s.setNote(accessor.getString(20));
      return s;
    }

  }

}
