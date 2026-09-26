package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Action;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Context;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Decision;
import grafioschtrader.service.AlgoMeanReversionDecisionService.MarketData;

/**
 * Resolves one closing day of a hierarchy into decisions, with every budget of that day already applied.
 *
 * <p>
 * This is the part of a mean reversion evaluation that does not care who is asking. It walks the hierarchy, checks that
 * the instrument is on the watchlist and carries an unambiguous allocation, values the tenant at the given close,
 * derives the top, bucket and instrument capacity from that valuation, and asks
 * {@link AlgoMeanReversionDecisionService} what to do. Proposals of the same day compete for the same equity, so an
 * entry already proposed earlier in the day reserves its amount against the later ones; and a second pass blocks an
 * exposure increase on an instrument that another strategy is exiting on the same day.
 * </p>
 *
 * <p>
 * What it deliberately does not do is decide <em>when</em> the day is, persist anything, or notify anyone. The wall
 * clock, the recommendation rows and the alarm delivery belong to {@link AlgoMeanReversionEvaluationService}, which is
 * the live caller; a historical replay supplies a past day and its own observations and books fills instead. That
 * separation is the reason a replay and the live evaluation cannot drift apart: there is one implementation of the
 * budget rules.
 * </p>
 */
@Service
public class AlgoMeanReversionScopeEvaluator {

  /**
   * One instrument of one strategy on one closing day, after every budget and ordering rule has been applied.
   *
   * @param scope            the strategy and instrument the decision belongs to
   * @param idAlgoTop        the hierarchy the instrument was reached through
   * @param member           the {@code AlgoSecurity} node that carries its weight, null when the allocation could not
   *                         be resolved and the decision is therefore unavailable
   * @param valuationDate    the closing day the decision was taken on, which for an instrument whose exchange was
   *                         closed is earlier than the day the caller asked for
   * @param decision         what to do, including the blocked and unavailable outcomes
   * @param context          the inputs the decision was taken from; a caller that books a fill needs it again
   * @param securityExposure gross exposure of the instrument in tenant currency before the decision
   * @param amount           size of the proposal in tenant currency, zero when nothing is proposed
   * @param unitExposure     tenant currency value of one unit, including the value per point of a margin instrument
   */
  public record Proposal(AlgoAlertScope scope, Integer idAlgoTop, AlgoSecurity member, LocalDate valuationDate,
      Decision decision, Context context, double securityExposure, double amount, double unitExposure) {
  }

  private final AlgoTopJpaRepository tops;
  private final AlgoAssetclassJpaRepository buckets;
  private final AlgoSecurityJpaRepository members;
  private final AlgoAlertScopeResolver scopes;
  private final AlgoHistoricalValuationService valuation;
  private final AlgoMeanReversionPositionService positions;
  private final AlgoMeanReversionDecisionService decisions;
  private final SimulationSourceRepository source;
  private final WatchlistJpaRepository watchlists;
  private final AlgoReplayCalendar calendar;

  public AlgoMeanReversionScopeEvaluator(AlgoTopJpaRepository tops, AlgoAssetclassJpaRepository buckets,
      AlgoSecurityJpaRepository members, AlgoAlertScopeResolver scopes, AlgoHistoricalValuationService valuation,
      AlgoMeanReversionPositionService positions, AlgoMeanReversionDecisionService decisions,
      SimulationSourceRepository source, WatchlistJpaRepository watchlists, AlgoReplayCalendar calendar) {
    this.tops = tops;
    this.buckets = buckets;
    this.members = members;
    this.scopes = scopes;
    this.valuation = valuation;
    this.positions = positions;
    this.decisions = decisions;
    this.source = source;
    this.watchlists = watchlists;
    this.calendar = calendar;
  }

