package grafioschtrader.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;

/**
 * Works out which alert is evaluated against which instrument.
 *
 * <p>
 * The evaluation used to ask only for the strategies hanging directly on the AlgoTop node and apply each of them to
 * every instrument of the linked watchlist. An alert configured on an asset class bucket or on a single instrument -
 * which is what the tree lets a user create, and what the alert overview shows - was therefore never evaluated at all,
 * while a top level alert was evaluated against instruments it was never meant for. This resolver walks the three
 * levels instead:
 * </p>
 *
 * <ul>
 * <li>a strategy on an {@link AlgoSecurity} node applies to that node's instrument;</li>
 * <li>a strategy on an {@link AlgoAssetclass} bucket applies to the instruments of the bucket's own security
 * children;</li>
 * <li>a strategy on the {@link AlgoTop} applies to every instrument of the linked watchlist.</li>
 * </ul>
 *
 * <p>
 * Every pair is resolved once, even when the same instrument is reachable through more than one path, so an instrument
 * that sits in a bucket and in the watchlist cannot be alarmed twice by one strategy.
 * </p>
 *
 * <p>
 * Deactivation is reported rather than filtered. A pair whose strategy is switched off, and in the live scopes also a
 * pair of a hierarchy not assigned to monitoring or with its alert disabled, comes back with {@code active == false},
 * because the caller has to discard its crossing baselines: dropping the pair
 * silently would leave a stale baseline behind, and switching the alert on again would then report the move that
 * happened while it was off.
 * </p>
 */
@Service
public class AlgoAlertScopeResolver {

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private AlgoMonitoringService monitoring;

  /** Live-only preference filter; resolveForAlgoTop deliberately remains independent for historical replay. */
  private List<AlgoAlertScope> liveScopes(AlgoTop top) {
    boolean assigned = monitoring.isAssigned(top.getIdTenant(), top.getId());
    return resolveForAlgoTop(top).stream().map(s -> new AlgoAlertScope(s.idTenant(), s.strategy(), s.security(),
        s.contextName(), s.active() && assigned && s.strategy().isAlertEnabled())).toList();
  }

  /**
   * All pairs of every AlgoTop hierarchy plus every standalone alert, of every tenant. This is the system wide scope of
   * the scheduled evaluation.
   *
   * @return the pairs, active and inactive
   */
  public List<AlgoAlertScope> resolveAll() {
    List<AlgoAlertScope> scopes = new ArrayList<>();
    for (AlgoTop algoTop : algoTopJpaRepository.findAll()) {
      scopes.addAll(liveScopes(algoTop));
    }
    scopes.addAll(resolveStandalone(null));
    return scopes;
  }

  /**
   * All pairs of one tenant. This is what a manually triggered evaluation is limited to, so that a user cannot
   * evaluate, and be told about, another tenant's alerts.
   *
   * @param idTenant the tenant whose configuration is resolved
   * @return the pairs of that tenant, active and inactive
   */
  public List<AlgoAlertScope> resolveForTenant(Integer idTenant) {
    List<AlgoAlertScope> scopes = new ArrayList<>();
    for (AlgoTop algoTop : algoTopJpaRepository.findByIdTenantOrderByName(idTenant)) {
      scopes.addAll(liveScopes(algoTop));
    }
    scopes.addAll(resolveStandalone(idTenant));
    return scopes;
  }

  /**
   * The pairs of one AlgoTop hierarchy: its own strategies against the linked watchlist, each bucket's strategies
   * against that bucket's instruments, and each instrument node's strategies against itself.
   *
   * @param algoTop the hierarchy to walk
   * @return the pairs below that AlgoTop, active and inactive
   */
  public List<AlgoAlertScope> resolveForAlgoTop(AlgoTop algoTop) {
    Integer idTenant = algoTop.getIdTenant();
    String contextName = algoTop.getName();
    // A LinkedHashMap keyed by strategy and instrument, because the same instrument can be reached both as a security
    // child of a bucket and as a member of the watchlist. Insertion order keeps the evaluation reproducible.
    Map<String, AlgoAlertScope> byPair = new LinkedHashMap<>();

    Hierarchy hierarchy = readHierarchy(algoTop);
    Map<Integer, List<AlgoStrategy>> strategiesByNode = hierarchy.strategiesByNode();

    List<Security> watchlistSecurities = securitiesOfWatchlist(algoTop.getIdWatchlist());
    for (AlgoStrategy strategy : strategiesByNode.getOrDefault(algoTop.getId(), List.of())) {
      boolean active = strategy.isActivatable();
      for (Security security : watchlistSecurities) {
        put(byPair, idTenant, strategy, security, contextName, active);
      }
    }

    for (AlgoAssetclass bucket : hierarchy.buckets()) {
      List<AlgoSecurity> members = hierarchy.membersByBucket().get(bucket.getId());

      for (AlgoStrategy strategy : strategiesByNode.getOrDefault(bucket.getId(), List.of())) {
        boolean active = strategy.isActivatable();
        for (AlgoSecurity member : members) {
          if (member.getSecurity() != null) {
            put(byPair, idTenant, strategy, member.getSecurity(), contextName, active);
          }
        }
      }

      for (AlgoSecurity member : members) {
        if (member.getSecurity() == null) {
          continue;
        }
        for (AlgoStrategy strategy : strategiesByNode.getOrDefault(member.getId(), List.of())) {
          boolean active = strategy.isActivatable();
          put(byPair, idTenant, strategy, member.getSecurity(), contextName, active);
        }
      }
    }
    return new ArrayList<>(byPair.values());
  }

