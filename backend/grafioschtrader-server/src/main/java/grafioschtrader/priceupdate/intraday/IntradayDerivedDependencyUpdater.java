package grafioschtrader.priceupdate.intraday;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Updates the intraday prices of a list of securities so that every derived security is calculated after the
 * instruments it is derived from.
 *
 * <p>
 * A derived security takes its price from its main link and, with a formula, from further linked instruments. That
 * calculation is only as current as the linked prices it reads, which leads to three requirements:
 * <ul>
 * <li>A linked instrument that is not part of the update list, for example the currency pair behind a derived security
 * of a watchlist, is updated as well before the calculation. It is not part of the result.</li>
 * <li>Instruments the caller has refreshed before, such as the currency pairs of a watchlist or prices received from a
 * GTNet peer, are not requested again and are used as they are; a GTNet price exists only on that instance.</li>
 * <li>A derived security built on another derived security is calculated after it. The securities are therefore
 * calculated in dependency levels, each level in parallel.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Like the intraday loaders it uses, this class is created by {@code SecurityJpaRepositoryImpl} and is not a Spring
 * bean; every write commits on its own through the repository proxies.
 * </p>
 */
public class IntradayDerivedDependencyUpdater {

  private static final Logger log = LoggerFactory.getLogger(IntradayDerivedDependencyUpdater.class);

  private final IIntradayLoad<Security> intradayThruConnector;
  private final IntradayThruCalculation<Security> intradayThruCalculation;
  private final SecurityJpaRepository securityJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  /**
   * Creates the updater from the loaders and repositories of the owning repository implementation.
   *
   * @param intradayThruConnector            loads the prices of securities that have an intraday connector
   * @param intradayThruCalculation          calculates the prices of derived securities
   * @param securityJpaRepository            loads linked securities that are not part of the update list
   * @param currencypairJpaRepository        loads and updates linked currency pairs that are not part of the update
   *                                         list
   * @param securityDerivedLinkJpaRepository supplies the formula links of the derived securities
   */
  public IntradayDerivedDependencyUpdater(IIntradayLoad<Security> intradayThruConnector,
      IntradayThruCalculation<Security> intradayThruCalculation, SecurityJpaRepository securityJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository,
      SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository) {
    this.intradayThruConnector = intradayThruConnector;
    this.intradayThruCalculation = intradayThruCalculation;
    this.securityJpaRepository = securityJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.securityDerivedLinkJpaRepository = securityDerivedLinkJpaRepository;
  }

  /**
   * Updates the last prices of the given securities, the connector based ones first and then the derived ones in the
   * order of their dependencies.
   *
   * @param securities           the securities to update, derived or not
   * @param refreshedInstruments instruments the caller has already updated in this price update; they are neither
   *                             requested again nor reloaded from the database
   * @param refreshMissingLinks  true to load and update linked instruments that are neither in {@code securities} nor
   *                             in {@code refreshedInstruments}. False when the caller has just updated every
   *                             instrument anyway, as the periodic update of all prices does.
   * @param singleThread         true to process every step on the calling thread
   * @return the securities after their update: first the non-derived ones, then the derived ones, each in input order.
   *         Linked instruments that were updated additionally are not included.
   */
  public List<Security> updateLastPrices(List<Security> securities,
      Collection<? extends Securitycurrency<?>> refreshedInstruments, boolean refreshMissingLinks,
      boolean singleThread) {
    final Map<Integer, Securitycurrency<?>> refreshed = new HashMap<>();
    refreshedInstruments.forEach(sc -> putRefreshed(refreshed, sc));
    final List<Security> direct = securities.stream().filter(s -> !s.isDerivedInstrument()).toList();
    final List<Security> derived = securities.stream().filter(Security::isDerivedInstrument).toList();

    final Set<Integer> knownIds = new HashSet<>(refreshed.keySet());
    securities.stream().map(Security::getIdSecuritycurrency).filter(Objects::nonNull).forEach(knownIds::add);
    final LinkedInstruments linked = resolveLinks(derived, knownIds, refreshMissingLinks);

    currencypairJpaRepository.updateLastPriceByList(linked.currencypairs).forEach(cp -> putRefreshed(refreshed, cp));
    final List<Security> connectorInput = new ArrayList<>(direct);
    connectorInput.addAll(linked.securities);
    final List<Security> updatedDirect = intradayThruConnector.updateLastPriceOfSecuritycurrency(connectorInput,
        singleThread);
    updatedDirect.forEach(s -> putRefreshed(refreshed, s));

    final List<Security> allDerived = new ArrayList<>(derived);
    allDerived.addAll(linked.derived);
    final Map<Security, Security> calculated = calculateInLevels(allDerived, linked.linkIds, refreshed, singleThread);

    final List<Security> result = new ArrayList<>(updatedDirect.subList(0, direct.size()));
    derived.forEach(s -> result.add(calculated.getOrDefault(s, s)));
    return result;
  }