  /**
   * Evaluates every activatable mean reversion scope of a hierarchy against one closing day.
   *
   * @param idTenant      tenant whose positions, cash and ledger the decisions are taken against; the simulation
   *                      environment during a replay
   * @param idOwnerTenant tenant that owns the hierarchy and its strategies, which stays the main tenant even while a
   *                      simulation is being replayed
   * @param onlyIdAlgoTop restricts the evaluation to a single hierarchy, which is what a simulation environment needs;
   *                      null evaluates every hierarchy of the owner
   * @param through       the closing day to evaluate; an instrument whose exchange was closed is evaluated against its
   *                      own last session before it
   * @param market        the observations the decisions may read, bounded by the caller to the requested day
   * @return the proposals of that day, risk reducing ones first, empty when the hierarchy has no such scope
   */
  /**
   * Answers whether {@link #evaluate} can find anything to decide on below the given AlgoTop, so that a replay whose
   * hierarchy holds no active mean reversion strategy does not read the whole hierarchy every day only to find nothing.
   * The selection is the one {@code evaluate} applies before its per-pair checks, including a redistributed allocation.
   *
   * @param idOwnerTenant the tenant owning the hierarchy
   * @param idAlgoTop     the AlgoTop the replay runs
   * @param market        the replay market data, which carries a redistributed allocation when there is one
   * @return true when at least one active mean reversion pair hangs below that AlgoTop
   */
  public boolean hasActiveMeanReversion(Integer idOwnerTenant, Integer idAlgoTop, MarketData market) {
    for (AlgoTop originalTop : tops.findByIdTenantOrderByName(idOwnerTenant)) {
      AlgoTop top = market.allocation() == null ? originalTop : market.allocation().top(originalTop);
      if (idAlgoTop.equals(top.getId()) && scopes.resolveForAlgoTop(top).stream()
          .anyMatch(s -> AlgoMeanReversionEvaluationService.isMeanReversion(s.strategy()) && s.active())) {
        return true;
      }
    }
    return false;
  }