  /** Reads configuration afresh, batching strategy reads without caching activation state across calls or tenants. */
  private Hierarchy readHierarchy(AlgoTop algoTop) {
    Integer idTenant = algoTop.getIdTenant();
    List<AlgoAssetclass> buckets = algoAssetclassJpaRepository.findByIdTenantAndIdAlgoAssetclassParent(idTenant,
        algoTop.getId());
    Map<Integer, List<AlgoSecurity>> membersByBucket = new LinkedHashMap<>();
    List<Integer> nodeIds = new ArrayList<>();
    nodeIds.add(algoTop.getId());
    for (AlgoAssetclass bucket : buckets) {
      nodeIds.add(bucket.getId());
      List<AlgoSecurity> members = algoSecurityJpaRepository.findByIdAlgoSecurityParentAndIdTenant(bucket.getId(),
          idTenant);
      membersByBucket.put(bucket.getId(), members);
      members.stream().filter(member -> member.getSecurity() != null).forEach(member -> nodeIds.add(member.getId()));
    }
    Map<Integer, List<AlgoStrategy>> strategiesByNode = algoStrategyJpaRepository
        .findByIdTenantAndIdAlgoAssetclassSecurityInOrderByIdAlgoRuleStrategy(idTenant, nodeIds).stream()
        .collect(Collectors.groupingBy(AlgoStrategy::getIdAlgoAssetclassSecurity));
    return new Hierarchy(buckets, membersByBucket, strategiesByNode);
  }

  private record Hierarchy(List<AlgoAssetclass> buckets, Map<Integer, List<AlgoSecurity>> membersByBucket,
      Map<Integer, List<AlgoStrategy>> strategiesByNode) {
  }

  /**
   * The pairs of the alerts a user added straight from a watchlist or portfolio row. They hang on an
   * {@link AlgoSecurity} without a parent, so they have no AlgoTop above them and no watchlist scope; the instrument
   * itself names the notification. Their live switch is the strategy's alert preference.
   *
   * @param idTenant tenant to restrict to, or null for every tenant
   * @return the standalone pairs, active and inactive
   */
  public List<AlgoAlertScope> resolveStandalone(Integer idTenant) {
    List<AlgoAlertScope> scopes = new ArrayList<>();
    for (AlgoSecurity algoSecurity : algoSecurityJpaRepository.findByIdAlgoSecurityParentIsNull()) {
      if (idTenant != null && !idTenant.equals(algoSecurity.getIdTenant())) {
        continue;
      }
      Security security = algoSecurity.getSecurity();
      if (security == null) {
        continue;
      }
      for (AlgoStrategy strategy : strategiesOf(algoSecurity.getIdAlgoAssetclassSecurity(),
          algoSecurity.getIdTenant())) {
        scopes.add(new AlgoAlertScope(algoSecurity.getIdTenant(), strategy, security, security.getName(),
            strategy.isActivatable() && strategy.isAlertEnabled()));
      }
    }
    return scopes;
  }

  private List<AlgoStrategy> strategiesOf(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoStrategyJpaRepository.findByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

  /**
   * The instruments of the AlgoTop's watchlist.
   *
   * <p>
   * Read with a query rather than by walking {@code Watchlist.securitycurrencyList}. That collection is lazy and this
   * resolver runs outside a transaction on the scheduled path, where the traversal threw
   * {@code LazyInitializationException} and failed every background evaluation. The query also leaves the watchlist's
   * currency pairs in the database: the alert types all measure an instrument price against a holding, a history or an
   * indicator of that instrument, so only securities are in scope.
   * </p>
   *
   * @param idWatchlist the watchlist linked to the AlgoTop, may be null
   * @return its securities, empty when no watchlist is linked
   */
  private List<Security> securitiesOfWatchlist(Integer idWatchlist) {
    return idWatchlist == null ? List.of() : watchlistJpaRepository.securitiesOfWatchlist(idWatchlist);
  }

  private static void put(Map<String, AlgoAlertScope> byPair, Integer idTenant, AlgoStrategy strategy,
      Security security, String contextName, boolean active) {
    String key = strategy.getIdAlgoRuleStrategy() + ":" + security.getIdSecuritycurrency();
    // Reaching the same pair twice means two paths lead to it. Both carry the same strategy and therefore the same
    // activation, so the first path is kept.
    byPair.putIfAbsent(key, new AlgoAlertScope(idTenant, strategy, security, contextName, active));
  }

}