  /**
   * Calculates the derived securities level by level. A level holds the securities whose links are no longer pending;
   * after a level its results become refreshed instruments for the next one.
   */
  private Map<Security, Security> calculateInLevels(List<Security> derived, Map<Integer, Set<Integer>> linkIds,
      Map<Integer, Securitycurrency<?>> refreshed, boolean singleThread) {
    final Map<Security, Security> calculated = new HashMap<>();
    for (List<Security> level : dependencyLevels(derived, linkIds)) {
      final List<Security> results = intradayThruCalculation.updateLastPriceOfSecuritycurrency(level, refreshed,
          singleThread);
      for (int i = 0; i < level.size(); i++) {
        calculated.put(level.get(i), results.get(i));
        putRefreshed(refreshed, results.get(i));
      }
    }
    return calculated;
  }

  /**
   * Orders derived securities into levels, so that every security comes after all derived securities it links to. A
   * cycle cannot be ordered; its members are calculated together in the last level and a warning is logged.
   *
   * @param derived the derived securities to calculate
   * @param linkIds the ids of the instruments each derived security links to, keyed by its id
   * @return the levels in calculation order, none of them empty
   */
  static List<List<Security>> dependencyLevels(List<Security> derived, Map<Integer, Set<Integer>> linkIds) {
    final List<Security> pending = new ArrayList<>(derived);
    final List<List<Security>> levels = new ArrayList<>();
    while (!pending.isEmpty()) {
      final Set<Integer> pendingIds = pending.stream().map(Security::getIdSecuritycurrency).filter(Objects::nonNull)
          .collect(Collectors.toSet());
      List<Security> level = pending.stream().filter(s -> linkIds.getOrDefault(s.getIdSecuritycurrency(), Set.of())
          .stream().noneMatch(id -> !id.equals(s.getIdSecuritycurrency()) && pendingIds.contains(id))).toList();
      if (level.isEmpty()) {
        log.warn("Derived securities with cyclic links, calculated without ordering: {}", pendingIds);
        level = List.copyOf(pending);
      }
      pending.removeAll(level);
      levels.add(level);
    }
    return levels;
  }

  /**
   * Collects the link ids of the derived securities and, if requested, loads the linked instruments that nobody updates
   * in this run. Loaded derived securities are followed in turn, so a chain of derived instruments is resolved down to
   * its base instruments.
   */
  private LinkedInstruments resolveLinks(List<Security> derived, Set<Integer> knownIds, boolean loadMissing) {
    final LinkedInstruments linked = new LinkedInstruments();
    final Set<Integer> seen = new HashSet<>(knownIds);
    List<Security> frontier = derived;
    while (!frontier.isEmpty()) {
      addLinkIds(frontier, linked.linkIds);
      if (!loadMissing) {
        break;
      }
      final Set<Integer> missing = new LinkedHashSet<>();
      frontier.forEach(s -> linked.linkIds.getOrDefault(s.getIdSecuritycurrency(), Set.of()).stream().filter(seen::add)
          .forEach(missing::add));
      frontier = loadMissing(missing, linked);
    }
    return linked;
  }

  /** Loads the missing ids as securities and the remainder as currency pairs; returns the derived securities found. */
  private List<Security> loadMissing(Set<Integer> missing, LinkedInstruments linked) {
    if (missing.isEmpty()) {
      return List.of();
    }
    final List<Security> loadedDerived = new ArrayList<>();
    final Set<Integer> notFound = new HashSet<>(missing);
    for (Security security : securityJpaRepository.findAllById(missing)) {
      notFound.remove(security.getIdSecuritycurrency());
      (security.isDerivedInstrument() ? loadedDerived : linked.securities).add(security);
    }
    if (!notFound.isEmpty()) {
      linked.currencypairs.addAll(currencypairJpaRepository.findAllById(notFound));
    }
    linked.derived.addAll(loadedDerived);
    return loadedDerived;
  }

  private void addLinkIds(List<Security> derived, Map<Integer, Set<Integer>> linkIds) {
    final List<Integer> ids = derived.stream().map(Security::getIdSecuritycurrency).filter(Objects::nonNull).toList();
    derived.stream().filter(s -> s.getIdSecuritycurrency() != null).forEach(s -> linkIds
        .computeIfAbsent(s.getIdSecuritycurrency(), _ -> new HashSet<>()).add(s.getIdLinkSecuritycurrency()));
    if (!ids.isEmpty()) {
      for (SecurityDerivedLink link : securityDerivedLinkJpaRepository.findByIdEmIdSecuritycurrencyIn(ids)) {
        linkIds.computeIfAbsent(link.getIdSecuritycurrency(), _ -> new HashSet<>())
            .add(link.getIdLinkSecuritycurrency());
      }
    }
  }

  private static void putRefreshed(Map<Integer, Securitycurrency<?>> refreshed, Securitycurrency<?> securitycurrency) {
    if (securitycurrency.getIdSecuritycurrency() != null) {
      refreshed.put(securitycurrency.getIdSecuritycurrency(), securitycurrency);
    }
  }

  /** The instruments a set of derived securities links to, beyond those the caller updates itself. */
  private static final class LinkedInstruments {
    /** Ids of the instruments each derived security links to, keyed by the id of the derived security. */
    final Map<Integer, Set<Integer>> linkIds = new HashMap<>();
    /** Linked securities with an intraday connector. */
    final List<Security> securities = new ArrayList<>();
    /** Linked derived securities, calculated before the securities derived from them. */
    final List<Security> derived = new ArrayList<>();
    /** Linked currency pairs. */
    final List<Currencypair> currencypairs = new ArrayList<>();
  }
}
