package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class SecurityResourceTest {

  private static List<Assetclass> assetclasses;
  private static List<Stockexchange> stockexchanges;
  private static Comparator<Stockexchange> comparatorSE = (s1, s2) -> s1.getName().compareTo(s2.getName());
  
  @Autowired
  TestRestTemplate restTemplate = new TestRestTemplate();

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenHandler jwtTokenHandler;
  private Assetclass assetclass;
  private Stockexchange stockexchange;
  private Security security;
  
  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTemplate, port, jwtTokenHandler);
  }

  @Test
  @Order(2)
  void getAllAssetclassTest() {
    ResponseEntity<Assetclass[]> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.ASSETCLASS_MAP + "/", port), HttpMethod.GET,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Assetclass[].class);

    Optional<Assetclass> assetclassOpt = Arrays.stream(response.getBody())
        .filter(a -> a.getCategoryType() == AssetclassType.EQUITIES
            && a.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
            && a.getSubCategoryByLanguage(Language.GERMAN).equals("Aktien Schweiz"))
        .findFirst();
    assertThat(assetclassOpt).isNotEmpty();
    assetclass = assetclassOpt.get();
    assetclasses = Arrays.asList(response.getBody());
  }

  @Test
  @Order(3)
  void getAllStockexchangesTest() {
    ResponseEntity<Stockexchange[]> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.STOCKEXCHANGE_MAP + "/", port), HttpMethod.GET,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Stockexchange[].class);

    Optional<Stockexchange> stockexchangeOpt = Arrays.stream(response.getBody())
        .filter(s -> s.getMic().equals(GlobalConstants.STOCK_EX_MIC_SIX)).findFirst();
    assertThat(stockexchangeOpt).isPresent();
    stockexchange = stockexchangeOpt.get();
    stockexchanges = Arrays.asList(response.getBody());
    stockexchanges.sort(comparatorSE);
  }

  @Test
  @Order(4)
  @DisplayName("Create security with user 'limit1'")
  void createTest() throws ParseException {
    Security s = new Security();
    s.setActiveFromDate(new SimpleDateFormat("yyyyMMdd").parse("20000101"));
    s.setActiveToDate(new SimpleDateFormat("yyyyMMdd").parse("20251231"));
    s.setAssetClass(assetclass);
    s.setCurrency("CHF");
    s.setIdConnectorHistory("gt.datafeed.six");
    s.setIdConnectorIntra("gt.datafeed.swissquote");
    s.setIsin("CH0012032048");
    s.setName("Roche Holding AG");
    s.setStockexchange(stockexchange);
    s.setTickerSymbol("ROG");
    s.setUrlHistoryExtend("CH0012032048CHF4");
    s.setUrlIntraExtend("CH0012032048");
    HttpEntity<Security> request = RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, s);

    ResponseEntity<Security> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.SECURITY_MAP + "/", port), HttpMethod.POST, request,
        Security.class);
    assertNotNull(response);
    security = response.getBody();
    assertThat(security.getIdSecuritycurrency()).isGreaterThan(0);
  }

  @Test
  @Order(5)
  @DisplayName("Delete singe security with user 'limit1'")
  void deleteByIdTest() throws ParseException {
    String entityUrl = RestTestHelper.createURLWithPort(RequestMappings.SECURITY_MAP + "/", port)
        + security.getIdSecuritycurrency();
    restTemplate.exchange(entityUrl, HttpMethod.DELETE, RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null),
        String.class);

    ResponseEntity<Security> s = restTemplate.exchange(entityUrl, HttpMethod.GET,
        RestTestHelper.getHttpEntity(RestTestHelper.LIMIT1, null), Security.class);
    assertThat(s.getBody()).isNull();
  }

  @Order(10)
  @ParameterizedTest
  @CsvFileSource(resources = "/securities.csv", encoding = "UTF-8")
  @DisplayName("Create many securites from csv")
  void createAllSecuritiesTest(@AggregateWith(SecurityAggregator.class) Security security) {
    HttpEntity<Security> request = RestTestHelper.getHttpEntity(RestTestHelper.getRadomUser(), security);
    ResponseEntity<Security> response = restTemplate.exchange(
        RestTestHelper.createURLWithPort(RequestMappings.SECURITY_MAP + "/", port), HttpMethod.POST, request,
        Security.class);
    assertNotNull(response);
    security = response.getBody();
    assertThat(security.getIdSecuritycurrency()).isGreaterThan(0);
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
      s.setActiveFromDate(DateHelper.getDateFromLocalDate(accessor.get(4, LocalDate.class)));
      s.setActiveToDate(DateHelper.getDateFromLocalDate(accessor.get(5, LocalDate.class)));
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
