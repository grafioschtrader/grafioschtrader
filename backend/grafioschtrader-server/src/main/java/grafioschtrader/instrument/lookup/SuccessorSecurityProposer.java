package grafioschtrader.instrument.lookup;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecurityLookupCandidateDto;
import grafioschtrader.dto.SecurityLookupResponse;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.DistributionFrequency;

/**
 * Proposes the successor security of an ISIN change when no security with the new ISIN exists yet. Instead of copying
 * the connectors and URL extensions of the predecessor, which mostly address the old ISIN, it asks the security lookup
 * for the new ISIN on the exchange, in the currency and with the asset class of the predecessor, and chooses from its
 * probed connectors without a user:
 * <ol>
 * <li>the connector of the predecessor, with the URL extension the lookup derived for the new ISIN, when its probe
 * succeeded;</li>
 * <li>otherwise the first connector whose probe succeeded. For historical prices a connector only qualifies when it
 * delivers prices from the date of the ISIN change on, the first trading day of the successor.</li>
 * </ol>
 * A feed without a qualifying connector keeps the connector of the predecessor. When the lookup fails or finds no
 * listing, no proposal is made and the caller copies the predecessor as before.
 *
 * <p>
 * The lookup asks external providers and probes connectors, which may take tens of seconds. It must therefore run
 * outside the database transaction that creates the ISIN change.
 * </p>
 */
@Component
public class SuccessorSecurityProposer {

  /** Appended to the name of the predecessor when no provider delivered a name for the successor. */
  public static final String NEW_ISIN_SUFFIX = " (new ISIN)";
  private static final int NAME_MAX_LENGTH = 80;

  private static final Logger log = LoggerFactory.getLogger(SuccessorSecurityProposer.class);

  private final SecurityLookupService securityLookupService;
  private final SecurityJpaRepository securityJpaRepository;
  private final ConnectorProbe connectorProbe;
  private final List<IFeedConnector> feedConnectors;

  public SuccessorSecurityProposer(SecurityLookupService securityLookupService,
      SecurityJpaRepository securityJpaRepository, ConnectorProbe connectorProbe, List<IFeedConnector> feedConnectors) {
    this.securityLookupService = securityLookupService;
    this.securityJpaRepository = securityJpaRepository;
    this.connectorProbe = connectorProbe;
    this.feedConnectors = List.copyOf(feedConnectors);
  }

  /** Connector chosen for one feed, with the URL extension and the frequency its probe derived. */
  record FeedChoice(String idConnector, String urlExtension, DistributionFrequency distributionFrequency) {
  }

  /**
   * Proposes the successor of an ISIN change.
   *
   * @param action the ISIN change as the administrator entered it; only old and new ISIN and the date are read
   * @param locale language of the administrator, used for links to pages offered in several languages
   * @return the unsaved successor, or empty when the old ISIN is unknown, the new ISIN already exists or the lookup
   *         found no listing
   */
  public Optional<Security> propose(SecurityAction action, Locale locale) {
    if (action.getIsinOld() == null || action.getIsinNew() == null
        || !securityJpaRepository.findByIsin(action.getIsinNew()).isEmpty()) {
      return Optional.empty();
    }
    List<Security> oldSecurities = securityJpaRepository.findByIsin(action.getIsinOld());
    return oldSecurities.isEmpty() ? Optional.empty()
        : propose(oldSecurities.getFirst(), action.getIsinNew(), action.getActionDate(), locale, LocalDate.now());
  }

