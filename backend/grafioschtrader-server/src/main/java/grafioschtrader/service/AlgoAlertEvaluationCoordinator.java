package grafioschtrader.service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.AlgoAlertSchedule.Window;

/**
 * Coordinates shared quote refresh, exchange eligibility and durable per-alert scheduling for every alert entry point.
 */
@Service
public class AlgoAlertEvaluationCoordinator {
  private static final Logger log = LoggerFactory.getLogger(AlgoAlertEvaluationCoordinator.class);
  private static final Set<AlgoStrategyImplementationType> TYPES = EnumSet.of(
      AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE,
      AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE,
      AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT,
      AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING,
      AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD,
      AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_EXPRESSION);

  public static boolean isAlertType(AlgoStrategyImplementationType type) {
    return TYPES.contains(type);
  }

  private final FeatureConfig features;
  private final AlgoAlertScopeResolver resolver;
  private final AlgoAlertEvaluationStateJpaRepository repository;
  private final AlgoAlertEvaluationStateService states;
  private final AlgoAlertStateService crossings;
  private final SecurityJpaRepository securities;
  private final TradingDaysMinusJpaRepository holidays;
  private final GlobalparametersService parameters;
  private final List<IFeedConnector> connectors;
  private final AlgoAlarmEvaluationService evaluator;
  private Clock clock = Clock.systemUTC();

  /** Dependencies retain the existing evaluator for condition calculations and notification delivery. */
  public AlgoAlertEvaluationCoordinator(FeatureConfig features, AlgoAlertScopeResolver resolver,
      AlgoAlertEvaluationStateJpaRepository repository, AlgoAlertEvaluationStateService states,
      AlgoAlertStateService crossings, SecurityJpaRepository securities, TradingDaysMinusJpaRepository holidays,
      GlobalparametersService parameters, List<IFeedConnector> connectors, @Lazy AlgoAlarmEvaluationService evaluator) {
    this.features = features;
    this.resolver = resolver;
    this.repository = repository;
    this.states = states;
    this.crossings = crossings;
    this.securities = securities;
    this.holidays = holidays;
    this.parameters = parameters;
    this.connectors = connectors;
    this.evaluator = evaluator;
  }

  /** Cheap scheduler entry: no quotes or history are downloaded here. */
  public boolean hasDueAlerts() {
    if (!enabled())
      return false;
    return !dueScopes(scopes(null), now(), parameters.getAlgoAlarmEvaluationIntervalHours()).isEmpty();
  }

  /** Runs due background work, rechecking after any delay in the task queue. */
  public void background() {
    if (!enabled())
      return;
    int hours = parameters.getAlgoAlarmEvaluationIntervalHours();
    run(dueScopes(scopes(null), now(), hours), hours, true, false);
  }

  /** Manual invocation stays tenant-scoped and bypasses calendar and frequency restrictions. */
  public void manual(Integer tenant) {
    if (!enabled())
      return;
    run(scopes(tenant).stream().map(s -> new Candidate(s, new Window(null, null, false, null))).toList(),
        parameters.getAlgoAlarmEvaluationIntervalHours(), false, false);
  }

  /** Fresh observations can evaluate before the background interval; repeated quote timestamps are ignored. */
  public void intraday(List<Security> updated) {
    if (!enabled() || updated == null || updated.isEmpty())
      return;
    Map<Integer, Security> byId = updated.stream().filter(Objects::nonNull)
        .collect(Collectors.toMap(Security::getIdSecuritycurrency, s -> s, (a, _) -> a));
    LocalDateTime now = now();
    List<Candidate> candidates = scopes(null).stream()
        .filter(s -> byId.containsKey(s.security().getIdSecuritycurrency()))
        .map(s -> new AlgoAlertScope(s.idTenant(), s.strategy(), byId.get(s.security().getIdSecuritycurrency()),
            s.contextName(), true))
        .map(s -> new Candidate(s, window(s.security(), now)))
        .filter(c -> c.window().eligible() && !c.window().closing()).toList();
    run(candidates, parameters.getAlgoAlarmEvaluationIntervalHours(), false, true);
  }

