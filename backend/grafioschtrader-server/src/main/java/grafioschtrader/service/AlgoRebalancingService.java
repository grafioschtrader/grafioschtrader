package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.service.AlgoHistoricalValuationService.Position;
import grafioschtrader.service.AlgoHistoricalValuationService.Snapshot;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import grafioschtrader.types.AlgoSignalKind;

/**
 * Compares an AlgoTop against the holdings it describes and says what would restore the configured allocation.
 *
 * <p>
 * The service decides, it does not act. A live evaluation writes the plan and raises a notification; the portfolio is
 * changed by the user, through the ordinary transaction path. Historical replay and forward scenarios will drive the
 * same calculation with their own market observations, which is why the plan is built from a supplied snapshot rather
 * than from whatever the database happens to hold at the moment of the call.
 * </p>
 *
 * <p>
 * Two things are easy to get wrong here and are therefore stated once. The hierarchy stores <em>parent-relative</em>
 * weights, so a bucket at 50% of a 48.2% ceiling is 24.1% of equity and not 50% of anything the report shows; every
 * percentage this service produces has already been resolved against total net equity. And the quantity being allocated
 * is gross exposure, not net position value: a short position and a margin position contribute their notional to the
 * budget while contributing only their unrealised result to equity.
 * </p>
 */
@Service
public class AlgoRebalancingService {

  private static final Logger log = LoggerFactory.getLogger(AlgoRebalancingService.class);

  /** The line is inside its tolerance, so nothing is proposed. */
  public static final String REASON_WITHIN_TOLERANCE = "REBALANCE_WITHIN_TOLERANCE";
  /** The position holds more exposure than its target allows. */
  public static final String REASON_ABOVE_TARGET = "REBALANCE_ABOVE_TARGET";
  /** The position holds less exposure than its target allows. */
  public static final String REASON_BELOW_TARGET = "REBALANCE_BELOW_TARGET";
  /** A tactical bucket is below its target; only its own entry strategy may open that position. */
  public static final String REASON_TACTICAL_CEILING = "REBALANCE_TACTICAL_CEILING";
  /** Gross exposure is already above the permitted ceiling, so no increase is proposed. */
  public static final String REASON_EXPOSURE_BREACH = "REBALANCE_EXPOSURE_BREACH";
  /** Net equity is not positive, so no exposure increasing order can be sized at all. */
  public static final String REASON_NON_POSITIVE_EQUITY = "REBALANCE_NON_POSITIVE_EQUITY";
  /** The monetary difference is known but the instrument has no usable price to turn it into units. */
  public static final String REASON_NO_PRICE = "REBALANCE_NO_PRICE";

  /** Percentage points below which a deviation is treated as rounding rather than as drift. */
  private static final double WEIGHT_EPSILON = 0.01;

  @Autowired
  private AlgoHistoricalValuationService valuation;

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private AlgoRecommendationJpaRepository algoRecommendationJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private AlgoRecommendationWriter algoRecommendationWriter;

  @Autowired
  private AlgoAlarmRecorder algoAlarmRecorder;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private FeatureConfig features;

  /**
   * The last completed day, which is the newest day a closing valuation may be asked for.
   * {@link AlgoHistoricalValuationService#validateDate(LocalDate)} rejects today, because an unfinished day has no
   * closing price and the plan would compare against a moving target.
   *
   * @return yesterday
   */
  public static LocalDate lastCompletedDay() {
    return LocalDate.now().minusDays(1);
  }

  /**
   * Builds the plan of one AlgoTop against the closing state of a day, reading prices from the historical repository.
   *
   * @param idTenant      tenant whose positions are compared; for a live evaluation the tenant that owns the AlgoTop
   * @param algoTop       the hierarchy that defines the targets
   * @param valuationDate completed day whose closing holdings and prices are used
   * @param locale        language of the bucket labels; nothing that reaches the database depends on it
   * @return the resolved comparison
   * @throws DataViolationException if the AlgoTop carries no rebalancing configuration, its weights do not add up, or
   *                                the day cannot be valued
   */
  @Transactional(readOnly = true)
  public RebalancingPlan plan(Integer idTenant, AlgoTop algoTop, LocalDate valuationDate, Locale locale) {
    return plan(idTenant, algoTop, valuationDate, locale, null);
  }

  /**
   * Builds the plan against a caller supplied date of the previous redeployment.
   *
   * <p>
   * The stored plan carries the day of the last live checkpoint, which is the right answer for a live evaluation and
   * the wrong one for a historical replay: a replay walks its own timeline, and asking the stored plan when it last
   * rebalanced would either make every replayed day due or none of them. A replay therefore remembers the day it last
   * redeployed and passes it in here.
   * </p>
   *
   * @param idTenant         tenant whose positions are compared
   * @param algoTop          the hierarchy that defines the targets
   * @param valuationDate    completed day whose closing holdings and prices are used
   * @param locale           language of the bucket labels
   * @param lastRebalancedOn day of the previous redeployment, or null to read it from the stored plan; a null value
   *                         together with no stored plan makes the interval due, as it does for a hierarchy that was
   *                         never evaluated
   * @return the resolved comparison
   */
  @Transactional(readOnly = true)
  public RebalancingPlan plan(Integer idTenant, AlgoTop algoTop, LocalDate valuationDate, Locale locale,
      LocalDate lastRebalancedOn) {
    return plan(idTenant, algoTop, valuationDate, locale, lastRebalancedOn,
        new AlgoHistoricalValuationService.ClosingPrices() {
          public Double close(Integer id, LocalDate date) {
            return closingPrice(id, date);
          }

          public Long volume(Integer id, LocalDate date) {
            return historyquoteJpaRepository.findFirstByIdSecuritycurrencyAndDateLessThanEqualOrderByDateDesc(id, date)
                .map(grafioschtrader.entities.Historyquote::getVolume).orElse(null);
          }
        });
  }

