package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
import org.junit.jupiter.params.aggregator.AggregateWith;
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

  private Assetclass assetclass;
  private Stockexchange stockexchange;
  private Security security;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(2)
  void getAllAssetclassTest() {
    Assetclass[] body = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(RequestGTMappings.ASSETCLASS_MAP + "/")
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
    assetclass = assetclassOpt.get();
    assetclasses = Arrays.asList(body);
  }

  @Test
  @Order(3)
  void getAllStockexchangesTest() {
    Stockexchange[] body = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP + "/")
        .exchange()
        .expectStatus().isOk()
        .expectBody(Stockexchange[].class)
        .returnResult()
        .getResponseBody();

    Optional<Stockexchange> stockexchangeOpt = Arrays.stream(body)
        .filter(s -> s.getMic().equals(GlobalConstants.STOCK_EX_MIC_SIX)).findFirst();
    assertThat(stockexchangeOpt).isPresent();
    stockexchange = stockexchangeOpt.get();
    stockexchanges = Arrays.asList(body);
    stockexchanges.sort(comparatorSE);
  }

  @Test
  @Order(4)
  @DisplayName("Create security with user 'limit1'")
  void createTest() throws ParseException {
    Security s = new Security();
    s.setActiveFromDate(LocalDate.of(2000, 1, 1));
    s.setActiveToDate(LocalDate.of(2025, 12, 31));
    s.setAssetClass(assetclass);
    s.setCurrency(GlobalConstants.MC_CHF);
    s.setIdConnectorHistory("gt.datafeed.six");
    s.setIdConnectorIntra("gt.datafeed.swissquote");
    s.setIsin("CH0012032048");
    s.setName("Roche Holding AG");
    s.setStockexchange(stockexchange);
    s.setTickerSymbol("ROG");
    s.setUrlHistoryExtend("CH0012032048CHF4");
    s.setUrlIntraExtend("CH0012032048");

    Security created = authenticatedClient(RestTestHelper.LIMIT1)
        .post()
        .uri(RequestGTMappings.SECURITY_MAP + "/")
        .body(s)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Security.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    security = created;
    assertThat(security.getIdSecuritycurrency()).isGreaterThan(0);
  }

  @Test
  @Order(5)
  @DisplayName("Delete singe security with user 'limit1'")
  void deleteByIdTest() throws ParseException {
    String entityUrl = RequestGTMappings.SECURITY_MAP + "/" + security.getIdSecuritycurrency();

    authenticatedClient(RestTestHelper.LIMIT1)
        .delete()
        .uri(entityUrl)
        .exchange();

    Security fetched = authenticatedClient(RestTestHelper.LIMIT1)
        .get()
        .uri(entityUrl)
        .exchange()
        .expectBody(Security.class)
        .returnResult()
        .getResponseBody();
    assertThat(fetched).isNull();
  }

  @Order(10)
  @ParameterizedTest
  @CsvFileSource(resources = "/testdata/securities.csv", encoding = "UTF-8")
  @DisplayName("Create many securites from csv")
  void createAllSecuritiesTest(@AggregateWith(SecurityAggregator.class) Security security) {
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
