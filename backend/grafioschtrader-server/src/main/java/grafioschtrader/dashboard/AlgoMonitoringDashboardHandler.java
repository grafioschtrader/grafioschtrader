package grafioschtrader.dashboard;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import grafiosch.dashboard.DashboardLayoutRepository;
import grafiosch.dashboard.DashboardWidgetHandler;
import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.entities.User;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.dto.AlgoMonitoringSummary;
import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.AlgoRebalancingService;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The card telling whether the hierarchy assigned to monitoring needs attention and when its next checkpoint falls due.
 *
 * <p>
 * It summarizes the plan the daily evaluation stored rather than calculating one: a live plan values every position,
 * which a start page should not do on every load, and the stored plan is exactly what the last notification was based
 * on. The full plan has a line per bucket and instrument and nearly always proposes nothing, so the card condenses it to
 * a state, the largest drift and at most three trades, and leaves everything else to the rebalancing report.
 * </p>
 *
 * <p>
 * Monitoring exists only in the main tenant, so the card reads it even while a simulation environment is selected.
 * </p>
 */
@Component
@Order(130)
public class AlgoMonitoringDashboardHandler implements DashboardWidgetHandler {

  private static final String TYPE = "ALGO_MONITORING";
  private static final int MAX_TRADES = 3;

  private final TenantJpaRepository tenantJpaRepository;
  private final AlgoTopJpaRepository algoTopJpaRepository;
  private final AlgoRecommendationJpaRepository algoRecommendationJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final AlgoRebalancingService algoRebalancingService;
  private final FeatureConfig features;
  private final ObjectMapper mapper;

  public AlgoMonitoringDashboardHandler(TenantJpaRepository tenantJpaRepository,
      AlgoTopJpaRepository algoTopJpaRepository, AlgoRecommendationJpaRepository algoRecommendationJpaRepository,
      SecurityJpaRepository securityJpaRepository, AlgoRebalancingService algoRebalancingService,
      FeatureConfig features, ObjectMapper mapper) {
    this.tenantJpaRepository = tenantJpaRepository;
    this.algoTopJpaRepository = algoTopJpaRepository;
    this.algoRecommendationJpaRepository = algoRecommendationJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.algoRebalancingService = algoRebalancingService;
    this.features = features;
    this.mapper = mapper;
  }

  @Override
  public Descriptor descriptor() {
    return new Descriptor(TYPE, "DASHBOARD_" + TYPE, "DASHBOARD_" + TYPE + "_HELP", "THIRD", Map.of(), null);
  }

  @Override
  public boolean available(User user) {
    return features.isAlgo() && user.getActualIdTenant() != null;
  }

  /** The card has no settings. */
  @Override
  public void validate(JsonNode config) {
    if (!config.isObject() || !config.isEmpty()) {
      DashboardLayoutRepository.invalid("DASHBOARD_INVALID");
    }
  }

  @Override
  public Payload load(User user, JsonNode config) {
    return new Payload(List.of(), mapper.valueToTree(summarize(user)));
  }

  AlgoMonitoringSummary summarize(User user) {
    Integer idTenant = user.getActualIdTenant();
    Integer idAlgoTop = tenantJpaRepository.findById(idTenant).map(Tenant::getIdAlgoTop).orElse(null);
    AlgoTop algoTop = idAlgoTop == null ? null
        : algoTopJpaRepository.findByIdTenantAndIdAlgoAssetclassSecurity(idTenant, idAlgoTop);
    if (algoTop == null) {
      return AlgoMonitoringSummary.reason("DASHBOARD_ALGO_NO_MONITORING");
    }
    List<AlgoRecommendation> plan = algoRecommendationJpaRepository
        .findByIdTenantAndIdAlgoTopOrderByLevelTypeAscIdNodeAsc(idTenant, idAlgoTop);
    if (plan.isEmpty()) {
      return AlgoMonitoringSummary.reason("DASHBOARD_ALGO_NOT_EVALUATED");
    }
    return summarize(user, algoTop, plan, meanReversionSignals(idTenant, idAlgoTop));
  }

