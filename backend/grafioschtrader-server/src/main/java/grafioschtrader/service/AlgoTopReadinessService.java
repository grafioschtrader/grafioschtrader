package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.AlgoTopReadiness;
import grafioschtrader.dto.AlgoTopReadiness.Issue;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;

/**
 * Decides whether a rule-based strategy can be used as it stands: for a simulation environment and its replay, and for
 * the rebalancing comparison.
 *
 * <p>
 * The result is derived on every read and never stored. A strategy is assembled one node at a time, so its weightings
 * legitimately fail to add up while it is being built, and several findings change without the strategy being saved at
 * all - an instrument's active period ends, the linked watchlist loses a security. A persisted flag would be stale in
 * exactly those cases.
 * </p>
 *
 * <p>
 * Only what the engines themselves refuse is blocking: the weighting rules of {@code AlgoRebalancingService} and
 * {@code AlgoExposureBudget}, the rebalancing configuration, the security bands, and the preconditions of an active Mean
 * Reversion Dip. What the engines tolerate - an unsuitable instrument, which a replay excludes and redistributes, or an
 * asset class without a tradable security, which simply stays uninvested - is reported without blocking. Findings that
 * depend on a date, an environment or price data are not decided here; the gate that knows that context still refuses
 * them.
 * </p>
 */
@Service
public class AlgoTopReadinessService {

  public static final String TOP_WEIGHT_INVALID = "algo.readiness.top.weight.invalid";
  public static final String WEIGHTS_INCOMPLETE = "algo.readiness.weights.incomplete";
  public static final String WEIGHT_INVALID = "algo.readiness.weight.invalid";
  public static final String REBALANCING_MISSING = "algo.readiness.rebalancing.missing";
  public static final String REBALANCING_CONFIG = "algo.readiness.rebalancing.config";
  public static final String BAND_INVALID = "algo.readiness.band.invalid";
  public static final String MEAN_REVERSION_CONFIG = "algo.readiness.mean.reversion.config";
  public static final String MEAN_REVERSION_WATCHLIST = "algo.readiness.mean.reversion.watchlist";
  public static final String SECURITY_AMBIGUOUS = "algo.readiness.security.ambiguous";
  public static final String ASSETCLASS_NO_INSTRUMENT = "algo.readiness.assetclass.no.instrument";
  public static final String INSTRUMENT_UNSUITABLE = "algo.readiness.instrument.unsuitable";

  private static final String FIELD_ADDED_PERCENTAGE = "addedPercentage";
  private static final String FIELD_PERCENTAGE = "percentage";
  private static final String FIELD_NAME = "name";

  private final AlgoAssetclassJpaRepository assetclasses;
  private final AlgoStrategyJpaRepository strategies;
  private final WatchlistJpaRepository watchlists;
  private final MessageSource messageSource;

  public AlgoTopReadinessService(AlgoAssetclassJpaRepository assetclasses, AlgoStrategyJpaRepository strategies,
      WatchlistJpaRepository watchlists, MessageSource messageSource) {
    this.assetclasses = assetclasses;
    this.strategies = strategies;
    this.watchlists = watchlists;
    this.messageSource = messageSource;
  }

  /**
   * Evaluates the strategy and attaches the result to it, so that it reaches the client with the entity.
   *
   * @param top    the strategy, owned by the tenant whose hierarchy it is
   * @param locale language of the messages
   * @return the attached result
   */
  @Transactional(readOnly = true)
  public AlgoTopReadiness attach(AlgoTop top, Locale locale) {
    top.readiness = check(top, assetclasses.findByIdTenantAndIdAlgoAssetclassParent(top.getIdTenant(), top.getId()),
        locale);
    return top.readiness;
  }