  /** Replay variant includes the run's unpaid income and bounded historical observations. */
  @Transactional(readOnly = true)
  public RebalancingPlan plan(Integer idTenant, AlgoTop algoTop, LocalDate valuationDate, Locale locale,
      LocalDate lastRebalancedOn, AlgoHistoricalValuationService.ClosingPrices market) {
    return evaluate(idTenant, algoTop, valuationDate, locale, lastRebalancedOn, market, false);
  }

  /** Initial construction fills exact targets without the periodic selection cap or security bands. */
  @Transactional(readOnly = true)
  public RebalancingPlan initialPlan(Integer idTenant, AlgoTop algoTop, LocalDate date, Locale locale,
      AlgoHistoricalValuationService.ClosingPrices market) {
    return evaluate(idTenant, algoTop, date, locale, date, market, true);
  }

  private RebalancingPlan evaluate(Integer idTenant, AlgoTop algoTop, LocalDate valuationDate, Locale locale,
      LocalDate lastRebalancedOn, AlgoHistoricalValuationService.ClosingPrices market, boolean initial) {
    if (market.allocation() != null)
      algoTop = market.allocation().top(algoTop);
    AlgoStrategy strategy = requireRebalancingStrategy(algoTop);
    RebalancingTop config = requireConfig(strategy, algoTop);
    Snapshot snapshot = valuation.value(idTenant, valuationDate, market);
    snapshot.requireAvailable();
    return build(idTenant, algoTop, strategy, config, snapshot, valuationDate, locale, lastRebalancedOn, market,
        initial);
  }

  /**
   * Builds the plan of one AlgoTop identified by id, checking that it belongs to the given configuration owner. This is
   * the entry point of the report endpoint, where the id arrives from the browser.
   *
   * @param idTenant      tenant whose positions are compared
   * @param idOwnerTenant tenant that owns the hierarchy, which is the main tenant even in a simulation
   * @param idAlgoTop     the requested AlgoTop
   * @param valuationDate completed day whose closing holdings and prices are used
   * @param locale        language of the bucket labels
   * @return the resolved comparison
   * @throws DataViolationException if the AlgoTop does not exist for that owner, or the plan cannot be built
   */
  @Transactional(readOnly = true)
  public RebalancingPlan planById(Integer idTenant, Integer idOwnerTenant, Integer idAlgoTop, LocalDate valuationDate,
      Locale locale) {
    AlgoTop algoTop = algoTopJpaRepository.findByIdTenantAndIdAlgoAssetclassSecurity(idOwnerTenant, idAlgoTop);
    if (algoTop == null) {
      throw AlgoHistoricalValuationService.invalid("id.algo.top", "simulation.algotop.not.found", idAlgoTop);
    }
    return plan(idTenant, algoTop, valuationDate, locale);
  }

  private Double closingPrice(Integer idSecuritycurrency, LocalDate asOf) {
    var result = historyquoteJpaRepository.getIdDateCloseByIdsAndDate(List.of(idSecuritycurrency), asOf);
    return result.isEmpty() ? null : result.getFirst().getClose();
  }

  /**
   * Whether the hierarchy carries a rebalancing strategy at all. A caller that treats an unconfigured hierarchy as an
   * ordinary case asks this first, so that everything {@link #plan} still refuses is a real impediment worth reporting
   * rather than a configuration decision to be passed over in silence.
   *
   * @param algoTop the hierarchy to look at
   * @return true when a rebalancing strategy is defined on its top level
   */
  @Transactional(readOnly = true)
  public boolean hasRebalancingStrategy(AlgoTop algoTop) {
    return findRebalancingStrategy(algoTop).isPresent();
  }

  private AlgoStrategy requireRebalancingStrategy(AlgoTop algoTop) {
    return findRebalancingStrategy(algoTop).orElseThrow(() -> AlgoHistoricalValuationService.invalid("id.algo.top",
        "algo.rebalancing.not.configured", algoTop.getName()));
  }

  private RebalancingTop requireConfig(AlgoStrategy strategy, AlgoTop algoTop) {
    RebalancingTop config = AlertConfigAdapter.read(strategy, RebalancingTop.class);
    if (config == null || config.getThresholdPercentage() == null || config.getTimePeriodPerYear() == null
        || !Double.isFinite(config.getSecurityDeviationPercentage())) {
      throw AlgoHistoricalValuationService.invalid("algo.rebalancing.not.configured", algoTop.getName());
    }
    return config;
  }

  /**
   * Calendar days between two checkpoints of this configuration. The configured value is redeployments per year, which
   * is the natural way to express the intention but not a quantity a day can be compared against.
   *
   * @param config the rebalancing configuration of the hierarchy
   * @return the interval, at least one day
   */
  public static long checkpointInterval(RebalancingTop config) {
    return Math.max(1, Math.round(365.0 / config.getTimePeriodPerYear()));
  }

  /**
   * Whether the allocation of this hierarchy is to be compared on that day at all.
   *
   * <p>
   * This exists separately from {@link #plan} because it is the cheap half of the question. Building a plan values
   * every position and reads a closing quote per instrument and per currency pair; asking the interval first means a
   * run of a thousand days with four redeployments per year pays for twelve of those valuations instead of a thousand.
   * A caller that already holds a plan reads {@link RebalancingPlan#periodicDue()} instead.
   * </p>
   *
   * @param idTenant         tenant whose positions would be compared
   * @param algoTop          the hierarchy that defines the targets
   * @param valuationDate    the day in question
   * @param lastRebalancedOn day of the previous redeployment, or null to read it from the stored plan; a null value
   *                         together with no stored plan makes the checkpoint due, which is how an environment that has
   *                         never been rebalanced reaches its targets on its first day
   * @return true when the interval has elapsed
   * @throws DataViolationException if the hierarchy carries no rebalancing configuration
   */
  @Transactional(readOnly = true)
  public boolean isCheckpointDue(Integer idTenant, AlgoTop algoTop, LocalDate valuationDate,
      LocalDate lastRebalancedOn) {
    return isCheckpointDue(idTenant, algoTop, requireConfig(requireRebalancingStrategy(algoTop), algoTop),
        valuationDate, lastRebalancedOn);
  }

