package grafioschtrader.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.entities.User;
import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.BenchmarkSeriesLoader;
import grafioschtrader.reports.BenchmarkSeriesLoader.LoadedBenchmark;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.PerformanceTotalChart;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.Sampling;
import grafioschtrader.reportviews.performance.BenchmarkDescriptor;
import grafioschtrader.reportviews.performance.BenchmarkSeries;
import grafioschtrader.reportviews.performance.BenchmarkSeries.Quote;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepositoryCustom.HoldDailyTotalSeries;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.PerformanceBenchmarkService;
import grafioschtrader.service.PerformanceBenchmarkService.ResolvedBenchmark;
import grafioschtrader.types.BenchmarkReturnBasis;
import grafioschtrader.types.BenchmarkSource;
import grafioschtrader.types.DashboardChartRange;
import grafioschtrader.types.TenantKindType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The total value chart only reads stored daily values, so its rules are tested without a database. */
class PerformanceTotalChartDashboardHandlerTest {

  private static final int ID_TENANT = 7;
  private static final LocalDate START = LocalDate.of(2020, 1, 6);

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final HoldDailyTotalJpaRepository holdDailyTotals = mock(HoldDailyTotalJpaRepository.class);
  private final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  private final PortfolioJpaRepository portfolios = mock(PortfolioJpaRepository.class);
  private final PerformanceBenchmarkService benchmarks = mock(PerformanceBenchmarkService.class);
  private final BenchmarkSeriesLoader benchmarkLoader = mock(BenchmarkSeriesLoader.class);
  private final HoldCashaccountDepositJpaRepository deposits = mock(HoldCashaccountDepositJpaRepository.class);
  private final PerformanceTotalChartDashboardHandler handler = new PerformanceTotalChartDashboardHandler(
      holdDailyTotals, tenants, portfolios, mapper, benchmarks, benchmarkLoader, deposits);
  private final User user = mock(User.class);
  private final Tenant tenant = new Tenant();

  @BeforeEach
  void setup() {
    tenant.setIdTenant(ID_TENANT);
    tenant.setCurrency("CHF");
    tenant.setTenantKindType(TenantKindType.MAIN);
    when(user.getIdTenant()).thenReturn(ID_TENANT);
    when(tenants.getReferenceById(ID_TENANT)).thenReturn(tenant);
  }

  @Test
  @DisplayName("A single read may change the portfolio and the period, and nothing else")
  void validateTransientSettings() {
    for (String valid : new String[] { "{\"idPortfolio\":null}", "{\"idPortfolio\":5}",
        "{\"range\":\"CHART_RANGE_MAX\"}", "{\"idPortfolio\":5,\"range\":\"CHART_RANGE_YTD\"}" }) {
      assertThatCode(() -> handler.validateTransient(json(valid))).as(valid).doesNotThrowAnyException();
    }
    for (String invalid : new String[] { "{}", "{\"idPortfolio\":\"5\"}", "{\"range\":\"X\"}",
        "{\"idPortfolio\":5,\"days\":3}" }) {
      assertThatThrownBy(() -> handler.validateTransient(json(invalid))).as(invalid)
          .isInstanceOf(ResponseStatusException.class);
    }
  }

