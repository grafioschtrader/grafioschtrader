package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafiosch.service.EntityLimitService;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.TransactionType;

/** Reconstructs strategy ownership from actual transactions, including corrections and split-adjusted quantities. */
@Service
public class AlgoMeanReversionPositionService {
  private final SimulationSourceRepository source;
  private final AlgoExecutionStateJpaRepository states;
  private final SecuritysplitJpaRepository splits;
  private final EntityLimitService limits;
  private final TenantJpaRepository tenants;
  private final AlgoAlertScopeResolver scopes;

  public AlgoMeanReversionPositionService(SimulationSourceRepository source, AlgoExecutionStateJpaRepository states,
      SecuritysplitJpaRepository splits, EntityLimitService limits, TenantJpaRepository tenants,
      AlgoAlertScopeResolver scopes) {
    this.source = source;
    this.states = states;
    this.splits = splits;
    this.limits = limits;
    this.tenants = tenants;
    this.scopes = scopes;
  }

  /** Assignment is checked on the normal transaction write path, including imports. */
  public void validateAssignment(Transaction transaction) {
    var all = new ArrayList<>(source.transactions(transaction.getIdTenant(), LocalDate.of(9999, 12, 31)));
    if (transaction.getSecurity() != null && transaction.getSecurity().isMarginInstrument()
        && transaction.getConnectedIdTransaction() != null && transaction.getIdAlgoStrategy() == null)
      all.stream().filter(t -> Objects.equals(t.getId(), transaction.getConnectedIdTransaction())).findFirst()
          .ifPresent(t -> transaction.setIdAlgoStrategy(t.getIdAlgoStrategy()));
    if (transaction.getIdAlgoStrategy() != null) {
      if (transaction.getSecurity() == null || transaction.getUnits() == null || !isTrade(transaction))
        throw invalid("Only security fills can be assigned");
      Tenant tenant = tenants.findById(transaction.getIdTenant()).orElseThrow();
      int owner = tenant.getIdParentTenant() == null ? tenant.getId() : tenant.getIdParentTenant();
      boolean allowed = scopes.resolveForTenant(owner).stream()
          .anyMatch(s -> s.strategy().getIdAlgoRuleStrategy().equals(transaction.getIdAlgoStrategy())
              && s.security().getId().equals(transaction.getSecurity().getId())
              && AlgoMeanReversionEvaluationService.isMeanReversion(s.strategy()));
      if (!allowed)
        throw invalid("Strategy does not own this instrument scope");
    }

    Set<Integer> affected = new HashSet<>();
    if (transaction.getSecurity() != null)
      affected.add(transaction.getSecurity().getId());
    all.stream().filter(t -> Objects.equals(t.getId(), transaction.getId()) && t.getSecurity() != null)
        .forEach(t -> affected.add(t.getSecurity().getId()));
    all.removeIf(t -> Objects.equals(t.getId(), transaction.getId()));
    all.add(transaction);
    // Validate both the old and new assignment. Removing an opening must not orphan its later exits.
    Map<String, Double> quantities = new HashMap<>();
    var assigned = all.stream()
        .filter(t -> t.getSecurity() != null && affected.contains(t.getSecurity().getId())
            && !t.getSecurity().isMarginInstrument() && t.getIdAlgoStrategy() != null && isTrade(t))
        .sorted(Comparator.comparing(Transaction::getTransactionTime)
            .thenComparing(t -> t.getId() == null ? Integer.MAX_VALUE : t.getId()))
        .toList();
    for (Transaction t : assigned) {
      String key = t.getSecurity().getId() + ":" + t.getIdSecurityaccount() + ":" + t.getIdAlgoStrategy();
      var splitMap = splits.getSecuritysplitMapByIdSecuritycurrency(t.getSecurity().getId());
      double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(t.getSecurity().getId(),
          t.getTransactionTime().toLocalDate(), LocalDate.of(9999, 12, 31), splitMap).fromToDateFactor;
      double delta = Math.abs(t.getUnits()) * factor * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1);
      double units = quantities.merge(key, delta, Double::sum);
      if (units < -1e-8)
        throw invalid("Assigned exit has no sufficient opening in this security account");
    }
    // Check both opening and closing assignments, including later closes when an opening is reassigned.
    Map<Integer, Transaction> byId = new HashMap<>();
    all.forEach(t -> {
      if (t.getId() != null)
        byId.put(t.getId(), t);
    });
    for (Transaction t : all) {
      if (t.getSecurity() != null && t.getSecurity().isMarginInstrument() && isTrade(t)
          && t.getConnectedIdTransaction() != null) {
        Transaction opening = byId.get(t.getConnectedIdTransaction());
        if (opening != null && !Objects.equals(opening.getIdAlgoStrategy(), t.getIdAlgoStrategy())
            && !Objects.equals(opening.getId(), transaction.getId()))
          throw invalid("Margin opening and closing fills must have the same strategy assignment");
      }
    }
  }

  /** Only unassigned open quantities block evaluation; closed legacy trades do not claim a position. */
  public boolean assignmentResolved(Integer tenant, Security security, LocalDate date) {
    var trades = source.transactions(tenant, date.plusDays(1)).stream()
        .filter(t -> t.getSecurity() != null && security.getId().equals(t.getSecurity().getId()) && isTrade(t))
        .toList();
    Map<String, Double> open = new HashMap<>();
    var splitMap = splits.getSecuritysplitMapByIdSecuritycurrency(security.getId());
    Set<Integer> unassignedOpenings = new HashSet<>();
    if (security.isMarginInstrument())
      trades.stream().filter(t -> t.getIdAlgoStrategy() == null && t.getConnectedIdTransaction() == null)
          .forEach(t -> unassignedOpenings.add(t.getId()));
    for (var t : trades) {
      Integer opening = t.getConnectedIdTransaction() == null ? t.getId() : t.getConnectedIdTransaction();
      if (security.isMarginInstrument() ? !unassignedOpenings.contains(opening) : t.getIdAlgoStrategy() != null)
        continue;
      double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(security.getId(),
          t.getTransactionTime().toLocalDate(), date.plusDays(1), splitMap).fromToDateFactor;
      String key = security.isMarginInstrument() ? opening.toString() : String.valueOf(t.getIdSecurityaccount());
      open.merge(key, Math.abs(t.getUnits()) * factor * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1),
          Double::sum);
    }
    return open.values().stream().allMatch(units -> Math.abs(units) < 1e-8);
  }

  /** Reconciliation replaces stale projections; neither recommendations nor delivery are read. */
  public AlgoMeanReversionDecisionService.Position reconcile(Integer tenant, Integer strategy, Security security,
      LocalDate date) {
    List<Transaction> trades = source.transactions(tenant, date.plusDays(1)).stream()
        .filter(t -> Objects.equals(strategy, t.getIdAlgoStrategy()) && t.getSecurity() != null
            && security.getId().equals(t.getSecurity().getId()) && isTrade(t))
        .sorted(Comparator.comparing(Transaction::getTransactionTime)
            .thenComparing(t -> t.getId() == null ? Integer.MAX_VALUE : t.getId()))
        .toList();
    var splitMap = splits.getSecuritysplitMapByIdSecuritycurrency(security.getId());
    List<AlgoExecutionState> calculated = new ArrayList<>();
    AlgoExecutionState current = null;
    LocalDate lastEntry = null, lastExit = null;
    int tradeCount = 0;
    Set<String> additionSignals = new HashSet<>();
    String openingSignal = null;
    List<AlgoMeanReversionDecisionService.PositionFill> lifecycleFills = new ArrayList<>();
    for (Transaction t : trades) {
      double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(security.getId(),
          t.getTransactionDateAsLocalDate(), date.plusDays(1), splitMap).fromToDateFactor;
      double delta = Math.abs(t.getUnits()) * factor * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1);
      double price = t.getQuotation() / factor;
      if (!Double.isFinite(price) || price <= 0 || !Double.isFinite(delta) || delta == 0)
        throw invalid("Invalid assigned fill");
      if (current == null || Math.abs(current.getSignedUnits()) < 1e-8) {
        if (!security.isMarginInstrument() && delta < 0
            || security.isMarginInstrument() && t.getConnectedIdTransaction() != null)
          throw invalid("Assigned exit has no assigned opening position");
        current = new AlgoExecutionState();
        current.setIdTenant(tenant);
        current.setIdAlgoStrategy(strategy);
        current.setIdSecuritycurrency(security.getId());
        current.setLifecycle(t.getId());
        current.setInitialPrice(price);
        // The size the lifecycle opens with is what a tranche of the profit taking plan is measured against.
        current.setInitialUnits(Math.abs(delta));
        calculated.add(current);
        additionSignals.clear();
        lifecycleFills.clear();
        openingSignal = t.getAlgoSignalId();
      }
      double old = current.getSignedUnits();
      if (security.isMarginInstrument() && old != 0
          && (t.getConnectedIdTransaction() == null) != (Math.signum(old) == Math.signum(delta)))
        throw invalid("A strategy cannot hold opposing margin positions simultaneously");
      if (old == 0 || Math.signum(old) == Math.signum(delta)) {
        if (old != 0) {
          if (openingSignal != null && openingSignal.equals(t.getAlgoSignalId())) {
            current.setInitialUnits(current.getInitialUnits() + Math.abs(delta));
            current.setInitialPrice((Math.abs(old) * current.getInitialPrice() + Math.abs(delta) * price)
                / (Math.abs(old) + Math.abs(delta)));
          } else {
            additionSignals.add(t.getAlgoSignalId() == null ? "manual:" + t.getId() : t.getAlgoSignalId());
          }
        }
        current.setAveragePrice(
            (Math.abs(old) * current.getAveragePrice() + Math.abs(delta) * price) / Math.abs(old + delta));
        lastEntry = t.getTransactionDateAsLocalDate();
      } else {
        if (Math.abs(delta) > Math.abs(old) + 1e-8)
          throw invalid("Assigned exit exceeds the strategy position");
        // Every reduction counts against the plan, whoever booked it, so a tranche can never execute twice.
        current.setRealizedExitUnits(current.getRealizedExitUnits() + Math.abs(delta));
        lastExit = t.getTransactionDateAsLocalDate();
      }
      current.setSignedUnits(Math.abs(old + delta) < 1e-8 ? 0 : old + delta);
      lifecycleFills.add(new AlgoMeanReversionDecisionService.PositionFill(delta,
          AlgoTrancheTargets.read(t.getAlgoTrancheTargets(), factor)));
      if (t.getTransactionDateAsLocalDate().isAfter(date.minusDays(30)))
        tradeCount++;
    }
    var stored = states.findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyOrderByLifecycle(tenant, strategy,
        security.getId());
    int extra = calculated.size() - stored.size();
    var limit = limits.resolveForCurrentUser(LimitKeyConfig.KEY_ALGO_EXECUTION_STATE);
    if (limit.isPresent() && states.countByIdTenant(tenant) + extra > limit.get())
      throw invalid("Execution-state limit reached");
    Map<Long, AlgoExecutionState> old = new HashMap<>();
    stored.forEach(s -> old.put(s.getLifecycle(), s));
    for (var s : calculated) {
      var previous = old.remove(s.getLifecycle());
      if (previous != null)
        s.setIdAlgoExecutionState(previous.getId());
    }
    states.deleteAll(old.values());
    states.saveAll(calculated);
    return current == null ? AlgoMeanReversionDecisionService.Position.empty()
        : new AlgoMeanReversionDecisionService.Position(current.getLifecycle(), current.getSignedUnits(),
            current.getInitialUnits(), current.getRealizedExitUnits(), current.getInitialPrice(),
            current.getAveragePrice(), lastEntry, lastExit, tradeCount, additionSignals.size(),
            List.copyOf(lifecycleFills));
  }

  /** Projections are invalidated atomically with ledger writes and rebuilt before the next decision. */
  @Transactional
  public void invalidate(Integer tenant) {
    states.deleteByIdTenant(tenant);
  }

  public static boolean isTrade(Transaction t) {
    return t.getTransactionType() == TransactionType.ACCUMULATE || t.getTransactionType() == TransactionType.REDUCE;
  }

  private static DataViolationException invalid(String detail) {
    return new DataViolationException("strategy.config", "algo.strategy.config.invalid", new Object[] { detail });
  }
}