  public List<Proposal> evaluate(Integer idTenant, Integer idOwnerTenant, Integer onlyIdAlgoTop, LocalDate through,
      MarketData market) {
    Map<Integer, List<Historyquote>> histories = new HashMap<>();
    Map<Integer, Double> reservedSecurity = new HashMap<>();
    double reservedTotal = 0;
    List<Proposal> pending = new ArrayList<>();
    for (AlgoTop originalTop : tops.findByIdTenantOrderByName(idOwnerTenant)) {
      AlgoTop top = market.allocation() == null ? originalTop : market.allocation().top(originalTop);
      if (onlyIdAlgoTop != null && !onlyIdAlgoTop.equals(top.getId())) {
        continue;
      }
      List<AlgoAssetclass> topBuckets = buckets.findByIdTenantAndIdAlgoAssetclassParent(idOwnerTenant, top.getId());
      if (market.allocation() != null)
        topBuckets = market.allocation().buckets(topBuckets);
      Set<Integer> watchlist = new HashSet<>();
      if (top.getIdWatchlist() != null) {
        watchlists.securitiesOfWatchlist(top.getIdWatchlist()).forEach(s -> watchlist.add(s.getId()));
      }
      var resolved = scopes.resolveForAlgoTop(top).stream()
          .filter(s -> AlgoMeanReversionEvaluationService.isMeanReversion(s.strategy()) && s.active())
          .sorted(
              Comparator.comparing((AlgoAlertScope s) -> s.strategy().getId()).thenComparing(s -> s.security().getId()))
          .toList();
      for (AlgoAlertScope scope : resolved) {
        if (market.tradingExcluded(scope.security()))
          continue;
        List<Historyquote> history = histories.computeIfAbsent(scope.security().getId(),
            id -> market.history(id, through));
        LocalDate date = through;
        Decision decision;
        Context context = null;
        AlgoSecurity member = null;
        double amount = 0, unit = 0, exposure = 0;
        try {
          var config = StrategyConfigValidator.executable(scope.strategy().getStrategyConfig());
          date = calendar.completedDate(scope.security(), through);
          if (history.isEmpty() || !history.getLast().getDate().equals(date)) {
            throw new IllegalArgumentException("MEAN_REVERSION_NO_CLOSE");
          }
          if (!watchlist.contains(scope.security().getId())) {
            throw new IllegalArgumentException("MEAN_REVERSION_OUTSIDE_WATCHLIST");
          }
          AlgoExposureBudget.requireWeights(topBuckets.stream().map(AlgoAssetclass::getPercentage).toList());
          AlgoAssetclass bucket = null;
          List<AlgoSecurity> bucketMembers = List.of();
          for (var b : topBuckets) {
            var ms = members.findByIdAlgoSecurityParentAndIdTenant(b.getId(), idOwnerTenant);
            if (market.allocation() != null)
              ms = market.allocation().securities(ms);
            for (var m : ms) {
              if (m.getSecurity() != null && m.getSecurity().getId().equals(scope.security().getId())) {
                if (member != null) {
                  throw new IllegalArgumentException("MEAN_REVERSION_AMBIGUOUS_ALLOCATION");
                }
                member = m;
                bucket = b;
                bucketMembers = ms;
              }
            }
          }
          if (member == null) {
            throw new IllegalArgumentException("MEAN_REVERSION_ALLOCATION_REQUIRED");
          }
          AlgoExposureBudget.requireWeights(bucketMembers.stream().map(AlgoSecurity::getPercentage).toList());
          if (top.getPercentage() == null || top.getPercentage() < 0 || top.getPercentage() > 100) {
            throw new IllegalArgumentException("MEAN_REVERSION_ALLOCATION_REQUIRED");
          }
          var snapshot = valuation.value(idTenant, date, market, Set.of(scope.security().getCurrency()));
          snapshot.requireAvailable();
          var p = positions.reconcile(idTenant, scope.strategy().getId(), scope.security(), date);
          Set<Integer> bucketIds = new HashSet<>();
          bucketMembers.forEach(m -> {
            if (m.getSecurity() != null) {
              bucketIds.add(m.getSecurity().getId());
            }
          });
          double bucketExposure = snapshot.positions().stream().filter(x -> bucketIds.contains(x.security().getId()))
              .mapToDouble(x -> x.grossExposure() * snapshot.fx().get(x.security().getCurrency())).sum();
          exposure = snapshot.positions().stream().filter(x -> x.security().getId().equals(scope.security().getId()))
              .mapToDouble(x -> x.grossExposure() * snapshot.fx().get(x.security().getCurrency())).sum();
          double price = history.isEmpty() ? 0 : history.getLast().getClose();
          Double fx = snapshot.fx().get(scope.security().getCurrency());
          if (fx == null && scope.security().getCurrency().equals(snapshot.currency())) {
            fx = 1.0;
          }
          if (fx == null) {
            throw new IllegalArgumentException("MEAN_REVERSION_FX_UNAVAILABLE");
          }
          unit = price * fx;
          if (scope.security().isMarginInstrument()) {
            unit *= marginValuePerPoint(idTenant, scope, date);
          }
          boolean assigned = positions.assignmentResolved(idTenant, scope.security(), date);
          double topBudget = AlgoExposureBudget.amount(snapshot.equity(), top.getPercentage());
          double bucketBudget = AlgoExposureBudget.amount(topBudget, bucket.getPercentage());
          double securityBudget = AlgoExposureBudget.amount(bucketBudget, member.getPercentage());
          double reservedBucket = bucketIds.stream().mapToDouble(id -> reservedSecurity.getOrDefault(id, 0.0)).sum();
          context = new Context(idTenant, scope.strategy().getId(), scope.security().getId(), date, market, p,
              snapshot.equity(), topBudget - snapshot.grossExposure(), bucketBudget - bucketExposure,
              securityBudget - exposure, exposure, unit, assigned, reservedTotal, reservedBucket,
              reservedSecurity.getOrDefault(scope.security().getId(), 0.0));
          decision = decisions.evaluate(config, context);
          if (decision.increasesExposure() && decision.direction() < 0 && !scope.security().isMarginInstrument()) {
            decision = new Decision(Action.UNAVAILABLE, -1, 0, price, "MEAN_REVERSION_SHORT_MARGIN_REQUIRED",
                decision.identity(), decision.tranche());
          }
          amount = decision.quantity() * unit;
        } catch (IllegalArgumentException | grafiosch.exceptions.DataViolationException
            | jakarta.validation.ValidationException | tools.jackson.core.JacksonException e) {
          decision = new Decision(Action.UNAVAILABLE, 0, 0, history.isEmpty() ? 0 : history.getLast().getClose(),
              (e.getMessage() != null && e.getMessage().matches("MEAN_REVERSION_[A-Z_]+") ? e.getMessage()
                  : "MEAN_REVERSION_UNAVAILABLE: " + e.getMessage()),
              idTenant + ":" + scope.strategy().getId() + ":" + scope.security().getId() + ":" + date,
              AlgoMessageAlert.NO_TRANCHE);
        }
        if (decision.increasesExposure()) {
          reservedTotal += amount;
          reservedSecurity.merge(scope.security().getId(), amount, Double::sum);
        }
        pending.add(new Proposal(scope, top.getId(), member, date, decision, context, exposure, amount, unit));
      }
    }
    return orderExitsBeforeEntries(pending);
  }