  @Test
  @DisplayName("A portfolio of another client is rejected")
  void foreignPortfolioRejected() {
    when(portfolios.findByIdTenantAndIdPortfolio(ID_TENANT, 999)).thenReturn(null);
    assertThatThrownBy(() -> handler.chart(user, json("{\"range\":\"CHART_RANGE_1Y\",\"idPortfolio\":999}")))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("The newest days are reported as pending while the marker lies on or before the newest row")
  void freshness() {
    List<HoldDailyTotal> rows = rows(5);
    LocalDate newest = rows.getLast().getHoldDate();
    PerformanceTotalChart current = PerformanceTotalChartDashboardHandler.toChart("CHF", null,
        DashboardChartRange.CHART_RANGE_1M, new HoldDailyTotalSeries(rows, newest.plusDays(1)));
    assertThat(current.newestDate()).isEqualTo(newest);
    assertThat(current.recalcPending()).isFalse();
    PerformanceTotalChart partly = PerformanceTotalChartDashboardHandler.toChart("CHF", null,
        DashboardChartRange.CHART_RANGE_1M, new HoldDailyTotalSeries(rows, newest.minusDays(2)));
    assertThat(partly.recalcPending()).isTrue();
    assertThat(partly.reasonKey()).isNull();
  }

  @Test
  @DisplayName("Total value and invested capital are taken from the stored row")
  void pointValues() {
    PerformanceTotalChart chart = PerformanceTotalChartDashboardHandler.toChart("CHF", null,
        DashboardChartRange.CHART_RANGE_1M, new HoldDailyTotalSeries(rows(1), null));
    assertThat(chart.points().getFirst().totalBalanceMC()).isEqualTo(1000.0 + 500.0 + 10.0);
    assertThat(chart.points().getFirst().investedCapitalMC()).isEqualTo(1200.0);
  }

  @Test
  @DisplayName("Long periods stay at most 500 points and end on the newest day")
  void sampling() {
    assertThat(PerformanceTotalChartDashboardHandler.sample(rows(500)).sampling()).isEqualTo(Sampling.DAY);

    List<HoldDailyTotal> fiveYears = rows(5 * 261);
    var weekly = PerformanceTotalChartDashboardHandler.sample(fiveYears);
    assertThat(weekly.sampling()).isEqualTo(Sampling.WEEK);
    assertThat(weekly.rows()).hasSizeLessThanOrEqualTo(PerformanceTotalChartDashboardHandler.MAX_POINTS);
    assertThat(weekly.rows().getLast()).isSameAs(fiveYears.getLast());

    List<HoldDailyTotal> twentyYears = rows(20 * 261);
    var monthly = PerformanceTotalChartDashboardHandler.sample(twentyYears);
    assertThat(monthly.sampling()).isEqualTo(Sampling.MONTH);
    assertThat(monthly.rows()).hasSizeLessThanOrEqualTo(PerformanceTotalChartDashboardHandler.MAX_POINTS);
    assertThat(monthly.rows().getLast()).isSameAs(twentyYears.getLast());
  }

  @Test
  @DisplayName("The shadow line is calculated on the daily rows, a weekly point carries the value of its own day")
  void shadowValuesBeforeSampling() {
    List<HoldDailyTotal> fiveYears = rows(5 * 261);
    when(holdDailyTotals.getSeries(any(), any(), any(), any()))
        .thenReturn(new HoldDailyTotalSeries(fiveYears, fiveYears.getLast().getHoldDate().plusDays(1)));
    when(benchmarks.resolve(tenant, null, null))
        .thenReturn(Optional.of(new ResolvedBenchmark(null, BenchmarkSource.BENCHMARK_SOURCE_TENANT)));
    // The benchmark gains 0.1 % per trading day; the client neither deposits nor withdraws
    when(benchmarkLoader.load(any(), eq("CHF"), any())).thenAnswer(invocation -> {
      List<LocalDate> dates = invocation.getArgument(2);
      List<Quote> quotes = new ArrayList<>();
      for (int i = 0; i < dates.size(); i++) {
        quotes.add(new Quote(dates.get(i), level(i)));
      }
      return new LoadedBenchmark(new BenchmarkDescriptor(1, "Index", "CHF", BenchmarkSource.BENCHMARK_SOURCE_TENANT,
          "CHF", null, false, BenchmarkReturnBasis.AS_SUPPLIED, List.of()),
          BenchmarkSeries.align(new BenchmarkSeries.Input(dates, quotes, null, "CHF", "CHF")));
    });

    PerformanceTotalChart chart = handler.chart(user, json("{\"range\":\"CHART_RANGE_MAX\"}"));
    assertThat(chart.sampling()).isEqualTo(Sampling.WEEK);
    assertThat(chart.benchmarkName()).isEqualTo("Index");
    assertThat(chart.benchmarkStartDate()).isEqualTo(START);
    for (var point : List.of(chart.points().get(10), chart.points().getLast())) {
      int day = fiveYears.indexOf(fiveYears.stream().filter(r -> r.getHoldDate().equals(point.date())).findFirst()
          .orElseThrow());
      assertThat(point.shadowValueMC()).isCloseTo(1510.0 * level(day) / level(0), within(0.01));
    }
  }

  private static double level(int tradingDay) {
    return 100 * Math.pow(1.001, tradingDay);
  }

  /** Consecutive weekdays from {@link #START}, each with the same figures. */
  private static List<HoldDailyTotal> rows(int count) {
    List<HoldDailyTotal> rows = new ArrayList<>();
    LocalDate day = START;
    while (rows.size() < count) {
      if (day.getDayOfWeek().getValue() <= 5) {
        rows.add(new HoldDailyTotal(ID_TENANT, null, holding(day)));
      }
      day = day.plusDays(1);
    }
    return rows;
  }

  /** Thousands of rows are built for the sampling test, too many for a mock each. */
  private static IPeriodHolding holding(LocalDate day) {
    return new IPeriodHolding() {
      @Override
      public LocalDate getDate() {
        return day;
      }

      @Override
      public double getCashBalanceMC() {
        return 1000.0;
      }

      @Override
      public double getSecuritiesMC() {
        return 500.0;
      }

      @Override
      public double getMarginCloseGainMC() {
        return 10.0;
      }

      @Override
      public double getExternalCashTransferMC() {
        return 1200.0;
      }

      @Override
      public double getDividendRealMC() {
        return 0;
      }

      @Override
      public double getFeeRealMC() {
        return 0;
      }

      @Override
      public double getInterestCashaccountRealMC() {
        return 0;
      }

      @Override
      public double getAccumulateReduceMC() {
        return 0;
      }

      @Override
      public double getSecurityRiskMC() {
        return 0;
      }

      @Override
      public double getGainMC() {
        return 0;
      }
    };
  }

  private JsonNode json(String text) {
    return mapper.readTree(text);
  }
}