  /**
   * Refuses a strategy that a simulation environment or a replay cannot be based on.
   *
   * @throws DataViolationException naming the first blocking finding
   */
  @Transactional(readOnly = true)
  public void requireReadyForReplay(AlgoTop top, Locale locale) {
    AlgoTopReadiness readiness = attach(top, locale);
    if (!readiness.readyForReplay()) {
      throw refusal(readiness);
    }
  }

  /**
   * Refuses a strategy whose rebalancing comparison cannot be computed.
   *
   * @throws DataViolationException naming the first blocking finding, or the missing portfolio rebalance
   */
  @Transactional(readOnly = true)
  public void requireReadyForRebalancing(AlgoTop top, Locale locale) {
    AlgoTopReadiness readiness = attach(top, locale);
    if (!readiness.readyForRebalancing()) {
      throw refusal(readiness);
    }
  }

  /**
   * Evaluates a strategy whose asset classes the caller already loaded, with their securities.
   *
   * @param top     the strategy
   * @param buckets its asset classes
   * @param locale  language of the messages
   * @return findings, blocking ones first
   */
  public AlgoTopReadiness check(AlgoTop top, List<AlgoAssetclass> buckets, Locale locale) {
    List<Issue> issues = new ArrayList<>();
    Integer idTop = top.getIdAlgoAssetclassSecurity();
    if (!validWeight(top.getPercentage())) {
      issues.add(issue(TOP_WEIGHT_INVALID, idTop, FIELD_PERCENTAGE, true, locale, top.getName(),
          String.valueOf(top.getPercentage())));
    }
    checkSum(issues, idTop, top.getName(), buckets.stream().map(AlgoAssetclass::getPercentage).toList(), true,
        locale);

    Map<Integer, List<AlgoSecurity>> membersByBucket = new HashMap<>();
    Set<Integer> nodeIds = new HashSet<>(Set.of(idTop));
    for (AlgoAssetclass bucket : buckets) {
      List<AlgoSecurity> members = bucket.getAlgoSecurityList() == null ? List.of() : bucket.getAlgoSecurityList();
      membersByBucket.put(bucket.getIdAlgoAssetclassSecurity(), members);
      nodeIds.add(bucket.getIdAlgoAssetclassSecurity());
      members.forEach(member -> nodeIds.add(member.getIdAlgoAssetclassSecurity()));
      checkBucket(issues, top, bucket, members, locale);
    }
    Map<Integer, List<AlgoStrategy>> strategiesByNode = strategies
        .findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(top.getIdTenant(), nodeIds).stream()
        .collect(Collectors.groupingBy(AlgoStrategy::getIdAlgoAssetclassSecurity));
    boolean rebalancing = checkRebalancing(issues, top, strategiesByNode.getOrDefault(idTop, List.of()), locale);
    checkMeanReversion(issues, top, buckets, membersByBucket, strategiesByNode, locale);

    issues.sort(Comparator.comparing(Issue::blocking).reversed());
    boolean readyForReplay = issues.stream().noneMatch(Issue::blocking);
    return new AlgoTopReadiness(readyForReplay, readyForReplay && rebalancing, List.copyOf(issues));
  }

