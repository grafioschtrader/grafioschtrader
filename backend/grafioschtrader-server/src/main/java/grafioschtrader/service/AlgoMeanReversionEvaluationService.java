package grafioschtrader.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Decision;
import grafioschtrader.service.AlgoMeanReversionDecisionService.MarketData;
import grafioschtrader.service.AlgoMeanReversionScopeEvaluator.Proposal;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import grafioschtrader.types.AlgoSignalKind;

/** Daily-close live adapter. Serializes a tenant's proposals and never books a transaction. */
@Service
public class AlgoMeanReversionEvaluationService {
  private final AlgoTopJpaRepository tops;
  private final AlgoAlertScopeResolver scopes;
  private final AlgoTradingRepository data;
  private final AlgoRecommendationJpaRepository recommendations;
  private final AlgoAlarmRecorder recorder;
  private final FeatureConfig features;
  private final AlgoMeanReversionScopeEvaluator evaluator;
  private final AlgoMonitoringService monitoring;
  private Clock clock = Clock.systemUTC();

  public AlgoMeanReversionEvaluationService(AlgoTopJpaRepository tops, AlgoAlertScopeResolver scopes,
      AlgoTradingRepository data, AlgoRecommendationJpaRepository recommendations, AlgoAlarmRecorder recorder,
      FeatureConfig features, AlgoMeanReversionScopeEvaluator evaluator, AlgoMonitoringService monitoring) {
    this.tops = tops;
    this.scopes = scopes;
    this.data = data;
    this.recommendations = recommendations;
    this.recorder = recorder;
    this.features = features;
    this.evaluator = evaluator;
    this.monitoring = monitoring;
  }

  public static boolean isMeanReversion(AlgoStrategy s) {
    return s.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP;
  }

  public List<Integer> tenantIds() {
    return tops.findAll().stream().map(AlgoTop::getIdTenant).distinct().sorted().toList();
  }

  /** Cheap daily due check; history is read only by the queued worker. */
  public boolean hasDue() {
    if (!features.isAlgo() || !features.isAlert())
      return false;
    LocalDate today = LocalDate.now(clock);
    // Only the assigned monitoring hierarchy keeps live proposals; the notification preference does not matter here.
    return tops.findAll().stream().filter(top -> monitoring.isAssigned(top.getIdTenant(), top.getId())).flatMap(top -> scopes.resolveForAlgoTop(top).stream())
        .filter(s -> s.active() && isMeanReversion(s.strategy()))
        .anyMatch(s -> recommendations
            .findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndTriggerKind(s.idTenant(), s.strategy().getId(),
                s.security().getId(), AlgoRebalancingTrigger.MEAN_REVERSION)
            .map(r -> r.getRunDate().isBefore(today)).orElse(true));
  }

  /**
   * One transaction serializes all parent budgets and atomically records recommendations and notifications. Only the
   * hierarchy assigned to monitoring is evaluated: another hierarchy, typically kept for simulation, would reserve budget
   * against the real holdings and propose trades nobody follows. Proposals of any other hierarchy, including those of a
   * previous assignment, are removed with the stale rows at the end; a tenant without assignment keeps none.
   */
  @Transactional
  public void evaluate(Integer tenantId, boolean manual) {
    if (!features.isAlgo() || !features.isAlert())
      return;
    Tenant tenant = data.lockTenant(tenantId);
    if (tenant.getIdParentTenant() != null)
      throw new IllegalArgumentException("Live evaluation requires the main tenant");
    LocalDate today = LocalDate.now(clock);
    Integer assigned = tenant.getIdAlgoTop();
    if (!manual && tops.findByIdTenantOrderByName(tenantId).stream()
        .filter(top -> top.getId().equals(assigned)).flatMap(top -> scopes.resolveForAlgoTop(top).stream()).filter(s -> s.active() && isMeanReversion(s.strategy()))
        .noneMatch(s -> recommendations
            .findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndTriggerKind(tenantId, s.strategy().getId(),
                s.security().getId(), AlgoRebalancingTrigger.MEAN_REVERSION)
            .map(r -> r.getRunDate().isBefore(today)).orElse(true)))
      return;
    LocalDate through = today.minusDays(1);
    MarketData market = (id, date) -> {
      List<Historyquote> result = new ArrayList<>(data.history(id, date, Limit.of(1200)));
      Collections.reverse(result);
      return result;
    };
    Set<Integer> retained = new HashSet<>();
    List<Proposal> proposals = assigned == null ? List.of()
        : evaluator.evaluate(tenantId, tenantId, assigned, through, market);
    for (Proposal proposal : proposals) {
      AlgoAlertScope scope = proposal.scope();
      Decision decision = proposal.decision();
      AlgoRecommendation row = recommendations
          .findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndTriggerKind(tenantId, scope.strategy().getId(),
              scope.security().getId(), AlgoRebalancingTrigger.MEAN_REVERSION)
          .orElseGet(AlgoRecommendation::new);
      row.setIdTenant(tenantId);
      row.setIdAlgoTop(proposal.idAlgoTop());
      row.setIdAlgoStrategy(scope.strategy().getId());
      row.setIdSecuritycurrency(scope.security().getId());
      row.setLevelType("S");
      row.setIdNode(proposal.member() == null ? scope.security().getId() : proposal.member().getId());
      row.setRunDate(today);
      row.setValuationDate(proposal.valuationDate());
      row.setCurrency(tenant.getCurrency());
      row.setTriggerKind(AlgoRebalancingTrigger.MEAN_REVERSION);
      row.setRationale(decision.rationale().substring(0, Math.min(1000, decision.rationale().length())));
      row.setRecommendedAmount(proposal.amount());
      row.setRecommendedUnits(decision.quantity());
      row.setRecommendedAction(recommendedAction(decision));
      recommendations.save(row);
      retained.add(row.getId());
      if (decision.actionable()) {
        recorder.record(
            scope, signalKind(decision), (byte) decision.direction(), decision.tranche(), decision.identity() + " | "
                + decision.rationale() + " | units=" + decision.quantity() + " | price=" + decision.price(),
            row.getValuationDate());
      }
    }
    recommendations
        .deleteAll(recommendations.findByIdTenantAndTriggerKind(tenantId, AlgoRebalancingTrigger.MEAN_REVERSION)
            .stream().filter(r -> !retained.contains(r.getId())).toList());
  }

  /** The direction of the proposed trade, not of the exposure: closing a short position is a purchase. */
  static AlgoRecommendationAction recommendedAction(Decision decision) {
    boolean buy = decision.increasesExposure() ? decision.direction() > 0 : decision.direction() < 0;
    return decision.actionable()
        ? buy ? AlgoRecommendationAction.REBALANCE_BUY : AlgoRecommendationAction.REBALANCE_SELL
        : decision.action() == AlgoMeanReversionDecisionService.Action.HOLD ? AlgoRecommendationAction.REBALANCE_HOLD
            : AlgoRecommendationAction.REBALANCE_BLOCKED;
  }

  static AlgoSignalKind signalKind(Decision decision) {
    return switch (decision.action()) {
    case ENTRY, ADD -> AlgoSignalKind.ENTRY_SIGNAL;
    // A tranche and a full take-profit are the same kind of event; the tranche key tells them apart.
    case SCALE_OUT, TAKE_PROFIT_EXIT -> AlgoSignalKind.PROFIT_TAKE;
    case RISK_EXIT -> AlgoSignalKind.RISK_BREACH;
    default -> AlgoSignalKind.STOP_LOSS;
    };
  }
}
