package grafioschtrader.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.entities.User;
import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.PerformanceTotalChart;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.Sampling;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepositoryCustom.HoldDailyTotalSeries;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.DashboardChartRange;
import grafioschtrader.types.TenantKindType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The total value chart only reads stored daily values, so its rules are tested without a database. */
class PerformanceTotalChartDashboardHandlerTest {

  private static final int ID_TENANT = 7;
  private static final int ID_PORTFOLIO = 11;
  private static final LocalDate START = LocalDate.of(2020, 1, 6);

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final HoldDailyTotalJpaRepository holdDailyTotals = mock(HoldDailyTotalJpaRepository.class);
  private final TenantJpaRepository tenants = mock(TenantJpaRepository.class);
  private final PortfolioJpaRepository portfolios = mock(PortfolioJpaRepository.class);
  private final PerformanceTotalChartDashboardHandler handler = new PerformanceTotalChartDashboardHandler(
      holdDailyTotals, tenants, portfolios, mapper);
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
  @DisplayName("The saved configuration holds exactly one known period")
  void validateSavedConfiguration() {
    assertThatCode(() -> handler.validate(json("{\"range\":\"CHART_RANGE_3Y\"}"))).doesNotThrowAnyException();
    for (String invalid : new String[] { "{}", "{\"range\":\"CHART_RANGE_2Y\"}", "{\"range\":3}",
        "{\"range\":\"CHART_RANGE_1Y\",\"idPortfolio\":1}" }) {
      assertThatThrownBy(() -> handler.validate(json(invalid))).as(invalid).isInstanceOf(ResponseStatusException.class);
    }
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
  @DisplayName("The portfolio scope is drawn in the currency of the portfolio")
  void portfolioCurrency() {
    Portfolio portfolio = new Portfolio();
    portfolio.setCurrency("EUR");
    when(portfolios.findByIdTenantAndIdPortfolio(ID_TENANT, ID_PORTFOLIO)).thenReturn(portfolio);
    when(holdDailyTotals.getSeries(eq(ID_TENANT), eq(ID_PORTFOLIO), any(), any()))
        .thenReturn(new HoldDailyTotalSeries(rows(3), START.plusDays(3)));
    PerformanceTotalChart chart = handler.chart(user,
        json("{\"range\":\"CHART_RANGE_1M\",\"idPortfolio\":" + ID_PORTFOLIO + "}"));
    assertThat(chart.currency()).isEqualTo("EUR");
    assertThat(chart.idPortfolio()).isEqualTo(ID_PORTFOLIO);
    assertThat(chart.points()).hasSize(3);
  }

  @Test
  @DisplayName("A simulation environment has no daily values and says so")
  void simulationEnvironment() {
    tenant.setTenantKindType(TenantKindType.SIMULATION_COPY);
    PerformanceTotalChart chart = handler.chart(user, json("{\"range\":\"CHART_RANGE_1Y\"}"));
    assertThat(chart.reasonKey()).isEqualTo(PerformanceTotalChartDashboardHandler.REASON_SIMULATION);
    assertThat(chart.points()).isEmpty();
  }

  @Test
  @DisplayName("Without rows the reason tells a pending first computation from an empty period")
  void emptyStates() {
    when(holdDailyTotals.getSeries(eq(ID_TENANT), isNull(), any(), any()))
        .thenReturn(new HoldDailyTotalSeries(List.of(), null));
    assertThat(handler.chart(user, json("{\"range\":\"CHART_RANGE_1Y\"}")).reasonKey())
        .isEqualTo(PerformanceTotalChartDashboardHandler.REASON_PENDING);
    PerformanceTotalChart empty = PerformanceTotalChartDashboardHandler.toChart("CHF", null,
        DashboardChartRange.CHART_RANGE_1M, new HoldDailyTotalSeries(List.of(), START));
    assertThat(empty.reasonKey()).isEqualTo(PerformanceTotalChartDashboardHandler.REASON_NO_DATA);
    assertThat(empty.recalcPending()).isFalse();
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