  private void checkBucket(List<Issue> issues, AlgoTop top, AlgoAssetclass bucket, List<AlgoSecurity> members,
      Locale locale) {
    Integer idBucket = bucket.getIdAlgoAssetclassSecurity();
    String label = AlgoRebalancingService.label(bucket, locale, messageSource);
    if (!validWeight(bucket.getPercentage())) {
      issues.add(issue(WEIGHT_INVALID, idBucket, FIELD_PERCENTAGE, true, locale, label,
          String.valueOf(bucket.getPercentage())));
    }
    // The engines skip an empty asset class, so its missing total is shown but does not block.
    checkSum(issues, idBucket, label, members.stream().map(AlgoSecurity::getPercentage).toList(), !members.isEmpty(),
        locale);
    Double band = bucket.getSecurityDeviationPercentage();
    Integer limit = bucket.getMaxTradedSecuritiesPerAssetclass();
    if (band != null && !validBand(band) || limit != null && limit < 1) {
      issues.add(issue(BAND_INVALID, idBucket, null, true, locale, label, band + " / " + limit));
    }
    LocalDate openingDate = top.getReferenceDate() == null ? null : top.getReferenceDate().plusDays(1);
    boolean hasValidInstrument = false;
    for (AlgoSecurity member : members) {
      String memberLabel = member.getSecurity() == null ? String.valueOf(member.getIdAlgoAssetclassSecurity())
          : member.getSecurity().getName();
      if (!validWeight(member.getPercentage())) {
        issues.add(issue(WEIGHT_INVALID, member.getIdAlgoAssetclassSecurity(), FIELD_PERCENTAGE, true, locale,
            memberLabel, String.valueOf(member.getPercentage())));
      }
      boolean eligible = AlgoSecurityEligibility.isEligibleInstrument(member.getSecurity(), openingDate);
      if (!eligible) {
        issues.add(issue(INSTRUMENT_UNSUITABLE, member.getIdAlgoAssetclassSecurity(), FIELD_NAME, false, locale,
            memberLabel, ""));
      }
      hasValidInstrument |= eligible && weight(member.getPercentage()) > 0;
    }
    if (weight(bucket.getPercentage()) > 0 && !hasValidInstrument) {
      issues.add(issue(ASSETCLASS_NO_INSTRUMENT, idBucket, FIELD_NAME, false, locale, label, ""));
    }
  }

  /** @return whether a portfolio rebalance exists on the top level, complete or not */
  private boolean checkRebalancing(List<Issue> issues, AlgoTop top, List<AlgoStrategy> topStrategies,
      Locale locale) {
    AlgoStrategy strategy = topStrategies.stream()
        .filter(s -> s.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING)
        .findFirst().orElse(null);
    Integer idTop = top.getIdAlgoAssetclassSecurity();
    if (strategy == null) {
      issues.add(issue(REBALANCING_MISSING, idTop, null, false, locale, top.getName(), ""));
      return false;
    }
    RebalancingTop config;
    try {
      config = AlertConfigAdapter.read(strategy, RebalancingTop.class);
    } catch (RuntimeException e) {
      config = null;
    }
    if (config == null || config.getThresholdPercentage() == null || config.getTimePeriodPerYear() == null
        || config.getSecurityDeviationPercentage() == null
        || !Double.isFinite(config.getSecurityDeviationPercentage())
        || config.getMaxTradedSecuritiesPerAssetclass() == null) {
      issues.add(issue(REBALANCING_CONFIG, idTop, null, true, locale, top.getName(), ""));
    } else if (!validBand(config.getSecurityDeviationPercentage()) || config.getMaxTradedSecuritiesPerAssetclass() < 1) {
      issues.add(issue(BAND_INVALID, idTop, null, true, locale, top.getName(),
          config.getSecurityDeviationPercentage() + " / " + config.getMaxTradedSecuritiesPerAssetclass()));
    }
    return true;
  }