  private boolean enabled() {
    return features.isAlgo() && features.isAlert();
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record Candidate(AlgoAlertScope scope, Window window) {
  }

  private record Claimed(Candidate candidate, String token) {
  }

  private String key(Integer tenant, Integer strategy, Integer security) {
    return tenant + ":" + strategy + ":" + security;
  }

  private String key(AlgoAlertScope s) {
    return key(s.idTenant(), s.strategy().getIdAlgoRuleStrategy(), s.security().getIdSecuritycurrency());
  }

  private List<AlgoAlertScope> scopes(Integer tenant) {
    List<AlgoAlertScope> all = tenant == null ? resolver.resolveAll() : resolver.resolveForTenant(tenant);
    List<AlgoAlertScope> active = all.stream()
        .filter(s -> TYPES.contains(s.strategy().getAlgoStrategyImplementations())).filter(AlgoAlertScope::active)
        .toList();
    Set<String> retained = active.stream().map(this::key).collect(Collectors.toSet());
    List<AlgoAlertEvaluationState> stored = tenant == null ? repository.findAll() : repository.findByIdTenant(tenant);
    for (AlgoAlertEvaluationState state : stored) {
      if (!retained.contains(key(state.getIdTenant(), state.getIdAlgoStrategy(), state.getIdSecuritycurrency()))) {
        crossings.discard(state.getIdAlgoStrategy(), state.getIdSecuritycurrency());
        repository.deleteById(state.getId());
      }
    }
    for (AlgoAlertScope s : all) {
      if (!s.active())
        crossings.discard(s.strategy().getIdAlgoRuleStrategy(), s.security().getIdSecuritycurrency());
    }
    return active;
  }

  private List<Candidate> dueScopes(List<AlgoAlertScope> scopes, LocalDateTime now, int hours) {
    List<Candidate> due = new ArrayList<>();
    Map<Integer, Window> windows = new HashMap<>();
    Map<String, AlgoAlertEvaluationState> stored = repository.findAll().stream()
        .collect(Collectors.toMap(s -> key(s.getIdTenant(), s.getIdAlgoStrategy(), s.getIdSecuritycurrency()), s -> s));
    for (AlgoAlertScope s : scopes) {
      Window window = windows.computeIfAbsent(s.security().getIdSecuritycurrency(), _ -> window(s.security(), now));
      AlgoAlertEvaluationState state = stored.get(key(s));
      if (AlgoAlertSchedule.due(state, AlertConfigAdapter.fingerprint(s.strategy()), now, hours, forClaim(window)))
        due.add(new Candidate(s, window));
    }
    return due;
  }

  // Configuration failures receive a diagnostic at the ordinary interval, without downloading anything.
  private Window forClaim(Window window) {
    return window.reason() != null && (window.reason().startsWith("Missing") || window.reason().startsWith("Invalid"))
        ? new Window(null, null, false, null)
        : window;
  }

  private Window window(Security security, LocalDateTime now) {
    if ((security.getActiveFromDate() != null && now.toLocalDate().isBefore(security.getActiveFromDate()))
        || (security.getActiveToDate() != null && now.toLocalDate().isAfter(security.getActiveToDate())))
      return new Window(null, null, false, "Instrument outside its active dates");
    var asset = security.getAssetClass();
    boolean crypto = asset != null && IFeedConnector.AssetclassCategory.CRYPTOCURRENCY.matches(asset.getCategoryType(),
        asset.getSpecialInvestmentInstrument());
    Stockexchange exchange = security.getStockexchange();
    if (crypto)
      return new Window(null, null, false, null);
    if (exchange == null)
      return new Window(null, null, false, "Missing exchange");
    Set<LocalDate> closed = new HashSet<>();
    LocalDate utcDate = now.toLocalDate();
    holidays
        .findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(
            exchange.getIdStockexchange(), utcDate.minusDays(2), utcDate.plusDays(1))
        .forEach(day -> closed.add(day.getTradingDateMinus()));
    IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(connectors, security.getIdConnectorIntra(),
        IFeedConnector.FeedSupport.FS_INTRA);
    return AlgoAlertSchedule.window(now.toInstant(ZoneOffset.UTC), false, exchange.getTimeZone(),
        exchange.getTimeOpen(), exchange.getTimeClose(), closed,
        connector == null ? 0 : connector.getIntradayDelayedSeconds());
  }

  private void run(List<Candidate> candidates, int hours, boolean background, boolean intraday) {
    List<Claimed> claimed = new ArrayList<>();
    for (Candidate c : candidates) {
      try {
        String token = states.claim(c.scope(), now(), hours, forClaim(c.window()), background, intraday);
        if (token != null)
          claimed.add(new Claimed(c, token));
      } catch (RuntimeException e) {
        log.warn("Could not claim alert {}: {}", key(c.scope()), e.getMessage());
      }
    }
    Map<Integer, Security> quotes = new HashMap<>();
    Set<Integer> failedRefreshes = new HashSet<>();
    for (Claimed item : claimed) {
      Candidate c = item.candidate();
      Security security = c.scope().security();
      Integer id = security.getIdSecuritycurrency();
      if (quotes.containsKey(id) || !c.window().eligible())
        continue;
      if (!intraday
          && !AlgoAlertSchedule.fresh(security.getSLast(), security.getSTimestamp(), now(), hours, c.window())) {
        try {
          // One instrument per call isolates a failing provider from unrelated instruments.
          List<Security> loaded = securities.updateLastPriceByList(List.of(security));
          if (loaded != null && !loaded.isEmpty() && loaded.getFirst() != null)
            security = loaded.getFirst();
          if (security.getRetryIntraLoad() > 0)
            failedRefreshes.add(id);
        } catch (RuntimeException e) {
          failedRefreshes.add(id);
          log.warn("Alert quote refresh failed for instrument {}: {}", id, e.getMessage());
        }
      }
      quotes.put(id, security);
    }
    for (Claimed item : claimed) {
      Candidate c = item.candidate();
      AlgoAlertScope scope = c.scope();
      Security quote = quotes.getOrDefault(scope.security().getIdSecuritycurrency(), scope.security());
      LocalDateTime evaluatedAt = now();
      String reason = c.window().reason();
      if (reason == null && failedRefreshes.contains(quote.getIdSecuritycurrency()))
        reason = "Quote refresh failed";
      if (reason == null
          && !AlgoAlertSchedule.fresh(quote.getSLast(), quote.getSTimestamp(), evaluatedAt, hours, c.window()))
        reason = "Required quote is missing or stale";
      try {
        states.finish(scope, item.token(), evaluatedAt, quote.getSTimestamp(), reason, () -> {
          try {
            evaluator.evaluateOne(scope, quote, scope.strategy().getAlgoStrategyImplementations(),
                evaluatedAt.toLocalDate());
          } catch (AlgoAlertEvaluationStateService.PartialEvaluationException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
          }
        });
      } catch (RuntimeException e) {
        states.failed(scope, item.token(), e.getMessage());
        log.warn("Could not commit alert evaluation {}: {}", key(scope), e.getMessage());
      }
    }
  }
}
