package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.BenchmarkSeriesLoader;
import grafioschtrader.reports.BenchmarkSeriesLoader.LoadedBenchmark;
import grafioschtrader.reportviews.performance.BenchmarkResultFactory;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.PerformanceBenchmarkService.ResolvedBenchmark;

/**
 * Compares the equity curve of a completed historical replay with a benchmark, on request and without storing anything.
 * The comparison is built by the same {@link BenchmarkResultFactory} as the period performance report, with the flow
 * timing and the annualization rule of the replay metrics.
 */
@Service
public class ReplayBenchmarkService {

  private final AlgoHistoricalReplayService replayService;
  private final TenantJpaRepository tenantJpaRepository;
  private final PerformanceBenchmarkService performanceBenchmarkService;
  private final BenchmarkSeriesLoader benchmarkSeriesLoader;
  private final RiskFreeRateService riskFreeRateService;
  private final TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  public ReplayBenchmarkService(AlgoHistoricalReplayService replayService, TenantJpaRepository tenantJpaRepository,
      PerformanceBenchmarkService performanceBenchmarkService, BenchmarkSeriesLoader benchmarkSeriesLoader,
      RiskFreeRateService riskFreeRateService, TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository) {
    this.replayService = replayService;
    this.tenantJpaRepository = tenantJpaRepository;
    this.performanceBenchmarkService = performanceBenchmarkService;
    this.benchmarkSeriesLoader = benchmarkSeriesLoader;
    this.riskFreeRateService = riskFreeRateService;
    this.tradingDaysPlusJpaRepository = tradingDaysPlusJpaRepository;
  }

  /**
   * Compares the latest completed replay of a simulation environment with a benchmark.
   *
   * @param idSimTenant the simulation environment, which must belong to the caller
   * @param idBenchmark null for the stored benchmark of the environment or, without one, of its main client; 0 for
   *                    none; otherwise the instrument, which must be readable for the main client
   * @return the comparison, empty when the replay has no completed curve or no benchmark resolves
   */
  @Transactional(readOnly = true)
  public Optional<ReplayBenchmarkComparison> compare(Integer idSimTenant, Integer idBenchmark) {
    // Checks the ownership of the environment before anything else is read
    List<SimulationRunEquityPoint> curve = replayService.equitySeries(idSimTenant);
    Tenant simulation = tenantJpaRepository.getReferenceById(idSimTenant);
    Optional<ResolvedBenchmark> resolved = performanceBenchmarkService.resolve(simulation, null, idBenchmark);
    if (curve.size() < 2 || resolved.isEmpty()) {
      return Optional.empty();
    }
    String currency = simulation.getCurrency();
    List<ValuationPoint> points = valuationPoints(curve, regularSessions(curve));
    List<LocalDate> dates = points.stream().map(ValuationPoint::date).toList();
    LoadedBenchmark loaded = benchmarkSeriesLoader.load(resolved.get(), currency, dates);
    BenchmarkResultFactory.Built built = BenchmarkResultFactory.build(loaded.descriptor(), loaded.aligned(),
        ReturnSeries.of(points, Convention.REPLAY), points, datedFlows(curve), dates.getFirst(), dates.getLast(),
        new BenchmarkResultFactory.Settings(Convention.REPLAY, 0,
            loaded.hasSpan() ? riskFreeRateService.rateLookup(currency, dates.getFirst(), dates.getLast()) : null));
    return Optional.of(new ReplayBenchmarkComparison(built.result(), LocalDateTime.now()));
  }

  /**
   * The valuation points of the curve: the change of the invested capital is the external flow of the day, split into
   * an inflow and an outflow; under {@link Convention#REPLAY} both count at the start of the day.
   *
   * @param curve   the equity curve, the first point is the opening
   * @param regular whether the interval between two dates spans exactly one expected session
   * @return one point per point of the curve
   */
  static List<ValuationPoint> valuationPoints(List<SimulationRunEquityPoint> curve,
      BiPredicate<LocalDate, LocalDate> regular) {
    List<ValuationPoint> points = new ArrayList<>(curve.size());
    points.add(new ValuationPoint(curve.getFirst().date(), curve.getFirst().equity(), 0, 0, true));
    for (int i = 1; i < curve.size(); i++) {
      SimulationRunEquityPoint before = curve.get(i - 1);
      SimulationRunEquityPoint point = curve.get(i);
      double flow = point.investedCapital() - before.investedCapital();
      points.add(new ValuationPoint(point.date(), point.equity(), Math.max(0, flow), Math.max(0, -flow),
          regular.test(before.date(), point.date())));
    }
    return points;
  }

  /** The net external flow of every day after the opening that has one, for the money-weighted return. */
  private static List<DatedFlow> datedFlows(List<SimulationRunEquityPoint> curve) {
    List<DatedFlow> flows = new ArrayList<>();
    for (int i = 1; i < curve.size(); i++) {
      double flow = curve.get(i).investedCapital() - curve.get(i - 1).investedCapital();
      if (flow != 0) {
        flows.add(new DatedFlow(curve.get(i).date(), flow));
      }
    }
    return flows;
  }

  /**
   * An interval of the curve is a daily risk interval only when both ends are expected sessions and no expected
   * session lies between them. The curve has no regular flag of its own and can end on a weekend terminal point, so the
   * schedule is taken from the trading days of GT; without any trading day in the range no interval is regular, and the
   * daily risk figures stay empty while the linked returns are still formed.
   */
  private BiPredicate<LocalDate, LocalDate> regularSessions(List<SimulationRunEquityPoint> curve) {
    Map<LocalDate, Integer> sessionIndex = new HashMap<>();
    var tradingDays = tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(curve.getFirst().date(), curve.getLast().date());
    for (int i = 0; i < tradingDays.size(); i++) {
      sessionIndex.put(tradingDays.get(i).getTradingDate(), i);
    }
    return (before, current) -> {
      Integer from = sessionIndex.get(before);
      Integer to = sessionIndex.get(current);
      return from != null && to != null && to == from + 1;
    };
  }
}