  /**
   * An active Mean Reversion Dip refuses a security outside the linked watchlist, a security held by two asset classes
   * and a configuration that is not executable. A security held twice without such a strategy is only reported,
   * because nothing is decided for it that could be ambiguous.
   */
  private void checkMeanReversion(List<Issue> issues, AlgoTop top, List<AlgoAssetclass> buckets,
      Map<Integer, List<AlgoSecurity>> membersByBucket, Map<Integer, List<AlgoStrategy>> strategiesByNode,
      Locale locale) {
    Map<Integer, List<AlgoSecurity>> bySecurity = new HashMap<>();
    Set<Integer> meanReversionSecurities = new HashSet<>();
    Set<Integer> watchlist = null;
    for (AlgoAssetclass bucket : buckets) {
      for (AlgoSecurity member : membersByBucket.get(bucket.getIdAlgoAssetclassSecurity())) {
        if (member.getSecurity() == null) {
          continue;
        }
        Integer idSecurity = member.getSecurity().getIdSecuritycurrency();
        bySecurity.computeIfAbsent(idSecurity, _ -> new ArrayList<>()).add(member);
        List<AlgoStrategy> active = strategiesByNode.getOrDefault(member.getIdAlgoAssetclassSecurity(), List.of())
            .stream()
            .filter(s -> s.isActivatable()
                && s.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP)
            .toList();
        if (active.isEmpty()) {
          continue;
        }
        meanReversionSecurities.add(idSecurity);
        String label = member.getSecurity().getName();
        for (AlgoStrategy strategy : active) {
          try {
            StrategyConfigValidator.executable(strategy.getStrategyConfig());
          } catch (RuntimeException e) {
            issues.add(issue(MEAN_REVERSION_CONFIG, member.getIdAlgoAssetclassSecurity(), null, true, locale, label,
                e.getMessage() == null ? "" : e.getMessage()));
          }
        }
        if (watchlist == null) {
          watchlist = top.getIdWatchlist() == null ? Set.of()
              : watchlists.securitiesOfWatchlist(top.getIdWatchlist()).stream()
                  .map(s -> s.getIdSecuritycurrency()).collect(Collectors.toSet());
        }
        if (!watchlist.contains(idSecurity)) {
          issues.add(issue(MEAN_REVERSION_WATCHLIST, member.getIdAlgoAssetclassSecurity(), FIELD_NAME, true, locale,
              label, ""));
        }
      }
    }
    bySecurity.forEach((idSecurity, members) -> {
      if (members.size() > 1) {
        AlgoSecurity first = members.getFirst();
        issues.add(issue(SECURITY_AMBIGUOUS, first.getIdAlgoAssetclassSecurity(), FIELD_NAME,
            meanReversionSecurities.contains(idSecurity), locale, first.getSecurity().getName(), ""));
      }
    });
  }

  private void checkSum(List<Issue> issues, Integer idNode, String label, List<Float> weights, boolean blocking,
      Locale locale) {
    double sum = weights.stream().mapToDouble(AlgoTopReadinessService::weight).sum();
    if (!Double.isFinite(sum)
        || Math.abs(sum - GlobalConstants.EXPECTED_ADDED_PERCENTAGE) >= GlobalConstants.ADDED_PERCENTAGE_TOLERANCE) {
      issues.add(issue(WEIGHTS_INCOMPLETE, idNode, FIELD_ADDED_PERCENTAGE, blocking, locale, label,
          String.valueOf(DataBusinessHelper.roundPercentage(sum))));
    }
  }

  /** @return the language of the requesting user, or the root locale for background work */
  public static Locale currentLocale() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof User user && user.getLocaleStr() != null
        && !user.getLocaleStr().isBlank()) {
      return user.createAndGetJavaLocale();
    }
    return Locale.ROOT;
  }

  private Issue issue(String code, Integer idNode, String field, boolean blocking, Locale locale, String label,
      String detail) {
    Object[] args = { label, detail };
    return new Issue(code, idNode, field, args, messageSource.getMessage(code, args, code, locale), blocking);
  }

  private static DataViolationException refusal(AlgoTopReadiness readiness) {
    Issue first = readiness.issues().stream().filter(Issue::blocking).findFirst()
        .orElseGet(() -> readiness.issues().stream().filter(i -> REBALANCING_MISSING.equals(i.code())).findFirst()
            .orElseThrow());
    return new DataViolationException("id.algo.top", first.code(), first.args());
  }

  private static boolean validWeight(Float weight) {
    return weight != null && Float.isFinite(weight) && weight >= 0 && weight <= 100;
  }

  private static boolean validBand(double band) {
    return Double.isFinite(band) && band >= 0 && band <= 100;
  }

  private static double weight(Float weight) {
    return weight == null ? 0 : weight;
  }
}
