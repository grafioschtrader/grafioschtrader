package grafioschtrader.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.common.ClientClock;
import grafiosch.dashboard.DashboardLayoutRepository;
import grafiosch.dashboard.DashboardWidgetHandler;
import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.entities.User;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.PerformanceTotalChartConfig;
import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.PerformanceTotalChart;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.Point;
import grafioschtrader.reportviews.dashboard.PerformanceTotalChartPayload.Sampling;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepositoryCustom.HoldDailyTotalSeries;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.DashboardChartRange;
import grafioschtrader.types.TenantKindType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The chart card showing how the total value of the client, or of one of its portfolios, developed, next to the capital
 * paid into it.
 *
 * <p>
 * It reads the daily values the task {@code HOLD_DAILY_TOTAL_UPDATE} keeps in {@code hold_daily_total} and runs no
 * performance query of its own. Those rows are copies of what the period performance report computes, so the chart and
 * the report agree on every day both show. A long period is reduced to weekly or monthly values, because a chart of a
 * few hundred pixels cannot show more points than that anyway and the payload would only grow.
 * </p>
 */
@Component
@Order(125)
public class PerformanceTotalChartDashboardHandler implements DashboardWidgetHandler {

  private static final String TYPE = "PERFORMANCE_TOTAL_CHART";

  /** A setting a card may change per read: a portfolio id, or null for the whole client. */
  private static final String ID_PORTFOLIO = "idPortfolio";
  /** The saved setting, which a card may also change per read. */
  private static final String RANGE = "range";
  private static final Set<String> TRANSIENT_SETTINGS = Set.of(ID_PORTFOLIO, RANGE);

  /** The most points one chart carries; beyond that the days are reduced to weeks and then to months. */
  static final int MAX_POINTS = 500;

  static final String REASON_PENDING = "DASHBOARD_TOTAL_CHART_PENDING";
  static final String REASON_NO_DATA = "DASHBOARD_TOTAL_CHART_NO_DATA";
  static final String REASON_SIMULATION = "DASHBOARD_TOTAL_CHART_SIMULATION";

  private static final List<String> RANGES = Arrays.stream(DashboardChartRange.values()).map(Enum::name).toList();

  private final HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final PortfolioJpaRepository portfolioJpaRepository;
  private final ObjectMapper mapper;

  public PerformanceTotalChartDashboardHandler(HoldDailyTotalJpaRepository holdDailyTotalJpaRepository,
      TenantJpaRepository tenantJpaRepository, PortfolioJpaRepository portfolioJpaRepository, ObjectMapper mapper) {
    this.holdDailyTotalJpaRepository = holdDailyTotalJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.portfolioJpaRepository = portfolioJpaRepository;
    this.mapper = mapper;
  }

  @Override
  public Descriptor descriptor() {
    return new Descriptor(TYPE, "DASHBOARD_" + TYPE, "DASHBOARD_" + TYPE + "_HELP", "HALF",
        Map.of(RANGE, DashboardChartRange.CHART_RANGE_1Y.name()),
        DynamicModelHelper.getFormDefinitionOfEntityClass(PerformanceTotalChartConfig.class, 1));
  }

  /** Investment data needs a client; a user who has not set one up yet has nothing to draw. */
  @Override
  public boolean available(User user) {
    return user.getIdTenant() != null;
  }

