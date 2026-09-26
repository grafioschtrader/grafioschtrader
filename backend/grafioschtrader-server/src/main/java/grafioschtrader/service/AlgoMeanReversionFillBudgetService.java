package grafioschtrader.service;

import java.util.*;

import org.springframework.stereotype.Service;

import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.AlgoMeanReversionDecisionService.*;

/** Rechecks actual fill prices against the simulation ledger while the caller holds the tenant lock. */
@Service
public class AlgoMeanReversionFillBudgetService {
  private final AlgoTopJpaRepository tops;
  private final AlgoAssetclassJpaRepository buckets;
  private final AlgoSecurityJpaRepository members;
  private final AlgoAlertScopeResolver scopes;
  private final AlgoHistoricalValuationService valuation;
  private final SimulationSourceRepository source;

  public AlgoMeanReversionFillBudgetService(AlgoTopJpaRepository tops, AlgoAssetclassJpaRepository buckets,
      AlgoSecurityJpaRepository members, AlgoAlertScopeResolver scopes, AlgoHistoricalValuationService valuation,
      SimulationSourceRepository source) {
    this.tops = tops;
    this.buckets = buckets;
    this.members = members;
    this.scopes = scopes;
    this.valuation = valuation;
    this.source = source;
  }

  public void validate(Integer owner, Context context, Decision decision, Transaction fill, Position current,
      double alreadyFilled) {
    if (context.market().tradingExcluded(fill.getSecurity()))
      throw new IllegalArgumentException("REPLAY_INSTRUMENT_EXCLUDED");
    if (source.transactions(context.tenant(), fill.getTransactionTime().toLocalDate().plusDays(1)).stream()
        .anyMatch(t -> t.getTransactionTime().isAfter(fill.getTransactionTime())))
      throw new IllegalArgumentException("Simulation fills must be chronological");
    AlgoTop top = null;
    AlgoAlertScope scope = null;
    for (var candidate : tops.findByIdTenantOrderByName(owner)) {
      var found = scopes
          .resolveForAlgoTop(candidate).stream().filter(s -> s.active()
              && s.strategy().getId().equals(context.strategy()) && s.security().getId().equals(context.security()))
          .findFirst();
      if (found.isPresent()) {
        top = candidate;
        scope = found.get();
        break;
      }
    }
    if (scope == null)
      throw new IllegalArgumentException("An active strategy scope is required for a simulation fill");
    var effective = context.market().allocation();
    if (effective != null)
      top = effective.top(top);
    var config = StrategyConfigValidator.executable(scope.strategy().getStrategyConfig());
    if (decision.action() == Action.ADD) {
      var averaging = config.downside_management.variant_B_average_down;
      if (config.downside_management.loss_action != grafioschtrader.algo.strategy.model.complex.enums.LossAction.B_average_down
          || averaging == null || !Boolean.TRUE.equals(averaging.enabled))
        throw new IllegalArgumentException("Averaging is no longer enabled");
      if (current == null || current.lifecycle() != context.position().lifecycle()
          || Math.signum(current.signedUnits()) != decision.direction()
          || current.realizedExitUnits() > context.position().realizedExitUnits() + 1e-8)
        throw new IllegalArgumentException("Addition was superseded by a reduction or closure");
      double gain = decision.direction() * (fill.getQuotation() / current.averagePrice() - 1);
      if (!Double.isFinite(gain) || gain <= -config.risk_controls.max_position_drawdown_pct + 1e-12)
        throw new IllegalArgumentException("Addition fill breaches the drawdown limit");
      var date = fill.getTransactionTime().toLocalDate();
      if (alreadyFilled == 0 && (current.executedAdds() >= averaging.max_adds
          || current.lastEntry() != null && date.isBefore(current.lastEntry().plusDays(config.cooldowns.after_buy_days))
          || current.lastExit() != null && date.isBefore(current.lastExit().plusDays(config.cooldowns.after_sell_days))
          || current.tradesIn30Days() >= config.cooldowns.max_trades_per_asset_per_30d))
        throw new IllegalArgumentException("Addition fill exceeds the count or cooldown limit");
    }
    if (!decision.increasesExposure())
      return;
    AlgoAssetclass bucket = null;
    AlgoSecurity member = null;
    List<AlgoSecurity> allocation = List.of();
    var topBuckets = buckets.findByIdTenantAndIdAlgoAssetclassParent(owner, top.getId());
    if (effective != null)
      topBuckets = effective.buckets(topBuckets);
    AlgoExposureBudget.requireWeights(topBuckets.stream().map(AlgoAssetclass::getPercentage).toList());
    for (var candidate : topBuckets) {
      var children = members.findByIdAlgoSecurityParentAndIdTenant(candidate.getId(), owner);
      if (effective != null)
        children = effective.securities(children);
      for (var child : children)
        if (child.getSecurity() != null && child.getSecurity().getId().equals(context.security())) {
          if (member != null)
            throw new IllegalArgumentException("Ambiguous strategy allocation");
          member = child;
          bucket = candidate;
          allocation = children;
        }
    }
    if (member == null)
      throw new IllegalArgumentException("An active allocation is required");
    AlgoExposureBudget.requireWeights(allocation.stream().map(AlgoSecurity::getPercentage).toList());
    // Include preceding fills in the ledger; only the proposed fill price and observations known at signal time are
    // used.
    var snapshot = valuation.value(context.tenant(), fill.getTransactionTime().toLocalDate(), (id, _) -> {
      if (id.equals(context.security()))
        return fill.getQuotation();
      var history = context.market().history(id, context.date());
      return history.isEmpty() ? null : history.getLast().getClose();
    }, Set.of(fill.getSecurity().getCurrency()));
    snapshot.requireAvailable();
    Set<Integer> bucketIds = new HashSet<>();
    allocation.forEach(m -> {
      if (m.getSecurity() != null)
        bucketIds.add(m.getSecurity().getId());
    });
    double bucketExposure = snapshot.positions().stream().filter(p -> bucketIds.contains(p.security().getId()))
        .mapToDouble(p -> p.grossExposure() * snapshot.fx().get(p.security().getCurrency())).sum();
    double exposure = snapshot.positions().stream().filter(p -> p.security().getId().equals(context.security()))
        .mapToDouble(p -> p.grossExposure() * snapshot.fx().get(p.security().getCurrency())).sum();
    double topBudget = AlgoExposureBudget.amount(snapshot.equity(), top.getPercentage());
    double bucketBudget = AlgoExposureBudget.amount(topBudget, bucket.getPercentage());
    double capacity = Math.min(Math.min(topBudget - snapshot.grossExposure(), bucketBudget - bucketExposure),
        Math.min(AlgoExposureBudget.amount(bucketBudget, member.getPercentage()) - exposure,
            snapshot.equity() * config.risk_controls.max_position_exposure_pct - exposure));
    double amount = Math.abs(fill.getUnits()) * fill.getQuotation() * fill.getValuePerPoint()
        * snapshot.fx().get(fill.getSecurity().getCurrency());
    if (!Double.isFinite(snapshot.equity()) || snapshot.equity() <= 0 || !Double.isFinite(amount) || amount <= 0
        || !Double.isFinite(capacity) || amount > capacity + 1e-8)
      throw new IllegalArgumentException("Actual fill exceeds the remaining exposure budget");
  }
}