  private Optional<AlgoStrategy> findRebalancingStrategy(AlgoTop algoTop) {
    return algoStrategyJpaRepository
        .findByIdAlgoAssetclassSecurityAndIdTenant(algoTop.getIdAlgoAssetclassSecurity(), algoTop.getIdTenant())
        .stream()
        .filter(s -> s.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING)
        .findFirst();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Plan construction
  // ---------------------------------------------------------------------------------------------------------------

  private RebalancingPlan build(Integer idTenant, AlgoTop algoTop, AlgoStrategy strategy, RebalancingTop config,
      Snapshot snapshot, LocalDate valuationDate, Locale locale, LocalDate lastRebalancedOn,
      AlgoHistoricalValuationService.ClosingPrices market, boolean initial) {
    double equity = snapshot.equity();
    double gross = snapshot.grossExposure();
    double topPercentage = requiredWeight(algoTop.getPercentage(), algoTop.getName());
    double budget = AlgoExposureBudget.amount(equity, topPercentage);
    double tolerance = config.getThresholdPercentage();
    boolean breach = equity <= 0 || gross > budget + Math.abs(equity) * WEIGHT_EPSILON / 100.0;

    Map<Integer, Double> exposure = exposureBySecurity(snapshot);
    Map<Integer, Double> signed = signedValueBySecurity(snapshot);
    UnitValues unitValue = new UnitValues(snapshot, unitValueBySecurity(snapshot), market);
    List<AlgoAssetclass> buckets = sortedBuckets(algoTop, locale);
    if (market.allocation() != null)
      buckets = market.allocation().buckets(buckets);
    requireWeightsComplete(buckets.stream().map(AlgoAssetclass::getPercentage).toList(), algoTop.getName());

    List<RebalancingPlan.Line> lines = new ArrayList<>();
    List<RebalancingPlan.ClassAdjustment> adjustments = new ArrayList<>();
    lines.add(topLine(algoTop, topPercentage, equity, gross, breach));
    double unusedTactical = 0;
    for (AlgoAssetclass bucket : buckets) {
      List<AlgoSecurity> members = algoSecurityJpaRepository
          .findByIdAlgoSecurityParentAndIdTenant(bucket.getIdAlgoAssetclassSecurity(), algoTop.getIdTenant());
      if (market.allocation() != null)
        members = market.allocation().securities(members);
      boolean tactical = isTactical(bucket, members, algoTop.getIdTenant());
      double bucketTargetPercentage = topPercentage * requiredWeight(bucket.getPercentage(), label(bucket, locale))
          / 100.0;
      double bucketActual = classExposure(members, exposure);
      boolean bucketShort = classExposure(members, signed) < 0;
      lines.add(line(StrategyHelper.ASSET_CLASS_LEVEL_LETTER, bucket.getIdAlgoAssetclassSecurity(),
          algoTop.getIdAlgoAssetclassSecurity(), null, label(bucket, locale), tactical, bucketTargetPercentage,
          bucketActual, equity, tolerance * topPercentage / 100, breach, bucketShort, null));
      if (tactical) {
        unusedTactical += Math.max(0, equity * bucketTargetPercentage / 100.0 - bucketActual);
      }
      if (initial) {
        lines.addAll(securityLines(algoTop, bucket, members, bucketTargetPercentage, equity, 0, breach, tactical,
            exposure, signed, unitValue, valuationDate, locale));
      } else {
        allocateClass(algoTop, bucket, members, config, bucketTargetPercentage, bucketActual, equity, budget, breach,
            tactical, exposure, signed, unitValue, valuationDate, locale, market, lines, adjustments);
      }
    }
    boolean driftDue = lines.stream().anyMatch(RebalancingPlan.Line::actionable);
    boolean periodicDue = isCheckpointDue(idTenant, algoTop, config, valuationDate, lastRebalancedOn);
    double cash = equity - snapshot.positions().stream().mapToDouble(p -> p.closingValue() * fx(snapshot, p)).sum();
    return new RebalancingPlan(idTenant, algoTop.getIdAlgoAssetclassSecurity(), strategy.getIdAlgoRuleStrategy(),
        algoTop.getName(), valuationDate, snapshot.currency(), equity, cash, gross, budget, unusedTactical,
        topPercentage, tolerance, breach, periodicDue, driftDue, lines, adjustments);
  }

  /** The parent decides the adjustment; securities only decide where that adjustment can be placed. */
  private void allocateClass(AlgoTop top, AlgoAssetclass bucket, List<AlgoSecurity> members, RebalancingTop config,
      double targetPercentage, double actual, double equity, double budget, boolean breach, boolean tactical,
      Map<Integer, Double> exposure, Map<Integer, Double> signed, UnitValues unitValues, LocalDate date, Locale locale,
      AlgoHistoricalValuationService.ClosingPrices market, List<RebalancingPlan.Line> lines,
      List<RebalancingPlan.ClassAdjustment> adjustments) {
    requireWeightsComplete(members.stream().map(AlgoSecurity::getPercentage).toList(), label(bucket, locale));
    double target = Math.max(0, equity * targetPercentage / 100);
    double gap = target - actual;
    double drift = budget > 0 ? -gap / budget * 100 : actual > 0 ? 100 : 0;
    double band = bucket.getSecurityDeviationPercentage() == null ? config.getSecurityDeviationPercentage()
        : bucket.getSecurityDeviationPercentage();
    int limit = bucket.getMaxTradedSecuritiesPerAssetclass() == null ? config.getMaxTradedSecuritiesPerAssetclass()
        : bucket.getMaxTradedSecuritiesPerAssetclass();
    boolean triggered = Math.abs(gap) > 1e-8
        && (budget <= 0 || Math.abs(gap) > budget * config.getThresholdPercentage() / 100 + 1e-8);
    String blocked = !bucket.isActivatable() ? "REBALANCE_INACTIVE"
        : tactical ? REASON_TACTICAL_CEILING
            : gap > 0 && breach ? (equity <= 0 ? REASON_NON_POSITIVE_EQUITY : REASON_EXPOSURE_BREACH) : null;
    ClassMembers prepared = classMembers(members, exposure, unitValues, date, market, triggered && blocked == null);
    List<AlgoClassRebalancingAllocator.Candidate> candidates = prepared.candidates();
    var allocation = triggered && blocked == null
        ? AlgoClassRebalancingAllocator.allocate(target, actual, band, limit, candidates, top.getId(), bucket.getId(),
            date)
        : new AlgoClassRebalancingAllocator.Selection(Map.of(), triggered ? gap : 0, blocked);
    double requested = triggered ? gap : 0;
    adjustments.add(new RebalancingPlan.ClassAdjustment(bucket.getId(), drift, band, limit, requested,
        requested - allocation.residual(), allocation.residual(), allocation.reason()));
    replaceClassLine(lines, drift, requested - allocation.residual(), allocation.reason());
    for (var candidate : candidates) {
      AlgoSecurity member = prepared.byId().get(candidate.idSecurity());
      lines.add(selectedLine(bucket, member, targetPercentage * candidate.weight() / 100, target, equity, candidate,
          signed.getOrDefault(candidate.idSecurity(), 0.0) < 0, unitValues.of(member.getSecurity(), date),
          allocation.changes().get(candidate.idSecurity()), tactical,
          !triggered ? REASON_WITHIN_TOLERANCE : blocked != null ? blocked : "REBALANCE_NOT_SELECTED"));
    }
  }

  private record ClassMembers(Map<Integer, AlgoSecurity> byId,
      List<AlgoClassRebalancingAllocator.Candidate> candidates) {
  }

  /** Multiple account assignments of an instrument share one exposure, target weight and selection slot. */
  private ClassMembers classMembers(List<AlgoSecurity> members, Map<Integer, Double> exposure, UnitValues unitValues,
      LocalDate date, AlgoHistoricalValuationService.ClosingPrices market, boolean needVolume) {
    Map<Integer, AlgoSecurity> distinct = new LinkedHashMap<>();
    Map<Integer, Double> weights = new LinkedHashMap<>();
    for (AlgoSecurity member : members) {
      if (member.getSecurity() != null) {
        int id = member.getSecurity().getIdSecuritycurrency();
        distinct.putIfAbsent(id, member);
        weights.merge(id, requiredWeight(member.getPercentage(), member.getSecurity().getName()), Double::sum);
      }
    }
    List<AlgoClassRebalancingAllocator.Candidate> candidates = new ArrayList<>();
    distinct.forEach((id, member) -> {
      Security security = member.getSecurity();
      Double unit = unitValues.of(security, date);
      boolean eligible = member.isActivatable() && unit != null && Double.isFinite(unit) && unit > 0
          && (security.getActiveFromDate() == null || !date.isBefore(security.getActiveFromDate()))
          && (security.getActiveToDate() == null || !date.isAfter(security.getActiveToDate()));
      candidates.add(new AlgoClassRebalancingAllocator.Candidate(id, weights.get(id), exposure.getOrDefault(id, 0.0),
          needVolume && eligible ? market.volume(id, date) : null, eligible));
    });
    return new ClassMembers(distinct, candidates);
  }

  private static double classExposure(List<AlgoSecurity> members, Map<Integer, Double> exposure) {
    return members.stream().filter(member -> member.getSecurity() != null)
        .map(member -> member.getSecurity().getIdSecuritycurrency()).distinct()
        .mapToDouble(id -> exposure.getOrDefault(id, 0.0)).sum();
  }

  /** Class recommendations show what the selected instruments can accomplish, with any residual reported separately. */
  private void replaceClassLine(List<RebalancingPlan.Line> lines, double drift, double change, String limitReason) {
    int index = lines.size() - 1;
    var line = lines.get(index);
    boolean selected = Math.abs(change) > 1e-8;
    lines.set(index, new RebalancingPlan.Line(line.levelType(), line.idNode(), line.idParentNode(), null, line.label(),
        line.tactical(), line.targetPercentage(), line.actualPercentage(), line.deviation(), line.targetAmount(),
        line.actualAmount(), selected ? line.action() : AlgoRecommendationAction.REBALANCE_HOLD,
        selected ? Math.abs(change) : null, null, limitReason == null ? line.rationale() : limitReason, drift, change));
  }

  /** Captures effective settings and hierarchy weights for the audit of a new run; old run JSON is never rewritten. */
  public Map<String, Object> configurationSnapshot(AlgoTop top) {
    RebalancingTop config = requireConfig(requireRebalancingStrategy(top), top);
    List<Map<String, Object>> classes = new ArrayList<>();
    for (AlgoAssetclass bucket : sortedBuckets(top, Locale.ROOT)) {
      List<Map<String, Object>> securities = new ArrayList<>();
      for (AlgoSecurity member : algoSecurityJpaRepository.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(),
          top.getIdTenant())) {
        if (member.getSecurity() != null)
          securities.add(Map.of("id", member.getSecurity().getId(), "weight", member.getPercentage(), "active",
              member.isActivatable()));
      }
      classes.add(Map.of("id", bucket.getId(), "weight", bucket.getPercentage(), "active", bucket.isActivatable(),
          "securityDeviationPercentage",
          bucket.getSecurityDeviationPercentage() == null ? config.getSecurityDeviationPercentage()
              : bucket.getSecurityDeviationPercentage(),
          "maxTradedSecuritiesPerAssetclass",
          bucket.getMaxTradedSecuritiesPerAssetclass() == null ? config.getMaxTradedSecuritiesPerAssetclass()
              : bucket.getMaxTradedSecuritiesPerAssetclass(),
          "securities", securities));
    }
    return Map.of("version", AlgoClassRebalancingAllocator.VERSION, "topId", top.getId(), "weight", top.getPercentage(),
        "configuration", config, "classes", classes, "tieBreak", "SplitMix64(topId,classId,dateEpochDay,securityId)");
  }