  /**
   * Proposes the successor of a predecessor.
   *
   * @param old        the predecessor
   * @param isinNew    ISIN of the successor
   * @param actionDate date of the ISIN change, becomes the first trading day of the successor
   * @param locale     language for links
   * @param today      reference day of the probes
   * @return the unsaved successor, or empty when the lookup found no listing
   */
  Optional<Security> propose(Security old, String isinNew, LocalDate actionDate, Locale locale, LocalDate today) {
    if (old.getStockexchange() == null || old.getAssetClass() == null) {
      return Optional.empty();
    }
    SecurityLookupResponse response;
    try {
      response = securityLookupService.lookup(isinNew, old.getCurrency(), old.getStockexchange().getMic(),
          old.getTickerSymbol(), old.getAssetClass().getIdAssetClass(), locale);
    } catch (RuntimeException e) {
      log.warn("Lookup of the successor {} of {} failed, the predecessor is copied: {}", isinNew, old.getIsin(),
          e.toString());
      return Optional.empty();
    }
    List<SecurityLookupCandidateDto> candidates = response.candidates().stream()
        .filter(c -> !c.instrumentTypeMismatch()).toList();
    if (candidates.isEmpty()) {
      log.info("Lookup found no listing of the successor {} of {}, the predecessor is copied", isinNew, old.getIsin());
      return Optional.empty();
    }
    Map<SecurityLookupCandidateDto, Predicate<ConnectorAlternativeDto>> coverage = new IdentityHashMap<>();
    Function<SecurityLookupCandidateDto, Predicate<ConnectorAlternativeDto>> fromActionDate = c -> coverage
        .computeIfAbsent(c, _ -> memoize(a -> deliversHistoryFrom(old, c, isinNew, a, actionDate, today)));
    SecurityLookupCandidateDto candidate = chooseCandidate(candidates, old, fromActionDate);
    Security successor = buildSuccessor(old, isinNew, actionDate, candidate, candidates);
    applyConnectors(successor, old, candidate, response, fromActionDate.apply(candidate));
    log.info("Successor {} of {} proposed from the {} listing: history {}, intraday {}, dividend {}, split {}", isinNew,
        old.getIsin(), candidate.providerId(), successor.getIdConnectorHistory(), successor.getIdConnectorIntra(),
        successor.getIdConnectorDividend(), successor.getIdConnectorSplit());
    return Optional.of(successor);
  }

  /**
   * Chooses the listing: the only one, or the first that serves historical prices with the connector of the
   * predecessor, or the first that serves them with any connector, or the first at all.
   */
  private SecurityLookupCandidateDto chooseCandidate(List<SecurityLookupCandidateDto> candidates, Security old,
      Function<SecurityLookupCandidateDto, Predicate<ConnectorAlternativeDto>> fromActionDate) {
    if (candidates.size() == 1) {
      return candidates.getFirst();
    }
    SecurityLookupCandidateDto withHistory = null;
    for (SecurityLookupCandidateDto candidate : candidates) {
      FeedChoice history = chooseConnector(FeedSupport.FS_HISTORY, candidate.historyConnectors(),
          old.getIdConnectorHistory(), fromActionDate.apply(candidate));
      if (history != null && history.idConnector().equals(old.getIdConnectorHistory())) {
        return candidate;
      }
      if (history != null && withHistory == null) {
        withHistory = candidate;
      }
    }
    return withHistory != null ? withHistory : candidates.getFirst();
  }

  /**
   * Builds the successor with exchange, currency and asset class of the predecessor and the master data of the
   * listing. A value the listing lacks is taken from the predecessor.
   */
  private Security buildSuccessor(Security old, String isinNew, LocalDate actionDate,
      SecurityLookupCandidateDto candidate, List<SecurityLookupCandidateDto> candidates) {
    Security successor = new Security();
    successor.setIsin(isinNew);
    successor.setName(chooseName(old, candidate, candidates));
    successor.setCurrency(old.getCurrency());
    successor.setAssetClass(old.getAssetClass());
    successor.setStockexchange(old.getStockexchange());
    successor.setLeverageFactor(old.getLeverageFactor());
    successor.setActiveFromDate(actionDate);
    successor.setActiveToDate(candidate.activeToDate() != null ? candidate.activeToDate() : old.getActiveToDate());
    successor.setTickerSymbol(firstNonNull(candidate.tickerSymbol(), old.getTickerSymbol()));
    successor.setDenomination(firstNonNull(candidate.denomination(), old.getDenomination()));
    successor.setProductLink(firstNonNull(candidate.productLink(), old.getProductLink()));
    successor.setStockexchangeLink(firstNonNull(candidate.stockexchangeLink(), old.getStockexchangeLink()));
    successor.setIssuerCountry(firstNonNull(candidate.issuerCountry(), old.getIssuerCountry()));
    return successor;
  }

