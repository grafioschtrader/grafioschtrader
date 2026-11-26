package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.repository.UserJpaRepository;
import grafioschtrader.dashboard.PerformanceTotalChartDashboardHandler;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.BenchmarkChartPoint;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IDailyExternalFlow;
import grafioschtrader.reportviews.performance.PerformanceBenchmarkResult;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.BenchmarkSource;
import grafioschtrader.types.TransactionType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The consolidated client view of the benchmark comparison against booked data of the test database: the two
 * portfolios of {@code alledit}, each with a benchmark of its own, a client benchmark in a foreign currency, and a
 * transfer between the portfolios that is value-neutral for the client.
 *
 * <p>
 * The test books its own transfer of 20'000 EUR from the EUR account of CornèrTrader to that of SaxoTrader in June
 * 2025 and deletes it again at the end; the client has no other external flow from May to July 2025, the window of the
 * test. For the client the transfer is no external flow, for each portfolio it is one. The test shows that the client
 * view resolves the client benchmark rather than one of the portfolios, that the transfer moves neither its comparison
 * nor its shadow portfolio, that the shadow portfolio of a portfolio pays out the transfer, and that the shadow line of
 * the dashboard card agrees with the shadow portfolio of the report on the same days.
 * </p>
 *
 * <p>
 * Benchmarks are taken by natural key: S&amp;P 500 (USD, converted into CHF) for the client, SMIM PR (CHF) for
 * SaxoTrader and the currency pair EUR/CHF for CornèrTrader. SaxoTrader holds cash only, so it has no trading calendar
 * and no period performance of its own; for it the test checks the resolved benchmark and the flow. Run explicitly with
 * {@code -Dgt.report.integration=true} after a roundtrip; the e2e backend must be stopped so only this test application
 * owns {@code grafioschtrader_t}, like {@link SecurityPeriodPerformanceIntegrationTest}.
 * </p>
 */
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "gt.report.integration", matches = "true")
class PerformanceBenchmarkConsolidationIntegrationTest extends BaseIntegrationTest {

  private static final String SAXO = "SaxoTrader";
  private static final String CORNER = "CornèrTrader";
  private static final double TRANSFER_EUR = 20_000;
  /** Both sides round amounts to two digits once. */
  private static final double MONEY = 0.05;
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private PerformanceTotalChartDashboardHandler totalChartHandler;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private User user;
  private LocalDate today;
  private Integer saxo;
  private Integer corner;
  private Integer clientBenchmark;
  private Integer saxoBenchmark;
  private Integer cornerBenchmark;
  private LocalDate from;
  private LocalDate to;
  private LocalDate transferDay;
  private Integer withdrawalId;