  /**
   * Accepts only the period. The options repeat the constants of {@link DashboardChartRange} because the annotations of
   * {@link PerformanceTotalChartConfig} describe the form for the client and are never executed against the stored
   * settings.
   */
  @Override
  public void validate(JsonNode config) {
    if (!config.isObject() || config.size() != 1 || !isRange(config.path(RANGE))) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  /**
   * Accepts the portfolio and the period of a single read, and nothing else. A portfolio of another client is not
   * rejected here, where the caller is unknown, but by {@link #load}.
   */
  @Override
  public void validateTransient(JsonNode override) {
    if (override.isEmpty() || !TRANSIENT_SETTINGS.containsAll(override.propertyNames())) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
    JsonNode idPortfolio = override.path(ID_PORTFOLIO);
    if (override.has(ID_PORTFOLIO) && !(idPortfolio.isNull() || idPortfolio.isIntegralNumber())
        || override.has(RANGE) && !isRange(override.path(RANGE))) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  @Override
  public Payload load(User user, JsonNode config) {
    return new Payload(List.of(), mapper.valueToTree(chart(user, config)));
  }

  /**
   * Builds the chart of the client or of the chosen portfolio.
   *
   * @param user   the caller, whose client is shown
   * @param config the saved settings merged with those of this read
   * @return the chart, with a reason key and no points when there is nothing to draw
   */
  PerformanceTotalChart chart(User user, JsonNode config) {
    Integer idTenant = user.getIdTenant();
    JsonNode idPortfolioNode = config.path(ID_PORTFOLIO);
    Integer idPortfolio = idPortfolioNode.isIntegralNumber() ? idPortfolioNode.asInt() : null;
    DashboardChartRange range = DashboardChartRange.fromName(config.path(RANGE).asString());
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    String currency = tenant.getCurrency();
    if (idPortfolio != null) {
      Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(idTenant, idPortfolio);
      if (portfolio == null) {
        DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
      }
      currency = portfolio.getCurrency();
    }
    if (tenant.getTenantKindType() == TenantKindType.SIMULATION_COPY) {
      // The update task covers main clients only, so a simulation environment never gets any daily values.
      return new PerformanceTotalChart(currency, idPortfolio, range.name(), RANGES, Sampling.DAY, null, false,
          REASON_SIMULATION, List.of());
    }
    LocalDate today = ClientClock.today();
    HoldDailyTotalSeries series = holdDailyTotalJpaRepository.getSeries(idTenant, idPortfolio, range.fromDate(today),
        today);
    return toChart(currency, idPortfolio, range, series);
  }

  /**
   * Turns the rows of a period into the chart. A missing marker means the client has never been computed, which is
   * reported differently from a client that simply held nothing in the period.
   */
  static PerformanceTotalChart toChart(String currency, Integer idPortfolio, DashboardChartRange range,
      HoldDailyTotalSeries series) {
    List<HoldDailyTotal> rows = series.rows();
    if (rows.isEmpty()) {
      return new PerformanceTotalChart(currency, idPortfolio, range.name(), RANGES, Sampling.DAY, null,
          series.recalcFromDate() == null, series.recalcFromDate() == null ? REASON_PENDING : REASON_NO_DATA,
          List.of());
    }
    LocalDate newestDate = rows.getLast().getHoldDate();
    boolean recalcPending = series.recalcFromDate() == null || !series.recalcFromDate().isAfter(newestDate);
    Sampled sampled = sample(rows);
    List<Point> points = sampled.rows().stream().map(r -> new Point(r.getHoldDate(), r.getTotalBalanceMC(),
        DataBusinessHelper.roundStandard(r.getExternalCashTransferMC()))).toList();
    return new PerformanceTotalChart(currency, idPortfolio, range.name(), RANGES, sampled.sampling(), newestDate,
        recalcPending, null, points);
  }

  /**
   * Reduces the rows to at most {@link #MAX_POINTS}: first to the last trading day of each week, and if that is still
   * too many, to the last trading day of each month. The newest row is always kept, so the chart ends on the value the
   * card reports as the newest one.
   *
   * @param rows the rows in ascending order of the day
   * @return the kept rows and how densely they are spaced
   */
  static Sampled sample(List<HoldDailyTotal> rows) {
    if (rows.size() <= MAX_POINTS) {
      return new Sampled(rows, Sampling.DAY);
    }
    List<HoldDailyTotal> weekly = lastPerPeriod(rows, d -> d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    if (weekly.size() <= MAX_POINTS) {
      return new Sampled(weekly, Sampling.WEEK);
    }
    return new Sampled(lastPerPeriod(rows, d -> d.withDayOfMonth(1)), Sampling.MONTH);
  }

  /** Keeps a row when the next row lies in another period, or when it is the last row. */
  private static List<HoldDailyTotal> lastPerPeriod(List<HoldDailyTotal> rows, UnaryOperator<LocalDate> periodOf) {
    List<HoldDailyTotal> kept = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      if (i == rows.size() - 1
          || !periodOf.apply(rows.get(i).getHoldDate()).equals(periodOf.apply(rows.get(i + 1).getHoldDate()))) {
        kept.add(rows.get(i));
      }
    }
    return kept;
  }

  private static boolean isRange(JsonNode node) {
    return node.isString() && DashboardChartRange.fromName(node.asString()) != null;
  }

  /** The rows a chart keeps, with their spacing. */
  record Sampled(List<HoldDailyTotal> rows, Sampling sampling) {
  }
}