  /**
   * Sets the connectors of the four feeds and the distribution frequency. Dividends are left without a connector when
   * the asset class receives none or the listing distributes nothing, splits when the asset class has none.
   */
  private void applyConnectors(Security successor, Security old, SecurityLookupCandidateDto candidate,
      SecurityLookupResponse response, Predicate<ConnectorAlternativeDto> fromActionDate) {
    FeedChoice history = chooseConnector(FeedSupport.FS_HISTORY, candidate.historyConnectors(),
        old.getIdConnectorHistory(), fromActionDate);
    successor.setIdConnectorHistory(history != null ? history.idConnector() : old.getIdConnectorHistory());
    successor.setUrlHistoryExtend(history != null ? history.urlExtension() : old.getUrlHistoryExtend());

    FeedChoice intra = chooseConnector(FeedSupport.FS_INTRA, candidate.intraConnectors(), old.getIdConnectorIntra(),
        _ -> true);
    successor.setIdConnectorIntra(intra != null ? intra.idConnector() : old.getIdConnectorIntra());
    successor.setUrlIntraExtend(intra != null ? intra.urlExtension() : old.getUrlIntraExtend());

    FeedChoice dividend = null;
    if (response.dividendConnectorApplicable() && !candidate.distributesNothing()) {
      dividend = chooseConnector(FeedSupport.FS_DIVIDEND, candidate.dividendConnectors(),
          old.getIdConnectorDividend(), _ -> true);
      successor.setIdConnectorDividend(dividend != null ? dividend.idConnector() : old.getIdConnectorDividend());
      successor.setUrlDividendExtend(dividend != null ? dividend.urlExtension() : old.getUrlDividendExtend());
    }
    successor.setDistributionFrequency(chooseDistributionFrequency(old, candidate, dividend));

    if (response.splitConnectorApplicable()) {
      FeedChoice split = chooseConnector(FeedSupport.FS_SPLIT, candidate.splitConnectors(), old.getIdConnectorSplit(),
          _ -> true);
      successor.setIdConnectorSplit(split != null ? split.idConnector() : old.getIdConnectorSplit());
      successor.setUrlSplitExtend(split != null ? split.urlExtension() : old.getUrlSplitExtend());
    }
  }

  /**
   * Chooses the connector of one feed: that of the predecessor when its probe succeeded, otherwise the first whose
   * probe succeeded; for dividends and splits a probe that delivered data comes before one that found none yet.
   *
   * @param feed         the feed
   * @param alternatives the probed connectors of the listing, in the order of the lookup
   * @param idConnector  connector of the predecessor for this feed, may be null
   * @param qualifies    further condition a connector has to meet, e.g. prices from the first trading day on
   * @return the choice, or null when no connector qualifies
   */
  static FeedChoice chooseConnector(FeedSupport feed, List<ConnectorAlternativeDto> alternatives, String idConnector,
      Predicate<ConnectorAlternativeDto> qualifies) {
    List<ConnectorAlternativeDto> usable = alternatives == null ? List.of()
        : alternatives.stream().filter(a -> isUsable(feed, a)).toList();
    Optional<ConnectorAlternativeDto> same = usable.stream().filter(a -> a.idConnector().equals(idConnector))
        .findFirst();
    if (same.isPresent() && qualifies.test(same.get())) {
      return toChoice(same.get());
    }
    for (ConnectorProbeStatus status : List.of(ConnectorProbeStatus.OK, ConnectorProbeStatus.NO_DATA)) {
      for (ConnectorAlternativeDto alternative : usable) {
        if (alternative.probeStatus() == status && !alternative.idConnector().equals(idConnector)
            && qualifies.test(alternative)) {
          return toChoice(alternative);
        }
      }
    }
    return null;
  }

  /**
   * Tells whether a probe makes a connector usable, by the same rule as the lookup dialog: it delivered data, or, for
   * dividends and splits, it answered without finding any.
   */
  static boolean isUsable(FeedSupport feed, ConnectorAlternativeDto alternative) {
    return alternative.probeStatus() == ConnectorProbeStatus.OK
        || alternative.probeStatus() == ConnectorProbeStatus.NO_DATA
            && (feed == FeedSupport.FS_DIVIDEND || feed == FeedSupport.FS_SPLIT);
  }