  /**
   * The value per point an open margin position of this instrument is booked with. There is no such factor on the
   * instrument itself, so it is read from the fills the tenant already has; without one, the size of an order cannot be
   * expressed in money at all.
   */
  private double marginValuePerPoint(Integer idTenant, AlgoAlertScope scope, LocalDate date) {
    var templates = source
        .transactions(idTenant, date.plusDays(1)).stream().filter(t -> t.getSecurity() != null
            && t.getSecurity().getId().equals(scope.security().getId()) && AlgoMeanReversionPositionService.isTrade(t))
        .toList();
    if (templates.isEmpty()) {
      throw new IllegalArgumentException("MEAN_REVERSION_MARGIN_TEMPLATE_REQUIRED");
    }
    double factor = templates.getLast().getValuePerPoint();
    if (templates.stream().anyMatch(t -> t.getValuePerPoint() != factor)) {
      throw new IllegalArgumentException("MEAN_REVERSION_MARGIN_TEMPLATE_REQUIRED");
    }
    return factor;
  }

  /**
   * Risk reducing proposals come first, and an exposure increase on an instrument that is being exited on the same day
   * is refused. Two strategies can hold the same instrument, and buying it back within the same evaluation would
   * silently undo the exit of the other one.
   */
  private List<Proposal> orderExitsBeforeEntries(List<Proposal> pending) {
    Set<Integer> exiting = new HashSet<>();
    pending.stream().filter(p -> p.decision().actionable() && !p.decision().increasesExposure())
        .forEach(p -> exiting.add(p.scope().security().getId()));
    List<Proposal> ordered = new ArrayList<>();
    for (var proposal : pending.stream().sorted(Comparator.comparing(p -> p.decision().increasesExposure())).toList()) {
      Decision decision = proposal.decision();
      if (decision.increasesExposure() && exiting.contains(proposal.scope().security().getId())) {
        ordered.add(new Proposal(proposal.scope(), proposal.idAlgoTop(), proposal.member(), proposal.valuationDate(),
            new Decision(Action.BLOCKED, decision.direction(), 0, decision.price(), "MEAN_REVERSION_PENDING_EXIT",
                decision.identity(), decision.tranche()),
            proposal.context(), proposal.securityExposure(), 0, proposal.unitExposure()));
      } else {
        ordered.add(proposal);
      }
    }
    return ordered;
  }
}