  private RebalancingPlan.Line selectedLine(AlgoAssetclass bucket, AlgoSecurity member, double targetPercentage,
      double classTarget, double equity, AlgoClassRebalancingAllocator.Candidate candidate, boolean shortPosition,
      Double unitValue, Double change, boolean tactical, String holdReason) {
    Security security = member.getSecurity();
    double actualPercentage = equity == 0 ? 0 : candidate.exposure() / equity * 100;
    double parentDeviation = classTarget > 0 ? candidate.exposure() / classTarget * 100 - candidate.weight() : 0;
    boolean selected = change != null;
    boolean sell = selected && ((change < 0) != shortPosition);
    String reason = selected ? change < 0 ? "REBALANCE_CLASS_REDUCTION" : "REBALANCE_CLASS_INCREASE"
        : !candidate.eligible() ? "REBALANCE_INELIGIBLE" : holdReason;
    return new RebalancingPlan.Line(StrategyHelper.SECURITY_LEVEL_LETTER, member.getId(), bucket.getId(),
        security.getId(), security.getName(), tactical, targetPercentage, actualPercentage,
        actualPercentage - targetPercentage, classTarget * candidate.weight() / 100, candidate.exposure(),
        selected ? sell ? AlgoRecommendationAction.REBALANCE_SELL : AlgoRecommendationAction.REBALANCE_BUY
            : REASON_EXPOSURE_BREACH.equals(reason) || REASON_NON_POSITIVE_EQUITY.equals(reason)
                ? AlgoRecommendationAction.REBALANCE_BLOCKED
                : AlgoRecommendationAction.REBALANCE_HOLD,
        selected ? Math.abs(change) : null, selected ? Math.abs(change) / unitValue : null, reason, parentDeviation,
        change);
  }