  /**
   * Tells whether a history connector delivers prices from the first trading day of the successor on. The request runs
   * on a transient security with the new ISIN and the master data of the listing. A date that has not been reached yet
   * cannot be checked and is accepted.
   */
  private boolean deliversHistoryFrom(Security old, SecurityLookupCandidateDto candidate, String isinNew,
      ConnectorAlternativeDto alternative, LocalDate actionDate, LocalDate today) {
    if (actionDate == null || actionDate.isAfter(today)) {
      return true;
    }
    IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors, alternative.idConnector(),
        FeedSupport.FS_HISTORY);
    if (connector == null) {
      return false;
    }
    SecurityBaseDataDto baseData = new SecurityBaseDataDto(candidate.providerId(), candidate.providerInstrumentId(),
        firstNonNull(candidate.isin(), isinNew), firstNonNull(candidate.currency(), old.getCurrency()),
        old.getStockexchange().getMic(), candidate.name(), firstNonNull(candidate.tickerSymbol(), old.getTickerSymbol()),
        candidate.issuerCountry(), actionDate, candidate.activeToDate(), null, null, null, null, null, null, null);
    Security draft = ConnectorProbe.draftSecurity(baseData, old.getStockexchange(),
        old.getAssetClass().getCategoryType(), old.getAssetClass().getSpecialInvestmentInstrument());
    return connectorProbe.deliversHistoryFrom(connector, draft, alternative.urlExtension(), actionDate, today);
  }

  /** Remembers the answer per connector and URL extension, so that no connector is asked twice. */
  private static Predicate<ConnectorAlternativeDto> memoize(Predicate<ConnectorAlternativeDto> predicate) {
    Map<String, Boolean> answers = new HashMap<>();
    return a -> answers.computeIfAbsent(a.idConnector() + "|" + a.urlExtension(), _ -> predicate.test(a));
  }

  /**
   * The name of the first listing not written entirely in capitals, as the lookup dialog prefers it, otherwise that of
   * the chosen listing; without any, the name of the predecessor marked as successor.
   */
  private static String chooseName(Security old, SecurityLookupCandidateDto candidate,
      List<SecurityLookupCandidateDto> candidates) {
    String name = candidates.stream().map(SecurityLookupCandidateDto::name)
        .filter(n -> n != null && !isAllUpperCase(n)).findFirst().orElse(candidate.name());
    return name == null || name.isBlank() ? successorName(old.getName()) : truncate(name.strip());
  }

  /**
   * Marks the name of a predecessor as that of its successor, within the length a security name may have.
   *
   * @param oldName name of the predecessor
   * @return the name with {@value #NEW_ISIN_SUFFIX}, shortened to fit
   */
  public static String successorName(String oldName) {
    int keep = Math.min(oldName.length(), NAME_MAX_LENGTH - NEW_ISIN_SUFFIX.length());
    return oldName.substring(0, keep).strip() + NEW_ISIN_SUFFIX;
  }

  private static DistributionFrequency chooseDistributionFrequency(Security old, SecurityLookupCandidateDto candidate,
      FeedChoice dividend) {
    if (candidate.distributionFrequency() != null) {
      return candidate.distributionFrequency();
    }
    if (dividend != null && dividend.distributionFrequency() != null) {
      return dividend.distributionFrequency();
    }
    List<DistributionFrequency> agreed = candidate.dividendConnectors().stream()
        .filter(a -> isUsable(FeedSupport.FS_DIVIDEND, a) && a.distributionFrequency() != null)
        .map(ConnectorAlternativeDto::distributionFrequency).distinct().toList();
    return agreed.size() == 1 ? agreed.getFirst() : old.getDistributionFrequency();
  }

  private static boolean isAllUpperCase(String name) {
    return name.equals(name.toUpperCase(Locale.ROOT)) && !name.equals(name.toLowerCase(Locale.ROOT));
  }

  private static String truncate(String name) {
    return name.length() <= NAME_MAX_LENGTH ? name : name.substring(0, NAME_MAX_LENGTH).strip();
  }

  private static FeedChoice toChoice(ConnectorAlternativeDto alternative) {
    return new FeedChoice(alternative.idConnector(), alternative.urlExtension(), alternative.distributionFrequency());
  }

  private static <T> T firstNonNull(T value, T fallback) {
    return value != null ? value : fallback;
  }
}