  @BeforeAll
  void bookTransferAndAssignBenchmarks() throws Exception {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    user = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser).orElseThrow();
    today = LocalDate.now(ZoneId.of("UTC"));
    List<Map<String, Object>> portfolios = authenticatedClient(RestTestHelper.ALLEDIT).get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant").exchange().expectStatus().isOk().expectBody(JSON_ARRAY)
        .returnResult().getResponseBody();
    saxo = (Integer) portfolio(portfolios, SAXO).get("idPortfolio");
    corner = (Integer) portfolio(portfolios, CORNER).get("idPortfolio");
    clientBenchmark = securityJpaRepository.findByIsinAndCurrency("US78378X1072", "USD").getIdSecuritycurrency();
    saxoBenchmark = securityJpaRepository.findByIsinAndCurrency("CH0019399838", "CHF").getIdSecuritycurrency();
    cornerBenchmark = currencypairJpaRepository.findByFromCurrencyAndToCurrency("EUR", "CHF").getIdSecuritycurrency();
    from = validDay(LocalDate.of(2025, 5, 2));
    to = validDay(LocalDate.of(2025, 7, 31));
    transferDay = validDay(LocalDate.of(2025, 6, 11));
    assertThat(holdCashaccountDepositJpaRepository.getDailyExternalFlowsByTenant(user.getIdTenant(), from, to))
        .as("the window must hold no external flow of the client before the transfer is booked").isEmpty();
    withdrawalId = bookTransfer(cashaccount(portfolio(portfolios, CORNER), "CT EUR"),
        cashaccount(portfolio(portfolios, SAXO), "ST EUR"));
    assign(null, clientBenchmark);
    assign(saxo, saxoBenchmark);
    assign(corner, cornerBenchmark);
  }

  @AfterAll
  void deleteTransferAndRemoveBenchmarks() {
    if (withdrawalId != null) {
      authenticatedClient(RestTestHelper.ALLEDIT).delete().uri(RequestGTMappings.TRANSACTION_MAP + "/" + withdrawalId)
          .exchange().expectStatus().is2xxSuccessful();
    }
    for (Integer idPortfolio : new Integer[] { saxo, corner, null }) {
      authenticatedClient(RestTestHelper.ALLEDIT).delete()
          .uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP + (idPortfolio == null ? "" : "?idPortfolio=" + idPortfolio))
          .exchange().expectStatus().isNoContent();
    }
  }

  @Test
  @DisplayName("Client view: client benchmark, the transfer is no flow, the shadow follows the index")
  void clientViewIgnoresTheTransferBetweenItsPortfolios() throws Exception {
    assertThat(holdCashaccountDepositJpaRepository.getDailyExternalFlowsByTenant(user.getIdTenant(), from, to))
        .as("the transfer cancels in the client view").isEmpty();

    WeekYear split = split(from, to);
    PerformanceBenchmarkResult benchmark = inSession(() -> performanceReport
        .getPeriodPerformanceByTenant(user.getIdTenant(), "de", today, from, to, split, true, null)).getBenchmark();
    assertThat(benchmark.idSecuritycurrency()).isEqualTo(clientBenchmark);
    assertThat(benchmark.source()).isEqualTo(BenchmarkSource.BENCHMARK_SOURCE_TENANT);
    assertThat(benchmark.fxPair()).as("the USD index is converted into CHF").isNotNull();
    assertThat(benchmark.metrics()).isNotNull();

    // Without any flow the shadow portfolio only follows the index: S_e = S_k * b_e / b_k
    var shadow = benchmark.shadow();
    double expectedEnd = shadow.valueStartMC() * (1 + benchmark.metrics().benchmarkTwrPercent() / 100);
    assertThat(shadow.valueEndMC()).isCloseTo(expectedEnd, within(shadow.valueStartMC() * 6e-5));
    assertThat(shadow.gainMC()).isCloseTo(shadow.valueEndMC() - shadow.valueStartMC(), within(MONEY));
  }

  @Test
  @DisplayName("Portfolio views: own benchmark each, the transfer is an outflow of one and an inflow of the other")
  void portfolioViewsSeeTheTransferAsAFlow() throws Exception {
    double saxoFlow = flowOnTransferDay(holdCashaccountDepositJpaRepository.getDailyExternalFlowsByPortfolio(saxo, from,
        to));
    double cornerFlow = flowOnTransferDay(
        holdCashaccountDepositJpaRepository.getDailyExternalFlowsByPortfolio(corner, from, to));
    assertThat(saxoFlow).as("20'000 EUR received, in CHF").isPositive();
    assertThat(cornerFlow).as("the same amount sent").isCloseTo(-saxoFlow, within(MONEY));

    Map<String, Object> stored = authenticatedClient(RestTestHelper.ALLEDIT).get()
        .uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP + "?idPortfolio=" + saxo).exchange().expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertThat(stored.get("idSecuritycurrency")).isEqualTo(saxoBenchmark);
    assertThat(stored.get("source")).isEqualTo(BenchmarkSource.BENCHMARK_SOURCE_PORTFOLIO.name());

    WeekYear split = split(from, to);
    PerformanceBenchmarkResult benchmark = inSession(() -> performanceReport
        .getPeriodPerformanceByPortfolio(user.getIdTenant(), "de", today, corner, from, to, split, true, null))
        .getBenchmark();
    assertThat(benchmark.idSecuritycurrency()).isEqualTo(cornerBenchmark);
    assertThat(benchmark.source()).isEqualTo(BenchmarkSource.BENCHMARK_SOURCE_PORTFOLIO);
    var shadow = benchmark.shadow();
    assertThat(shadow.shortfallDate()).isNull();
    // The shadow pays out the transfer: its gain excludes the flow, as the gain of the portfolio does
    assertThat(shadow.gainMC()).isCloseTo(shadow.valueEndMC() - shadow.valueStartMC() - cornerFlow, within(MONEY));
  }

  @Test
  @DisplayName("The shadow line of the dashboard card agrees with the shadow portfolio of the report on every day")
  void dashboardShadowAgreesWithTheReport() throws Exception {
    JsonNode chart = inSession(() -> totalChartHandler
        .load(user, JsonMapper.builder().build().readTree("{\"range\":\"CHART_RANGE_1Y\"}")).custom());
    assertThat(chart.path("benchmarkName").isString()).as("the client benchmark resolves on the card").isTrue();
    assertThat(chart.path("sampling").asString()).isEqualTo("DAY");
    LocalDate chartFrom = LocalDate.parse(chart.path("benchmarkStartDate").asString());
    LocalDate chartTo = LocalDate.parse(chart.path("newestDate").asString());

    WeekYear split = split(chartFrom, chartTo);
    PerformancePeriod period = inSession(() -> performanceReport.getPeriodPerformanceByTenant(user.getIdTenant(), "de",
        today, chartFrom, chartTo, split, true, null));
    Map<LocalDate, Double> reportShadow = new HashMap<>();
    for (BenchmarkChartPoint point : period.getBenchmark().chart()) {
      if (point.shadowValueMC() != null) {
        reportShadow.put(point.date(), point.shadowValueMC());
      }
    }
    int compared = 0;
    for (JsonNode point : chart.path("points")) {
      LocalDate date = LocalDate.parse(point.path("date").asString());
      Double expected = reportShadow.get(date);
      if (point.path("shadowValueMC").isNumber() && expected != null) {
        assertThat(point.path("shadowValueMC").asDouble()).as(date.toString()).isCloseTo(expected, within(MONEY));
        compared++;
      }
    }
    assertThat(compared).as("days both show").isGreaterThan(200);
  }

  /**
   * Runs a call inside a transaction, as the session of an HTTP request does in operation; the report and the card read
   * the tenant through a lazy reference.
   */
  private <T> T inSession(Callable<T> call) {
    return new TransactionTemplate(transactionManager).execute(_ -> {
      try {
        return call.call();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  private Integer bookTransfer(Cashaccount debit, Cashaccount credit) {
    Transaction withdrawal = new Transaction(debit, TRANSFER_EUR, TransactionType.WITHDRAWAL,
        transferDay.atTime(12, 0));
    Transaction deposit = new Transaction(credit, TRANSFER_EUR, TransactionType.DEPOSIT,
        withdrawal.getTransactionTime());
    CashAccountTransfer created = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/cashaccounttransfer")
        .body(new CashAccountTransfer(withdrawal, deposit)).exchange().expectStatus().isOk()
        .expectBody(CashAccountTransfer.class).returnResult().getResponseBody();
    return created.getWithdrawalTransaction().getIdTransaction();
  }

  private void assign(Integer idPortfolio, Integer idSecuritycurrency) {
    Map<String, Object> body = new HashMap<>();
    body.put("idPortfolio", idPortfolio);
    body.put("idSecuritycurrency", idSecuritycurrency);
    authenticatedClient(RestTestHelper.ALLEDIT).put().uri(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP).body(body)
        .exchange().expectStatus().isOk();
  }

  private static Map<String, Object> portfolio(List<Map<String, Object>> portfolios, String name) {
    return portfolios.stream().filter(p -> name.equals(p.get("name"))).findFirst()
        .orElseThrow(() -> new IllegalStateException("portfolio " + name + " of alledit required, run a roundtrip"));
  }

  @SuppressWarnings("unchecked")
  private static Cashaccount cashaccount(Map<String, Object> portfolio, String name) {
    Map<String, Object> account = ((List<Map<String, Object>>) portfolio.get("cashaccountList")).stream()
        .filter(a -> name.equals(a.get("name"))).findFirst()
        .orElseThrow(() -> new IllegalStateException("cash account " + name + " required"));
    return new Cashaccount((Integer) account.get("idSecuritycashAccount"), (String) account.get("currency"));
  }

  private double flowOnTransferDay(List<IDailyExternalFlow> flows) {
    return flows.stream().filter(f -> f.getFlowDate().equals(transferDay)).mapToDouble(IDailyExternalFlow::getFlowMC)
        .sum();
  }

  /** The nearest earlier day that the client view and CornèrTrader can value. */
  private LocalDate validDay(LocalDate candidate) throws Exception {
    List<FirstAndMissingTradingDays> calendars = List.of(
        performanceReport.getFirstAndMissingTradingDaysByTenant(user.getIdTenant(), today),
        performanceReport.getFirstAndMissingTradingDaysByPortfolio(user.getIdTenant(), corner));
    LocalDate day = candidate;
    while (!valuable(day, calendars)) {
      day = day.minusDays(1);
    }
    return day;
  }

  private static boolean valuable(LocalDate day, List<FirstAndMissingTradingDays> calendars) {
    return day.getDayOfWeek().getValue() <= 5 && calendars.stream().noneMatch(c -> c.isMissingQuoteDayOrHoliday(day));
  }

  private WeekYear split(LocalDate dateFrom, LocalDate dateTo) throws Exception {
    FirstAndMissingTradingDays days = performanceReport.getFirstAndMissingTradingDaysByTenant(user.getIdTenant(),
        today);
    return ChronoUnit.MONTHS.between(dateFrom, dateTo) < days.minIncludeMonthLimit ? WeekYear.WM_WEEK
        : WeekYear.WM_YEAR;
  }
}