  private List<RebalancingPlan.Line> securityLines(AlgoTop algoTop, AlgoAssetclass bucket, List<AlgoSecurity> members,
      double bucketTargetPercentage, double equity, double tolerance, boolean breach, boolean tactical,
      Map<Integer, Double> exposure, Map<Integer, Double> signed, UnitValues unitValue, LocalDate valuationDate,
      Locale locale) {
    requireWeightsComplete(members.stream().map(AlgoSecurity::getPercentage).toList(), label(bucket, locale));
    List<RebalancingPlan.Line> lines = new ArrayList<>();
    for (AlgoSecurity member : members) {
      Security security = member.getSecurity();
      if (security == null) {
        continue;
      }
      double targetPercentage = bucketTargetPercentage * requiredWeight(member.getPercentage(), security.getName())
          / 100.0;
      lines.add(line(StrategyHelper.SECURITY_LEVEL_LETTER, member.getIdAlgoAssetclassSecurity(),
          bucket.getIdAlgoAssetclassSecurity(), security.getIdSecuritycurrency(), security.getName(), tactical,
          targetPercentage, actualOf(member, exposure), equity, tolerance, breach, actualOf(member, signed) < 0,
          unitValue.of(security, valuationDate)));
    }
    return lines;
  }

  private RebalancingPlan.Line topLine(AlgoTop algoTop, double topPercentage, double equity, double gross,
      boolean breach) {
    double actualPercentage = equity == 0 ? 0 : gross / equity * 100.0;
    return new RebalancingPlan.Line(StrategyHelper.TOP_LEVEL_LETTER, algoTop.getIdAlgoAssetclassSecurity(), null, null,
        algoTop.getName(), false, topPercentage, actualPercentage, actualPercentage - topPercentage,
        equity * topPercentage / 100.0, gross,
        breach ? AlgoRecommendationAction.REBALANCE_SELL : AlgoRecommendationAction.REBALANCE_HOLD, null, null,
        breach ? REASON_EXPOSURE_BREACH : REASON_WITHIN_TOLERANCE);
  }