  private AlgoMonitoringSummary summarize(User user, AlgoTop algoTop, List<AlgoRecommendation> plan,
      int meanReversionSignals) {
    List<AlgoRecommendation> securityLines = plan.stream()
        .filter(r -> StrategyHelper.SECURITY_LEVEL_LETTER.equals(r.getLevelType())).toList();
    List<AlgoRecommendation> buys = withAction(securityLines, AlgoRecommendationAction.REBALANCE_BUY);
    List<AlgoRecommendation> sells = withAction(securityLines, AlgoRecommendationAction.REBALANCE_SELL);
    int blocked = withAction(securityLines, AlgoRecommendationAction.REBALANCE_BLOCKED).size();
    boolean periodicDue = plan.stream().anyMatch(r -> r.getTriggerKind() == AlgoRebalancingTrigger.PERIODIC);
    String statusKey = periodicDue ? "DASHBOARD_ALGO_CHECKPOINT_DUE"
        : buys.isEmpty() && sells.isEmpty() && blocked == 0 ? "DASHBOARD_ALGO_NOTHING_TO_DO"
            : "DASHBOARD_ALGO_DRIFT_REPORTED";
    AlgoRecommendation first = plan.getFirst();
    LocalDate lastCheckpoint = plan.stream().map(AlgoRecommendation::getCheckpointDate).filter(Objects::nonNull)
        .max(Comparator.naturalOrder()).orElse(null);
    AlgoRecommendation largest = plan.stream()
        .filter(r -> StrategyHelper.ASSET_CLASS_LEVEL_LETTER.equals(r.getLevelType())
            && r.getDeviationPercentage() != null)
        .max(Comparator.comparingDouble(r -> Math.abs(r.getDeviationPercentage()))).orElse(null);
    return new AlgoMonitoringSummary(null, statusKey, algoTop.getIdAlgoAssetclassSecurity(), algoTop.getName(),
        first.getValuationDate(), first.getCurrency(), periodicDue, lastCheckpoint,
        algoRebalancingService.nextCheckpointDate(algoTop, lastCheckpoint), buys.size(), sum(buys), sells.size(),
        sum(sells), blocked,
        largest == null ? null : algoRebalancingService.bucketLabel(largest.getIdNode(), user.createAndGetJavaLocale()),
        largest == null ? null : largest.getDeviationPercentage(), meanReversionSignals, topTrades(buys, sells));
  }

  /** Mean reversion proposals of the monitored hierarchy that ask for a trade. */
  private int meanReversionSignals(Integer idTenant, Integer idAlgoTop) {
    return (int) algoRecommendationJpaRepository
        .findByIdTenantAndTriggerKind(idTenant, AlgoRebalancingTrigger.MEAN_REVERSION).stream()
        .filter(r -> idAlgoTop.equals(r.getIdAlgoTop()) && isTrade(r.getRecommendedAction())).count();
  }

  /** Sales first, because executing a purchase before its funding sale can breach the ceiling on the way. */
  private List<AlgoMonitoringSummary.Trade> topTrades(List<AlgoRecommendation> buys, List<AlgoRecommendation> sells) {
    Comparator<AlgoRecommendation> bySize = Comparator
        .comparingDouble((AlgoRecommendation r) -> r.getRecommendedAmount() == null ? 0 : r.getRecommendedAmount())
        .reversed();
    List<AlgoRecommendation> trades = Stream
        .concat(sells.stream().sorted(bySize), buys.stream().sorted(bySize)).limit(MAX_TRADES).toList();
    Map<Integer, String> names = securityJpaRepository
        .findAllById(trades.stream().map(AlgoRecommendation::getIdSecuritycurrency).toList()).stream()
        .collect(Collectors.toMap(Security::getIdSecuritycurrency, Security::getName, (a, _) -> a));
    return trades.stream().map(r -> new AlgoMonitoringSummary.Trade(names.get(r.getIdSecuritycurrency()),
        r.getRecommendedAction(), r.getRecommendedAmount(), r.getRecommendedUnits())).toList();
  }

  private static List<AlgoRecommendation> withAction(List<AlgoRecommendation> lines, AlgoRecommendationAction action) {
    return lines.stream().filter(r -> r.getRecommendedAction() == action).toList();
  }

  private static double sum(List<AlgoRecommendation> lines) {
    return lines.stream().map(AlgoRecommendation::getRecommendedAmount).filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue).sum();
  }

  private static boolean isTrade(AlgoRecommendationAction action) {
    return action == AlgoRecommendationAction.REBALANCE_BUY || action == AlgoRecommendationAction.REBALANCE_SELL;
  }
}
