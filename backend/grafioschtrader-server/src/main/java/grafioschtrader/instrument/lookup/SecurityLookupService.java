package grafioschtrader.instrument.lookup;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.securitysearch.BivaSecuritySearch;
import grafioschtrader.connector.securitysearch.BoursoramaInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.BoursoramaSecuritySearch;
import grafioschtrader.connector.securitysearch.ConsorsbankInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.ConsorsbankSecuritySearch;
import grafioschtrader.connector.securitysearch.EodhdInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.EodhdSecuritySearch;
import grafioschtrader.connector.securitysearch.EuronextInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.EuronextSecuritySearch;
import grafioschtrader.connector.securitysearch.ISecuritySearchProvider;
import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.NasdaqNordicSecuritySearch;
import grafioschtrader.connector.securitysearch.OnvistaInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.OnvistaSecuritySearch;
import grafioschtrader.connector.securitysearch.OpenFigiInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.OpenFigiSecuritySearch;
import grafioschtrader.connector.securitysearch.SearchCandidateMatcher;
import grafioschtrader.connector.securitysearch.SecurityBaseDataResult;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.connector.securitysearch.SecuritySearchService;
import grafioschtrader.connector.securitysearch.SixInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.SixSecuritySearch;
import grafioschtrader.connector.securitysearch.ViennaInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.ViennaSecuritySearch;
import grafioschtrader.connector.securitysearch.XetraInstrumentTypeMapper;
import grafioschtrader.connector.securitysearch.XetraSecuritySearch;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.connector.yahoo.YahooInstrumentTypeMapper;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecurityLookupCandidateDto;
import grafioschtrader.dto.SecurityLookupResponse;
import grafioschtrader.dto.SecurityLookupResponse.ProviderStatus;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Proposes the data of a security that is being created from ISIN, currency, exchange and the asset class the user
 * chose. It searches the data providers that cover the chosen type at that exchange, fetches the master data of every
 * listing that does not contradict the query, and offers per feed the connectors that can serve the listing - each with
 * the URL extension the connector derives itself and the outcome of a trial request.
 *
 * <p>
 * The asset class is the user's choice and is not derived: a region read from the provider data would be wrong for
 * every fifth to third security. The type the provider reports serves as a plausibility check instead; a listing whose
 * type contradicts the choice is flagged, not dropped.
 * </p>
 *
 * <p>
 * Nothing is written: the probes run on a transient security that is never saved, and the user copies the chosen values
 * into the security form, which is saved through the normal dialog and its validation. Every provider and every probe
 * runs under a deadline; a failure becomes a status and never discards the results of the others.
 * </p>
 *
 * <p>
 * SIX, Deutsche Börse (Xetra, Frankfurt), Wiener Börse, Euronext, Yahoo, EODHD (with an API key), onvista,
 * Boursorama, Consorsbank, BIVA and Nasdaq Nordic (only for their stock exchange hyperlink) and OpenFIGI are searched
 * ({@link #LOOKUP_PROVIDERS}),
 * each only when it covers the exchange and the chosen type - decided by the price connector of the same data source
 * ({@link ISecuritySearchProvider#getFeedConnectorShortId()}) or by the provider itself;
 * only listings on the queried exchange in the queried currency are kept, the currency of a Yahoo listing being
 * confirmed by its master data. The same listing found by several providers is merged by {@link LookupListingMerger};
 * OpenFIGI only supplements it, e.g. with the ticker of a Frankfurt listing. The connectors offered are not limited to
 * these providers: DivvyDiary, for example, needs only the ISIN.
 * </p>
 */
@Service
public class SecurityLookupService {

  private static final Logger log = LoggerFactory.getLogger(SecurityLookupService.class);

  /** Source of a field that a rule derived rather than a provider delivered. */
  static final String DERIVED_SOURCE = "derived";

  /** Providers asked by the lookup. */
  static final Set<String> LOOKUP_PROVIDERS = Set.of(SixSecuritySearch.PROVIDER_ID, XetraSecuritySearch.PROVIDER_ID,
      ViennaSecuritySearch.PROVIDER_ID, EuronextSecuritySearch.PROVIDER_ID, YahooHelper.YAHOO,
      EodhdSecuritySearch.PROVIDER_ID, OnvistaSecuritySearch.PROVIDER_ID, BoursoramaSecuritySearch.PROVIDER_ID,
      ConsorsbankSecuritySearch.PROVIDER_ID, BivaSecuritySearch.PROVIDER_ID, NasdaqNordicSecuritySearch.PROVIDER_ID,
      OpenFigiSecuritySearch.PROVIDER_ID);

  private static final Duration SEARCH_DEADLINE = Duration.ofSeconds(20);
  private static final Duration PROBE_DEADLINE = Duration.ofSeconds(20);

  private final SecuritySearchService securitySearchService;
  private final List<ISecuritySearchProvider> providers;
  private final List<IFeedConnector> feedConnectors;
  private final ConnectorProbe connectorProbe;
  private final AssetclassJpaRepository assetclassJpaRepository;
  private final StockexchangeJpaRepository stockexchangeJpaRepository;

 
  public SecurityLookupService(SecuritySearchService securitySearchService, List<ISecuritySearchProvider> providers,
      List<IFeedConnector> feedConnectors, ConnectorProbe connectorProbe,
      AssetclassJpaRepository assetclassJpaRepository, StockexchangeJpaRepository stockexchangeJpaRepository) {
    this.securitySearchService = securitySearchService;
    this.providers = List.copyOf(providers);
    this.feedConnectors = List.copyOf(feedConnectors);
    this.connectorProbe = connectorProbe;
    this.assetclassJpaRepository = assetclassJpaRepository;
    this.stockexchangeJpaRepository = stockexchangeJpaRepository;
  }

  /**
   * Looks up the listings of an instrument.
   *
   * @param isin         ISIN of the instrument; must be syntactically valid
   * @param currency     trading currency
   * @param mic          MIC of the exchange the security is created on
   * @param ticker       ticker entered in the form, may be null; a fallback search term for providers that know an ISIN
   *                     only for another listing
   * @param idAssetClass asset class chosen in the form
   * @param locale       language of the user, used for links to pages offered in several languages
   * @return candidates and the status of every provider that was asked
   * @throws IllegalArgumentException when the asset class does not exist
   */
  public SecurityLookupResponse lookup(String isin, String currency, String mic, String ticker, Integer idAssetClass,
      Locale locale) {
    Assetclass assetclass = assetclassJpaRepository.findById(idAssetClass)
        .orElseThrow(() -> new IllegalArgumentException("Unknown asset class " + idAssetClass));
    return lookup(isin, currency, mic, ticker, assetclass, locale);
  }

  /**
   * Looks up the listings of an instrument of the given asset class.
   *
   * @param assetclass asset class chosen in the form; its type and financial instrument select providers and connectors
   * @see #lookup(String, String, String, String, Integer, Locale)
   */
  SecurityLookupResponse lookup(String isin, String currency, String mic, String ticker, Assetclass assetclass,
      Locale locale) {
    SecuritySearchQuery query = SecuritySearchQuery.of(isin, currency, mic, ticker);
    Stockexchange stockexchange = query.mic() == null ? null
        : stockexchangeJpaRepository.findByMic(query.mic()).orElse(null);
    ChosenType chosen = new ChosenType(assetclass.getIdAssetClass(), assetclass.getCategoryType(),
        assetclass.getSpecialInvestmentInstrument());
    FeedApplicability applicable = feedApplicability(chosen, stockexchange);
    Set<String> covering = coveringProviders(query.mic(), stockexchange, chosen);
    List<SecuritySearchResult> results = new ArrayList<>(securitySearchService.search(query, covering,
        SEARCH_DEADLINE));
    List<LookupListing> listings = collectListings(results, query, locale);
    List<SecuritySearchResult> indexSearch = searchIndexByCode(query, chosen, covering, listings);
    if (!indexSearch.isEmpty()) {
      Set<String> repeated = indexSearch.stream().map(SecuritySearchResult::providerId).collect(Collectors.toSet());
      results.removeIf(r -> repeated.contains(r.providerId()));
      results.addAll(indexSearch);
      listings.addAll(collectListings(indexSearch, query, locale));
    }
    List<ProviderStatus> providerStatus = results.stream()
        .map(r -> new ProviderStatus(r.providerId(), r.status(), r.detail())).toList();
    List<SecurityLookupCandidateDto> candidates = LookupListingMerger.merge(listings).stream()
        .map(listing -> createCandidate(listing, query.isin(), query.mic(), stockexchange, chosen, applicable, locale))
        .toList();
    return new SecurityLookupResponse(candidates, providerStatus, applicable.dividend(), applicable.split());
  }

  /** Keeps the listings on the queried exchange in the queried currency, each with its master data. */
  private List<LookupListing> collectListings(List<SecuritySearchResult> results, SecuritySearchQuery query,
      Locale locale) {
    List<LookupListing> listings = new ArrayList<>();
    for (SecuritySearchResult result : results) {
      for (SecuritySearchCandidateDto candidate : result.candidates()) {
        if (isOnQueriedMic(candidate, query.mic()) && !contradictsCurrency(candidate, query.currency())) {
          LookupListing listing = toListing(candidate, query.mic(), locale);
          if (query.currency() == null || listing.baseData().currency() == null
              || sameCurrency(query.currency(), listing.baseData().currency())) {
            listings.add(listing);
          }
        }
      }
    }
    return listings;
  }

  /** Providers that find an index by its code when its ISIN is unknown to them. */
  private static final Set<String> INDEX_CODE_PROVIDERS = Set.of(YahooHelper.YAHOO, EodhdSecuritySearch.PROVIDER_ID);

  /**
   * Yahoo finds no index by its ISIN, EODHD only some, and an index symbol cannot be derived from the code either
   * ({@code ^GSPC} for the S&P 500). When an index was chosen and another provider - Nasdaq Nordic or the EODHD index
   * list, for instance - delivered the index code, Yahoo and EODHD are asked once more with that code if they found
   * nothing; their answers replace the first ones.
   *
   * @return the results of the second search, empty when it does not apply
   */
  private List<SecuritySearchResult> searchIndexByCode(SecuritySearchQuery query, ChosenType chosen,
      Set<String> covering, List<LookupListing> listings) {
    if (chosen.sii() != SpecialInvestmentInstruments.NON_INVESTABLE_INDICES) {
      return List.of();
    }
    Set<String> missing = INDEX_CODE_PROVIDERS.stream().filter(covering::contains)
        .filter(id -> listings.stream().noneMatch(l -> l.providerIds().contains(id))).collect(Collectors.toSet());
    Optional<String> code = listings.stream().map(l -> l.baseData().tickerSymbol())
        .filter(t -> t != null && !t.isBlank() && !t.equalsIgnoreCase(query.ticker())).findFirst();
    return missing.isEmpty() || code.isEmpty() ? List.of()
        : securitySearchService.search(SecuritySearchQuery.of(query.isin(), query.currency(), query.mic(), code.get()),
            missing, SEARCH_DEADLINE);
  }

  /** Whether dividend and split connectors make sense for the chosen asset class at the exchange. */
  record FeedApplicability(boolean dividend, boolean split) {
  }

  /**
   * Applies the rules of the security entity to the chosen asset class, so that the lookup offers the same connectors
   * as the security form: {@link Security#isDividendCapableInstrument()} and {@link Security#canHaveSplitConnector()}.
   * The distribution frequency is not known before the lookup and is therefore not part of the dividend rule; an
   * instrument the lookup finds to distribute nothing has its dividend table disabled instead. An exchange without
   * market value, e.g. for private papers, has neither.
   *
   * @param chosen        the chosen asset class
   * @param stockexchange the exchange of the query, may be null
   * @return the applicability of the dividend and the split feed
   */
  static FeedApplicability feedApplicability(ChosenType chosen, Stockexchange stockexchange) {
    if (stockexchange != null && stockexchange.isNoMarketValue()) {
      return new FeedApplicability(false, false);
    }
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(chosen.categoryType());
    assetclass.setSpecialInvestmentInstrument(chosen.sii());
    Security security = new Security();
    security.setAssetClass(assetclass);
    return new FeedApplicability(security.isDividendCapableInstrument(), security.canHaveSplitConnector());
  }

  /** Asset class chosen by the user; type and financial instrument are those of the asset class. */
  record ChosenType(Integer idAssetClass, AssetclassType categoryType, SpecialInvestmentInstruments sii) {
  }

  /**
   * Selects the lookup providers worth asking. A provider with a price connector of the same data source covers what
   * that connector supports - exchange and type - regardless of whether the connector is activated, since the search
   * does not use it. A provider without one decides by itself.
   */
  private Set<String> coveringProviders(String mic, Stockexchange stockexchange, ChosenType chosen) {
    String countryCode = stockexchange == null ? null : stockexchange.getCountryCode();
    return providers.stream().filter(p -> LOOKUP_PROVIDERS.contains(p.getProviderId()))
        .filter(p -> p.getFeedConnectorShortId()
            .map(shortId -> feedConnectors.stream().filter(fc -> shortId.equals(fc.getShortID()))
                .anyMatch(fc -> fc.supports(mic, countryCode, chosen.categoryType(), chosen.sii())))
            .orElseGet(() -> p.covers(chosen.categoryType(), chosen.sii())))
        .map(ISecuritySearchProvider::getProviderId).collect(Collectors.toSet());
  }

  /**
   * Tells whether the type a provider reports contradicts the chosen asset class. A provider that reports no type, or
   * no asset class type, contradicts nothing in that respect. ETF and mutual fund count as compatible, because Wiener
   * Börse reports every fund as "Fondsdaten".
   */
  static boolean contradictsChosenType(Optional<InstrumentType> reported, ChosenType chosen) {
    if (reported.isEmpty()) {
      return false;
    }
    InstrumentType type = reported.get();
    boolean siiCompatible = type.specialInvestmentInstrument() == chosen.sii()
        || isFund(type.specialInvestmentInstrument()) && isFund(chosen.sii());
    return !siiCompatible || type.categoryType() != null && type.categoryType() != chosen.categoryType();
  }

  private static boolean isFund(SpecialInvestmentInstruments sii) {
    return sii == SpecialInvestmentInstruments.ETF || sii == SpecialInvestmentInstruments.MUTUAL_FUND;
  }

  /**
   * The security is created with the ISIN entered in the form, so connectors keyed by the ISIN, such as DivvyDiary, are
   * probed with it when the provider delivered none - Yahoo returns no ISIN. The candidate itself keeps the identity
   * its provider established.
   */
  static SecurityBaseDataDto withIsinForProbes(SecurityBaseDataDto d, String queriedIsin) {
    return d.isin() != null || queriedIsin == null ? d
        : new SecurityBaseDataDto(d.providerId(), d.providerInstrumentId(), queriedIsin, d.currency(), d.mic(),
            d.name(), d.tickerSymbol(), d.issuerCountry(), d.activeFromDate(), d.activeToDate(),
            d.distributionFrequency(), d.denomination(), d.productLink(), d.stockexchangeLink(), d.listedSince(),
            d.distributing(), d.providerAttributes());
  }

  /** A currency the search established already decides, so that no master data is requested for that listing. */
  private static boolean contradictsCurrency(SecuritySearchCandidateDto candidate, String currency) {
    return currency != null && candidate.currency() != null && candidate.currencyEvidence().isEstablished()
        && !sameCurrency(currency, candidate.currency());
  }

  /** A listing whose exchange is not established is dropped: it may be any other listing of the instrument. */
  private static boolean isOnQueriedMic(SecuritySearchCandidateDto candidate, String mic) {
    return mic == null || candidate.hasEstablishedMic() && mic.equals(candidate.mic());
  }

  /**
   * Compares two currencies after folding minor units, so that {@code GBp} and {@code GBP} are equal.
   *
   * @param a a currency, may be null
   * @param b a currency, may be null
   * @return true when both are set and equal
   */
  static boolean sameCurrency(String a, String b) {
    return a != null && b != null
        && SearchCandidateMatcher.normalizeCurrency(a).equals(SearchCandidateMatcher.normalizeCurrency(b));
  }

  private LookupListing toListing(SecuritySearchCandidateDto candidate, String mic, Locale locale) {
    SecurityBaseDataResult baseDataResult = fetchBaseData(candidate, locale);
    SecurityBaseDataDto baseData = baseDataResult.data() != null ? baseDataResult.data()
        : fromSearchCandidate(candidate, mic);
    return LookupListing.of(candidate, baseDataResult, baseData, deriveInstrumentType(candidate, baseData));
  }

  private SecurityLookupCandidateDto createCandidate(LookupListing listing, String queriedIsin, String mic,
      Stockexchange stockexchange, ChosenType chosen, FeedApplicability applicable, Locale locale) {
    SecuritySearchCandidateDto candidate = listing.candidate();
    SecurityBaseDataResult baseDataResult = listing.baseDataResult();
    SecurityBaseDataDto baseData = listing.baseData();
    Optional<InstrumentType> instrumentType = listing.instrumentType();
    AssetclassType reportedCategoryType = instrumentType.map(InstrumentType::categoryType).orElse(null);
    SpecialInvestmentInstruments reportedSii = instrumentType.map(InstrumentType::specialInvestmentInstrument)
        .orElse(null);
    SpecialInvestmentInstruments sii = chosen.sii();
    Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives = connectorAlternatives(
        withIsinForProbes(baseData, queriedIsin), listing.providerBaseData(), mic, stockexchange,
        chosen.categoryType(), sii, applicable);
    Map<String, String> fieldSources = new LinkedHashMap<>(listing.fieldSources());
    String productLink = baseData.productLink();
    if (productLink == null && (productLink = ProductLinkResolver.resolve(baseData, sii, locale)) != null) {
      fieldSources.put("productLink", DERIVED_SOURCE);
    }
    String stockexchangeLink = baseData.stockexchangeLink();
    if (stockexchangeLink == null && (stockexchangeLink = StockexchangeLinkResolver.resolve(baseData.mic(),
        baseData.tickerSymbol(), baseData.isin() != null ? baseData.isin() : queriedIsin, baseData.name(), sii,
        locale)) != null) {
      fieldSources.put("stockexchangeLink", DERIVED_SOURCE);
    }
    return new SecurityLookupCandidateDto(candidate.providerId(), candidate.providerInstrumentId(),
        candidate.providerInstrumentType(), baseData.isin(), baseData.currency(), baseData.mic(), baseData.name(),
        baseData.tickerSymbol(), baseData.issuerCountry(), baseData.activeFromDate(), baseData.activeToDate(),
        baseData.distributionFrequency(), baseData.denomination(), productLink, stockexchangeLink,
        reportedCategoryType, reportedSii, chosen.idAssetClass(), contradictsChosenType(instrumentType, chosen),
        baseDataResult.status(), baseDataResult.detail(),
        baseData.providerAttributes(), alternatives.get(FeedSupport.FS_HISTORY), alternatives.get(FeedSupport.FS_INTRA),
        alternatives.get(FeedSupport.FS_DIVIDEND), alternatives.get(FeedSupport.FS_SPLIT),
        distributesNothing(baseData, alternatives.get(FeedSupport.FS_DIVIDEND)), listing.providerIds(), fieldSources);
  }

  /**
   * Tells whether the instrument distributes nothing, so that a dividend connector is pointless. The master data
   * decides first; otherwise no connector may have delivered a dividend and at least one must conclude "no
   * distribution" - a connector that merely failed proves nothing.
   */
  static boolean distributesNothing(SecurityBaseDataDto baseData, List<ConnectorAlternativeDto> dividendConnectors) {
    if (baseData.distributionFrequency() == DistributionFrequency.DF_NONE) {
      return true;
    }
    return dividendConnectors.stream().noneMatch(a -> a.probeStatus() == ConnectorProbeStatus.OK)
        && dividendConnectors.stream().anyMatch(a -> a.distributionFrequency() == DistributionFrequency.DF_NONE);
  }

  private SecurityBaseDataResult fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    return providers.stream().filter(p -> p.getProviderId().equals(candidate.providerId())).findFirst()
        .map(p -> p.getSecurityBaseData(candidate, locale)).orElseGet(() -> SecurityBaseDataResult
            .failure(candidate.providerId(), SecuritySearchStatus.UNSUPPORTED_QUERY, "Unknown provider"));
  }

  /** Keeps what the search delivered when the master data could not be fetched. */
  private static SecurityBaseDataDto fromSearchCandidate(SecuritySearchCandidateDto candidate, String mic) {
    return new SecurityBaseDataDto(candidate.providerId(), candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.hasEstablishedMic() ? candidate.mic() : mic, candidate.name(),
        candidate.providerSymbol(), null, null, null, null, null, null, null, null, null, Map.of());
  }

  private static Optional<InstrumentType> deriveInstrumentType(SecuritySearchCandidateDto candidate,
      SecurityBaseDataDto baseData) {
    return switch (candidate.providerId()) {
    case SixSecuritySearch.PROVIDER_ID -> SixInstrumentTypeMapper.map(candidate.providerInstrumentType(),
        baseData.providerAttributes());
    case XetraSecuritySearch.PROVIDER_ID -> XetraInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case OpenFigiSecuritySearch.PROVIDER_ID -> OpenFigiInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case ViennaSecuritySearch.PROVIDER_ID -> ViennaInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case EodhdSecuritySearch.PROVIDER_ID -> EodhdInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case EuronextSecuritySearch.PROVIDER_ID -> EuronextInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case OnvistaSecuritySearch.PROVIDER_ID -> OnvistaInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case BoursoramaSecuritySearch.PROVIDER_ID -> BoursoramaInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case ConsorsbankSecuritySearch.PROVIDER_ID ->
      ConsorsbankInstrumentTypeMapper.map(candidate.providerInstrumentType());
    case YahooHelper.YAHOO -> YahooInstrumentTypeMapper
        .map(baseData.providerAttributes().getOrDefault("instrumentType", candidate.providerInstrumentType()));
    default -> Optional.empty();
    };
  }

  /**
   * Collects and probes the connectors of every feed. A connector qualifies when it is activated, offers the feed for
   * securities, derives a URL extension or needs none, and supports exchange and the chosen type. The dividend and the
   * split feed are left out when they do not apply to the chosen asset class, e.g. a bond gets neither, because its
   * coupons are no dividends and it has no splits.
   *
   * @param baseData         master data of the listing, merged from all providers; the probes run on it
   * @param providerBaseData master data each provider of the listing delivered itself, for deriving URL extensions
   */
  private Map<FeedSupport, List<ConnectorAlternativeDto>> connectorAlternatives(SecurityBaseDataDto baseData,
      List<SecurityBaseDataDto> providerBaseData, String mic, Stockexchange stockexchange, AssetclassType categoryType,
      SpecialInvestmentInstruments sii, FeedApplicability applicable) {
    String countryCode = stockexchange == null ? null : stockexchange.getCountryCode();
    Map<FeedSupport, List<PendingProbe>> pending = new EnumMap<>(FeedSupport.class);
    long deadlineNanos = System.nanoTime() + PROBE_DEADLINE.toNanos();
    // Not closed by try-with-resources: close() would wait for a connector that ignores the interrupt.
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      for (FeedSupport feedSupport : FeedSupport.values()) {
        List<PendingProbe> feedProbes = new ArrayList<>();
        if (!isFeedExcluded(feedSupport, applicable)) {
          for (IFeedConnector connector : qualifyingConnectors(feedSupport, mic, countryCode, categoryType, sii)) {
            if (needsUrlExtension(connector, feedSupport)) {
              deriveUrlExtension(connector, baseData, providerBaseData, feedSupport)
                  .ifPresent(urlExtension -> feedProbes.add(startProbe(executor, connector, feedSupport, baseData,
                      urlExtension, stockexchange, categoryType, sii)));
            } else {
              feedProbes
                  .add(startProbe(executor, connector, feedSupport, baseData, null, stockexchange, categoryType, sii));
            }
          }
        }
        pending.put(feedSupport, feedProbes);
      }
      Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives = new EnumMap<>(FeedSupport.class);
      pending.forEach((feedSupport, feedProbes) -> alternatives.put(feedSupport,
          feedProbes.stream().map(p -> p.await(deadlineNanos)).toList()));
      return confirmEmptyEventFeeds(alternatives);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * An empty dividend or split answer is normal for an instrument without such events, but a provider answers an
   * unknown symbol the same way. When the same connector with the same URL extension was also probed for history or
   * intraday prices and none of these probes delivered data, the empty answer becomes {@code UNKNOWN_SYMBOL}, which
   * the dialog does not offer for selection. A connector without such a price probe, e.g. DivvyDiary, keeps
   * {@code NO_DATA}.
   *
   * @param alternatives the probed connectors per feed
   * @return the alternatives with the empty event probes of unknown symbols marked
   */
  static Map<FeedSupport, List<ConnectorAlternativeDto>> confirmEmptyEventFeeds(
      Map<FeedSupport, List<ConnectorAlternativeDto>> alternatives) {
    List<ConnectorAlternativeDto> priceProbes = new ArrayList<>();
    priceProbes.addAll(alternatives.getOrDefault(FeedSupport.FS_HISTORY, List.of()));
    priceProbes.addAll(alternatives.getOrDefault(FeedSupport.FS_INTRA, List.of()));
    Map<FeedSupport, List<ConnectorAlternativeDto>> confirmed = new EnumMap<>(FeedSupport.class);
    alternatives.forEach((feedSupport, feedAlternatives) -> confirmed.put(feedSupport,
        feedSupport != FeedSupport.FS_DIVIDEND && feedSupport != FeedSupport.FS_SPLIT ? feedAlternatives
            : feedAlternatives.stream().map(a -> isUnknownSymbol(a, priceProbes) ? asUnknownSymbol(a) : a).toList()));
    return confirmed;
  }

  private static boolean isUnknownSymbol(ConnectorAlternativeDto event, List<ConnectorAlternativeDto> priceProbes) {
    if (event.probeStatus() != ConnectorProbeStatus.NO_DATA || event.urlExtension() == null) {
      return false;
    }
    List<ConnectorAlternativeDto> samePrice = priceProbes.stream()
        .filter(p -> event.idConnector().equals(p.idConnector()) && event.urlExtension().equals(p.urlExtension()))
        .toList();
    return !samePrice.isEmpty() && samePrice.stream().noneMatch(p -> p.probeStatus() == ConnectorProbeStatus.OK);
  }

  private static ConnectorAlternativeDto asUnknownSymbol(ConnectorAlternativeDto a) {
    return new ConnectorAlternativeDto(a.idConnector(), a.readableName(), a.urlExtension(),
        ConnectorProbeStatus.UNKNOWN_SYMBOL, a.sampleDate(), a.sampleValue(), a.sampleCount(), a.distributionFrequency(),
        "No prices for this URL extension either");
  }

  /** Starts the probe of one connector on its own transient security. */
  private PendingProbe startProbe(ExecutorService executor, IFeedConnector connector, FeedSupport feedSupport,
      SecurityBaseDataDto baseData, String urlExtension, Stockexchange stockexchange, AssetclassType categoryType,
      SpecialInvestmentInstruments sii) {
    return new PendingProbe(connector, urlExtension,
        executor.submit(() -> connectorProbe.probe(connector, feedSupport,
            ConnectorProbe.draftSecurity(baseData, stockexchange, categoryType, sii), urlExtension,
            new ConnectorProbe.ProbeContext(baseData.listedSince(), baseData.distributing(), LocalDate.now()))));
  }

  /**
   * Derives the URL extension of a connector. The master data of the connector's own search provider come first - a
   * merged listing names only the primary provider, so the onvista notation or the Yahoo symbol would otherwise be lost
   * when that row was absorbed by an exchange provider; the merged master data follow, from which a connector such as
   * Yahoo builds an extension out of ticker and exchange; the master data of the other providers come last, for a
   * connector such as comdirect that has no search of its own but reads the notation of a Consorsbank or onvista
   * listing. Since the merged ticker is the first ticker any provider delivered, a ticker-based derivation gains
   * nothing from the last group. A derivation that fails on one source, e.g. because the exchange mapping cannot be
   * read, is logged and the next source is tried; a connector that derives from none is not offered.
   */
  private Optional<String> deriveUrlExtension(IFeedConnector connector, SecurityBaseDataDto baseData,
      List<SecurityBaseDataDto> providerBaseData, FeedSupport feedSupport) {
    List<SecurityBaseDataDto> sources = new ArrayList<>();
    providerBaseData.stream().filter(d -> isOwnProvider(connector, d.providerId())).forEach(sources::add);
    sources.add(baseData);
    providerBaseData.stream().filter(d -> !isOwnProvider(connector, d.providerId())).forEach(sources::add);
    for (SecurityBaseDataDto source : sources) {
      try {
        Optional<String> urlExtension = connector.deriveUrlExtension(source, feedSupport);
        if (urlExtension.isPresent()) {
          return urlExtension;
        }
      } catch (RuntimeException e) {
        // A failure on one source must not hide the others, e.g. the master data of another provider
        log.warn("URL extension of {} for {} could not be derived from {}: {}", connector.getID(), feedSupport,
            source.providerId(), e.toString());
      }
    }
    return Optional.empty();
  }

  /** Tells whether the search provider belongs to the same data source as the connector. */
  private boolean isOwnProvider(IFeedConnector connector, String providerId) {
    return providers.stream().filter(p -> p.getProviderId().equals(providerId))
        .anyMatch(p -> p.getFeedConnectorShortId().filter(id -> id.equals(connector.getShortID())).isPresent());
  }

  /**
   * Tells whether the connector needs a URL extension for the feed. A connector such as DivvyDiary identifies the
   * instrument by its ISIN alone and is offered without one.
   */
  private static boolean needsUrlExtension(IFeedConnector connector, FeedSupport feedSupport) {
    return Arrays.stream(connector.getSecuritycurrencyFeedSupport().get(feedSupport))
        .anyMatch(fi -> fi.name().endsWith("_URL"));
  }

  private static boolean isFeedExcluded(FeedSupport feedSupport, FeedApplicability applicable) {
    return feedSupport == FeedSupport.FS_DIVIDEND && !applicable.dividend()
        || feedSupport == FeedSupport.FS_SPLIT && !applicable.split();
  }

  private List<IFeedConnector> qualifyingConnectors(FeedSupport feedSupport, String mic, String countryCode,
      AssetclassType categoryType, SpecialInvestmentInstruments sii) {
    return feedConnectors.stream()
        .filter(fc -> fc.isActivated() && fc.supportsSecurity()
            && fc.getSecuritycurrencyFeedSupport().containsKey(feedSupport)
            && fc.supports(mic, countryCode, categoryType, sii))
        .sorted(Comparator.comparing(IFeedConnector::getReadableName, String::compareToIgnoreCase)).toList();
  }

  /** A probe started on a virtual thread; a probe that misses the deadline is reported, not dropped. */
  private record PendingProbe(IFeedConnector connector, String urlExtension, Future<ConnectorAlternativeDto> future) {

    ConnectorAlternativeDto await(long deadlineNanos) {
      try {
        return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.TIMEOUT,
            "Deadline of the lookup exceeded");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        future.cancel(true);
        return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, "Interrupted");
      } catch (ExecutionException e) {
        return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED,
            String.valueOf(e.getCause()));
      }
    }
  }
}