  /**
   * Turns one target and one actual exposure into a line. This is the single place where the direction of the trade,
   * the blocking rules and the units are decided, so that a bucket line and an instrument line cannot disagree.
   */
  private RebalancingPlan.Line line(String levelType, Integer idNode, Integer idParent, Integer idSecuritycurrency,
      String label, boolean tactical, double targetPercentage, double actual, double equity, double tolerance,
      boolean breach, boolean shortPosition, Double unitValue) {
    double targetAmount = equity * targetPercentage / 100.0;
    double actualPercentage = equity == 0 ? 0 : actual / equity * 100.0;
    double deviation = actualPercentage - targetPercentage;
    double difference = actual - targetAmount;
    if (Math.abs(deviation) <= tolerance) {
      return new RebalancingPlan.Line(levelType, idNode, idParent, idSecuritycurrency, label, tactical,
          targetPercentage, actualPercentage, deviation, targetAmount, actual, AlgoRecommendationAction.REBALANCE_HOLD,
          null, null, REASON_WITHIN_TOLERANCE);
    }
    boolean reducing = difference > 0;
    if (!reducing && tactical) {
      return new RebalancingPlan.Line(levelType, idNode, idParent, idSecuritycurrency, label, tactical,
          targetPercentage, actualPercentage, deviation, targetAmount, actual, AlgoRecommendationAction.REBALANCE_HOLD,
          null, null, REASON_TACTICAL_CEILING);
    }
    if (!reducing && breach) {
      return new RebalancingPlan.Line(levelType, idNode, idParent, idSecuritycurrency, label, tactical,
          targetPercentage, actualPercentage, deviation, targetAmount, actual,
          AlgoRecommendationAction.REBALANCE_BLOCKED, null, null,
          equity <= 0 ? REASON_NON_POSITIVE_EQUITY : REASON_EXPOSURE_BREACH);
    }
    double amount = Math.abs(difference);
    Double units = unitValue == null || unitValue <= 0 ? null : amount / unitValue;
    // The action is the trade, not the exposure. Reducing a long position sells; reducing a short one buys it back,
    // and a report that told the user to sell more of what is already too short would be exactly wrong.
    boolean sell = reducing != shortPosition;
    return new RebalancingPlan.Line(levelType, idNode, idParent, idSecuritycurrency, label, tactical, targetPercentage,
        actualPercentage, deviation, targetAmount, actual,
        sell ? AlgoRecommendationAction.REBALANCE_SELL : AlgoRecommendationAction.REBALANCE_BUY, amount, units,
        units == null && idSecuritycurrency != null ? REASON_NO_PRICE
            : reducing ? REASON_ABOVE_TARGET : REASON_BELOW_TARGET);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Snapshot interpretation
  // ---------------------------------------------------------------------------------------------------------------

  private static double fx(Snapshot snapshot, Position position) {
    return snapshot.fx().getOrDefault(position.security().getCurrency(), 0.0);
  }

  /**
   * Signed position value per instrument in tenant currency. Only its sign is used, to tell a long position from a
   * short one: the size of the difference comes from the gross exposure, the direction of the trade from here.
   */
  private static Map<Integer, Double> signedValueBySecurity(Snapshot snapshot) {
    Map<Integer, Double> signed = new HashMap<>();
    for (Position position : snapshot.positions()) {
      signed.merge(position.security().getIdSecuritycurrency(), position.closingValue() * fx(snapshot, position),
          Double::sum);
    }
    return signed;
  }

  /** Gross exposure per instrument in tenant currency: the quantity the budget of 2.1 constrains. */
  private static Map<Integer, Double> exposureBySecurity(Snapshot snapshot) {
    Map<Integer, Double> exposure = new HashMap<>();
    for (Position position : snapshot.positions()) {
      exposure.merge(position.security().getIdSecuritycurrency(), position.grossExposure() * fx(snapshot, position),
          Double::sum);
    }
    return exposure;
  }

  /**
   * Exposure of one unit in tenant currency, taken from the position itself where there is one. A position knows its
   * own contract size, which a raw closing price does not, so this is the only figure a margin instrument can be sized
   * with; an instrument that is not held falls back to its closing price further down.
   */
  private static Map<Integer, Double> unitValueBySecurity(Snapshot snapshot) {
    Map<Integer, Double> perUnit = new LinkedHashMap<>();
    for (Position position : snapshot.positions()) {
      if (position.units() != 0) {
        perUnit.putIfAbsent(position.security().getIdSecuritycurrency(),
            Math.abs(position.grossExposure() / position.units()) * fx(snapshot, position));
      }
    }
    return perUnit;
  }

  /**
   * Exposure of one unit in tenant currency, for an instrument that may or may not be held.
   *
   * <p>
   * A held instrument answers from its own position, because only the position knows the contract size of a margin
   * product; the closing price of a future says nothing about what one contract is worth. An instrument that is not
   * held yet - the ordinary case of a target that has to be bought for the first time - is priced with its closing
   * quote, which is the correct per-unit cost of acquiring it. Anything else, including an instrument whose currency
   * the tenant does not hold and therefore has no rate for, yields no units rather than an invented quantity.
   * </p>
   */
  private final class UnitValues {
    private final Snapshot snapshot;
    private final Map<Integer, Double> held;
    private final AlgoHistoricalValuationService.ClosingPrices market;

    private UnitValues(Snapshot snapshot, Map<Integer, Double> held,
        AlgoHistoricalValuationService.ClosingPrices market) {
      this.market = market;
      this.snapshot = snapshot;
      this.held = held;
    }

    private Double of(Security security, LocalDate valuationDate) {
      Double fromPosition = held.get(security.getIdSecuritycurrency());
      if (fromPosition != null) {
        return fromPosition;
      }
      if (security.isMarginInstrument()) {
        return null;
      }
      Double rate = snapshot.fx().get(security.getCurrency());
      Double close = rate == null ? null : market.close(security.getIdSecuritycurrency(), valuationDate);
      return close == null || close <= 0 ? null : close * rate;
    }
  }

  private static double actualOf(AlgoSecurity member, Map<Integer, Double> exposure) {
    Security security = member.getSecurity();
    return security == null ? 0 : exposure.getOrDefault(security.getIdSecuritycurrency(), 0.0);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Hierarchy
  // ---------------------------------------------------------------------------------------------------------------

  private List<AlgoAssetclass> sortedBuckets(AlgoTop algoTop, Locale locale) {
    List<AlgoAssetclass> buckets = new ArrayList<>(algoAssetclassJpaRepository
        .findByIdTenantAndIdAlgoAssetclassParent(algoTop.getIdTenant(), algoTop.getIdAlgoAssetclassSecurity()));
    buckets.sort(Comparator.comparing(b -> label(b, locale)));
    return buckets;
  }

  /**
   * A bucket is tactical when something other than the rebalancing itself decides when its positions are opened. Its
   * allocated amount is then a ceiling: filling it here would open a position the entry strategy has not asked for, and
   * the two would then fight over the same budget.
   */
  private boolean isTactical(AlgoAssetclass bucket, List<AlgoSecurity> members, Integer idOwnerTenant) {
    if (carriesEntryStrategy(bucket.getIdAlgoAssetclassSecurity(), idOwnerTenant)) {
      return true;
    }
    return members.stream().anyMatch(m -> carriesEntryStrategy(m.getIdAlgoAssetclassSecurity(), idOwnerTenant));
  }

  private boolean carriesEntryStrategy(Integer idNode, Integer idOwnerTenant) {
    return algoStrategyJpaRepository.findByIdAlgoAssetclassSecurityAndIdTenant(idNode, idOwnerTenant).stream()
        .anyMatch(s -> s.getAlgoStrategyImplementations() != AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING
            && !AlgoAlertEvaluationCoordinator.isAlertType(s.getAlgoStrategyImplementations()));
  }

  /** Bucket label: the free category name when the user gave one, otherwise the asset class triple it stands for. */
  private String label(AlgoAssetclass bucket, Locale locale) {
    if (bucket.getName() != null && !bucket.getName().isBlank()) {
      return bucket.getName();
    }
    if (bucket.getAssetclass() == null) {
      return String.valueOf(bucket.getIdAlgoAssetclassSecurity());
    }
    var assetclass = bucket.getAssetclass();
    return translate(assetclass.getCategoryType().name(), locale) + ", "
        + translate(assetclass.getSpecialInvestmentInstrument().name(), locale);
  }

  private String translate(String key, Locale locale) {
    return messageSource.getMessage(key, null, key, locale);
  }

  private static double requiredWeight(Float percentage, String node) {
    if (percentage == null || percentage < 0) {
      throw AlgoHistoricalValuationService.invalid("algo.rebalancing.weight.missing", node);
    }
    return percentage;
  }

  /**
   * Sibling weights describe how one budget is divided, so anything but 100% leaves part of it unexplained and the
   * targets below would silently not add up to the target above.
   */
  private static void requireWeightsComplete(List<Float> percentages, String parent) {
    if (percentages.isEmpty()) {
      return;
    }
    double sum = percentages.stream().mapToDouble(p -> p == null ? 0 : p).sum();
    if (Math.abs(sum - 100.0) > WEIGHT_EPSILON) {
      throw AlgoHistoricalValuationService.invalid("algo.rebalancing.weights.incomplete",
          parent + ": " + DataBusinessHelper.roundPercentage(sum));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Live evaluation
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Whether any AlgoTop still owes an evaluation today. Deliberately cheap: it reads the run day of the stored plans
   * and values nothing, because it runs on the five minute scan that only decides whether to enqueue the real work.
   *
   * @return true when at least one activatable AlgoTop with a rebalancing strategy has no plan of today
   */
  @Transactional(readOnly = true)
  public boolean hasDueRebalancing() {
    return features.isAlgo() && algoTopJpaRepository.findAll().stream().anyMatch(this::isDueToday);
  }

  /** Evaluates every tenant. This is the background pass. */
  public void evaluateAll() {
    if (features.isAlgo()) {
      algoTopJpaRepository.findAll().forEach(algoTop -> evaluateOne(algoTop.getIdTenant(), algoTop, true));
    }
  }

  /**
   * Evaluates the AlgoTops of one tenant, whether or not a plan of today already exists. This is what a user gets when
   * asking for an evaluation by hand, where waiting for tomorrow would be an odd answer.
   *
   * @param idTenant the tenant to evaluate
   */
  public void evaluateForTenant(Integer idTenant) {
    if (features.isAlgo()) {
      algoTopJpaRepository.findByIdTenantOrderByName(idTenant)
          .forEach(algoTop -> evaluateOne(idTenant, algoTop, false));
    }
  }

  private boolean isDueToday(AlgoTop algoTop) {
    if (!algoTop.isActivatable()) {
      return false;
    }
    boolean configured = algoStrategyJpaRepository
        .findByIdAlgoAssetclassSecurityAndIdTenant(algoTop.getIdAlgoAssetclassSecurity(), algoTop.getIdTenant())
        .stream()
        .anyMatch(s -> s.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING
            && s.isActivatable());
    if (!configured) {
      return false;
    }
    return algoRecommendationJpaRepository.findLastRunDate(algoTop.getIdTenant(), algoTop.getIdAlgoAssetclassSecurity())
        .filter(LocalDate.now()::equals).isEmpty();
  }

  /**
   * One AlgoTop: build the plan, replace the stored one and notify when it asks for something. A configuration that
   * cannot be evaluated - no rebalancing strategy, weights that do not add up, a day that cannot be valued - is not an
   * error of the run: the other hierarchies are still evaluated, and the user sees the reason when opening the report.
   */
  private void evaluateOne(Integer idTenant, AlgoTop algoTop, boolean onlyWhenDue) {
    if (onlyWhenDue && !isDueToday(algoTop)) {
      return;
    }
    RebalancingPlan plan;
    try {
      plan = plan(idTenant, algoTop, lastCompletedDay(), Locale.ROOT);
    } catch (DataViolationException e) {
      // A hierarchy that cannot be compared is not a failure of the run: the other hierarchies are still evaluated,
      // and the user is told the reason when opening the report. It is logged so that a configuration nobody looks at
      // does not stay quietly unevaluated forever.
      log.info("Rebalancing of AlgoTop {} was skipped: {}", algoTop.getIdAlgoAssetclassSecurity(), e.getMessage());
      return;
    }
    // The plan is replaced every day so the allocation report stays current; only the signal waits for a checkpoint.
    persist(plan);
    if (plan.trigger() != AlgoRebalancingTrigger.NONE) {
      notifyActionable(plan, algoTop);
    }
  }

  /**
   * Replaces the previous plan of this AlgoTop, so the table holds one plan rather than the union of two.
   *
   * <p>
   * The plan is replaced on every daily evaluation, so the start of the periodic interval cannot be read off the run
   * day of the stored rows. A plan that is a checkpoint records its valuation day, traded or not; every plan in between
   * carries the last checkpoint over from the rows it replaces. Without this the interval restarted each day and never
   * elapsed again after the first evaluation.
   * </p>
   *
   * @param plan the plan to store
   */
  public void persist(RebalancingPlan plan) {
    LocalDate checkpoint = plan.periodicDue() ? plan.valuationDate()
        : algoRecommendationJpaRepository.findLastCheckpointDate(plan.idTenant(), plan.idAlgoTop()).orElse(null);
    List<AlgoRecommendation> rows = plan.lines().stream().map(line -> toEntity(plan, line, checkpoint)).toList();
    algoRecommendationWriter.replace(plan.idTenant(), plan.idAlgoTop(), rows);
  }

  private AlgoRecommendation toEntity(RebalancingPlan plan, RebalancingPlan.Line line, LocalDate checkpoint) {
    AlgoRecommendation row = new AlgoRecommendation();
    row.setIdTenant(plan.idTenant());
    row.setIdAlgoTop(plan.idAlgoTop());
    row.setIdAlgoStrategy(plan.idAlgoStrategy());
    row.setLevelType(line.levelType());
    row.setIdNode(line.idNode());
    row.setIdSecuritycurrency(line.idSecuritycurrency());
    row.setRunDate(LocalDate.now());
    row.setCheckpointDate(checkpoint);
    row.setValuationDate(plan.valuationDate());
    row.setCurrency(plan.currency());
    row.setTargetPercentage(line.targetPercentage());
    row.setActualPercentage(line.actualPercentage());
    row.setDeviationPercentage(line.deviation());
    row.setTargetAmount(line.targetAmount());
    row.setActualAmount(line.actualAmount());
    row.setRecommendedAction(line.action());
    row.setRecommendedAmount(line.recommendedAmount());
    row.setRecommendedUnits(line.recommendedUnits());
    row.setTriggerKind(plan.trigger());
    row.setRationale(line.rationale());
    return row;
  }

  /**
   * Raises one signal per instrument the user can act on, reductions first. The order matters because executing an
   * increase before the matching reduction can breach the gross ceiling on the way, even though the end state does not.
   */
  private void notifyActionable(RebalancingPlan plan, AlgoTop algoTop) {
    AlgoStrategy strategy = requireRebalancingStrategy(algoTop);
    LocalDate today = LocalDate.now();
    Map<Integer, Security> securities = securitiesOf(algoTop);
    plan.lines().stream().filter(line -> line.actionable() && line.idSecuritycurrency() != null)
        .sorted(Comparator.comparing((RebalancingPlan.Line line) -> !line.reducesExposure())).forEach(line -> {
          Security security = securities.get(line.idSecuritycurrency());
          if (security != null) {
            algoAlarmRecorder.record(new AlgoAlertScope(plan.idTenant(), strategy, security, algoTop.getName(), true),
                AlgoSignalKind.REBALANCE_DRIFT, (byte) (line.reducesExposure() ? 1 : -1), details(plan, line), today);
          }
        });
  }

  private Map<Integer, Security> securitiesOf(AlgoTop algoTop) {
    Map<Integer, Security> securities = new HashMap<>();
    for (AlgoAssetclass bucket : algoAssetclassJpaRepository
        .findByIdTenantAndIdAlgoAssetclassParent(algoTop.getIdTenant(), algoTop.getIdAlgoAssetclassSecurity())) {
      algoSecurityJpaRepository
          .findByIdAlgoSecurityParentAndIdTenant(bucket.getIdAlgoAssetclassSecurity(), algoTop.getIdTenant()).stream()
          .filter(m -> m.getSecurity() != null)
          .forEach(m -> securities.put(m.getSecurity().getIdSecuritycurrency(), m.getSecurity()));
    }
    return securities;
  }

  /** Locale independent, like every other signal detail: the delivery renders the text in the reader's language. */
  private static String details(RebalancingPlan plan, RebalancingPlan.Line line) {
    return String.format(Locale.ROOT,
        "{\"trigger\":\"%s\",\"action\":\"%s\",\"target\":%.2f,\"actual\":%.2f,\"deviation\":%.2f,"
            + "\"amount\":%.2f,\"currency\":\"%s\",\"reason\":\"%s\"}",
        plan.trigger(), line.action(), line.targetPercentage(), line.actualPercentage(), line.deviation(),
        Optional.ofNullable(line.recommendedAmount()).orElse(0.0), plan.currency(), line.rationale());
  }

  private boolean isCheckpointDue(Integer idTenant, AlgoTop algoTop, RebalancingTop config, LocalDate valuationDate,
      LocalDate lastRebalancedOn) {
    Optional<LocalDate> last = lastRebalancedOn != null ? Optional.of(lastRebalancedOn)
        : algoRecommendationJpaRepository.findLastCheckpointDate(idTenant, algoTop.getIdAlgoAssetclassSecurity());
    if (last.isEmpty()) {
      return true;
    }
    return !valuationDate.isBefore(last.get().plusDays(checkpointInterval(config)));
  }

}
