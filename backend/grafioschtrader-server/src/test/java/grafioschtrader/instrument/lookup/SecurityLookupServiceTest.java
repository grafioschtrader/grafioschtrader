package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.DividendHistoryResult;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.euronext.EuronextFeedConnector;
import grafioschtrader.connector.instrument.six.SixFeedConnector;
import grafioschtrader.connector.securitysearch.ISecuritySearchProvider;
import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.SearchCandidateMatcher;
import grafioschtrader.connector.securitysearch.SecurityBaseDataResult;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.connector.securitysearch.SecuritySearchService;
import grafioschtrader.connector.securitysearch.SixSecuritySearch;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecurityLookupCandidateDto;
import grafioschtrader.dto.SecurityLookupHint;
import grafioschtrader.dto.SecurityLookupHint.Severity;
import grafioschtrader.dto.SecurityLookupResponse;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Merging of search, master data, type derivation and connector probes, with a fake provider and mocked connectors so
 * that no request leaves the test.
 */
class SecurityLookupServiceTest {

  private static final String BOND_ISIN = "CH1148308724";
  private static final String BOND_VALOR_ID = "CH1148308724CHF4";

  private AssetclassJpaRepository assetclassJpaRepository;
  private StockexchangeJpaRepository stockexchangeJpaRepository;
  private SecurityBaseDataResult baseDataResult;
  /** SIX product line of the fake candidates: DB bond by default */
  private String productLine = "DB";

  @BeforeEach
  void setUp() {
    assetclassJpaRepository = mock(AssetclassJpaRepository.class);
    stockexchangeJpaRepository = mock(StockexchangeJpaRepository.class);
    when(stockexchangeJpaRepository.findByMic(anyString())).thenReturn(Optional.empty());
    baseDataResult = SecurityBaseDataResult.ok(SixSecuritySearch.PROVIDER_ID,
        new SecurityBaseDataDto(SixSecuritySearch.PROVIDER_ID, BOND_VALOR_ID, BOND_ISIN, "CHF", "XSWX",
            "0.125 GIV 21-27", "GIV21", "CH", LocalDate.of(2021, 12, 3), LocalDate.of(2027, 6, 7),
            DistributionFrequency.DF_YEAR, 5000, null, "https://example.org", LocalDate.of(2021, 12, 3), null,
            Map.of("securityType", "BO")));
  }

  /** A SIX provider answering with the bond in CHF and in EUR, without any network access. */
  private ISecuritySearchProvider fakeSix() {
    return new ISecuritySearchProvider() {
      @Override
      public String getProviderId() {
        return SixSecuritySearch.PROVIDER_ID;
      }

      @Override
      public boolean supports(SecuritySearchQuery query) {
        return true;
      }

      @Override
      public SecuritySearchResult search(SecuritySearchQuery query) {
        return new SecuritySearchResult(getProviderId(), SecuritySearchStatus.OK,
            List.of(candidate("CHF", productLine), candidate("EUR", productLine)), 0, null);
      }

      @Override
      public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
        return true;
      }

      @Override
      public SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
        return baseDataResult;
      }
    };
  }

  private static SecuritySearchCandidateDto candidate(String currency, String productLine) {
    return new SecuritySearchCandidateDto(SixSecuritySearch.PROVIDER_ID, "GIV21", BOND_ISIN + currency + "4", null,
        BOND_ISIN, IdentityEvidence.RETURNED, currency, IdentityEvidence.RETURNED, "XSWX", IdentityEvidence.RETURNED,
        "0.125 GIV 21-27", productLine);
  }

  private static IFeedConnector connector(String id, boolean activated, FeedSupport... feeds) throws Exception {
    IFeedConnector connector = mock(IFeedConnector.class);
    when(connector.getID()).thenReturn("gt.datafeed." + id);
    when(connector.getReadableName()).thenReturn(id);
    when(connector.isActivated()).thenReturn(activated);
    when(connector.supportsSecurity()).thenReturn(true);
    when(connector.supports(any(), any(), any(), any())).thenReturn(true);
    Map<FeedSupport, FeedIdentifier[]> feedSupport = new EnumMap<>(FeedSupport.class);
    for (FeedSupport fs : feeds) {
      feedSupport.put(fs, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
      when(connector.deriveUrlExtension(any(), eq(fs))).thenReturn(Optional.of(BOND_VALOR_ID));
    }
    when(connector.getSecuritycurrencyFeedSupport()).thenReturn(feedSupport);
    return connector;
  }

  private static Assetclass assetclass(int id, AssetclassType categoryType, SpecialInvestmentInstruments sii) {
    Assetclass assetclass = new Assetclass(categoryType, sii, "Sub", "Sub");
    assetclass.setIdAssetClass(id);
    return assetclass;
  }

  private static final Assetclass BONDS = assetclass(7, AssetclassType.FIXED_INCOME,
      SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  private static final Assetclass SHARES = assetclass(3, AssetclassType.EQUITIES,
      SpecialInvestmentInstruments.DIRECT_INVESTMENT);

  private SecurityLookupService service(List<IFeedConnector> connectors) {
    ISecuritySearchProvider six = fakeSix();
    return new SecurityLookupService(new SecuritySearchService(List.of(six)), List.of(six), connectors,
        new ConnectorProbe(), assetclassJpaRepository, stockexchangeJpaRepository);
  }

  @Test
  @DisplayName("Bond: one candidate in the queried currency, probes give statuses, no dividend connector")
  void bondLookup() throws Exception {
    IFeedConnector six = connector("six", true, FeedSupport.FS_HISTORY, FeedSupport.FS_INTRA, FeedSupport.FS_DIVIDEND);
    Historyquote quote = new Historyquote();
    quote.setDate(LocalDate.of(2026, 10, 9));
    quote.setClose(99.5);
    when(six.getEodSecurityHistory(any(), any(), any())).thenReturn(List.of(quote));
    doThrow(new IOException("connection reset")).when(six).updateSecurityLastPrice(any());
    IFeedConnector inactive = connector("inactive", false, FeedSupport.FS_HISTORY);

    SecurityLookupResponse response = service(List.of(six, inactive)).lookup(BOND_ISIN, "CHF", "XSWX", null, BONDS,
        Locale.GERMAN);

    assertEquals(1, response.candidates().size(), "the EUR listing does not match the queried currency");
    SecurityLookupCandidateDto candidate = response.candidates().getFirst();
    assertEquals(AssetclassType.FIXED_INCOME, candidate.categoryType());
    assertEquals(7, candidate.matchedAssetClassId(), "the chosen asset class");
    assertFalse(candidate.instrumentTypeMismatch());
    assertEquals(5000, candidate.denomination());
    ConnectorAlternativeDto history = candidate.historyConnectors().getFirst();
    assertEquals(1, candidate.historyConnectors().size(), "an inactive connector is never offered");
    assertEquals(ConnectorProbeStatus.OK, history.probeStatus());
    assertEquals(99.5, history.sampleValue());
    assertEquals(BOND_VALOR_ID, history.urlExtension());
    assertEquals(ConnectorProbeStatus.REQUEST_FAILED, candidate.intraConnectors().getFirst().probeStatus());
    assertTrue(candidate.dividendConnectors().isEmpty(), "a bond gets no dividend connector");
    assertFalse(response.dividendConnectorApplicable());
    assertFalse(response.splitConnectorApplicable(), "a directly held bond has no splits");
    assertFalse(candidate.distributesNothing(), "a bond pays coupons");
    assertTrue(candidate.splitConnectors().isEmpty());
    assertTrue(candidate.hints().isEmpty(), "SIX delivers every field the form needs for a bond");
    assertTrue(response.hints().isEmpty());
  }

  @Test
  @DisplayName("A provider type contradicting the chosen asset class flags the listing; failed master data keeps values")
  void mismatchAndFailedBaseData() throws Exception {
    SecurityLookupCandidateDto candidate = service(List.of())
        .lookup(BOND_ISIN, "CHF", "XSWX", null, SHARES, Locale.GERMAN).candidates().getFirst();
    assertTrue(candidate.instrumentTypeMismatch(), "SIX lists a bond, shares were chosen");
    assertEquals(3, candidate.matchedAssetClassId());
    assertEquals(List.of(LookupHints.TYPE_MISMATCH), hintKeys(candidate.hints()));

    baseDataResult = SecurityBaseDataResult.failure(SixSecuritySearch.PROVIDER_ID, SecuritySearchStatus.TIMEOUT,
        "slow");
    candidate = service(List.of()).lookup(BOND_ISIN, "CHF", "XSWX", null, BONDS, Locale.GERMAN).candidates().getFirst();
    assertEquals(SecuritySearchStatus.TIMEOUT, candidate.baseDataStatus());
    assertEquals("0.125 GIV 21-27", candidate.name());
    assertEquals("GIV21", candidate.tickerSymbol());
    assertEquals(AssetclassType.FIXED_INCOME, candidate.categoryType(), "a bond product line maps without details");
    assertEquals(
        List.of(LookupHints.BASE_DATA_FAILED, LookupHints.ACTIVE_FROM_MISSING, LookupHints.ACTIVE_TO_MISSING,
            LookupHints.DISTRIBUTION_FREQUENCY_MISSING, LookupHints.DENOMINATION_MISSING),
        hintKeys(candidate.hints()), "the search values lack every date and the bond fields; the warnings come first");
    assertEquals(Map.of("provider", "six", "statusKey", "TIMEOUT"), candidate.hints().getFirst().args());
  }

  private static List<String> hintKeys(List<SecurityLookupHint> hints) {
    return hints.stream().map(SecurityLookupHint::messageKey).toList();
  }

  /**
   * Chegg is listed on the NYSE. A Yahoo search answers its ISIN with the NYSE listing only, which the identity rules
   * reject when NASDAQ was queried - as happened when the security was created on XNAS.
   */
  private static ISecuritySearchProvider fakeYahooChegg() {
    return new ISecuritySearchProvider() {
      @Override
      public String getProviderId() {
        return "yahoo";
      }

      @Override
      public boolean supports(SecuritySearchQuery query) {
        return true;
      }

      @Override
      public SecuritySearchResult search(SecuritySearchQuery query) {
        return SearchCandidateMatcher.match(getProviderId(), query, List.of(yahooCandidate("CHGG", "XNYS")));
      }

      @Override
      public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
        return true;
      }

      @Override
      public SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
        throw new AssertionError("a rejected listing gets no master data request");
      }
    };
  }

  @Test
  @DisplayName("Wrong exchange: no candidate, but an error hint names the exchange on which Yahoo found the instrument")
  void instrumentOnlyOnOtherExchange() throws Exception {
    ISecuritySearchProvider yahoo = fakeYahooChegg();
    SecurityLookupResponse response = new SecurityLookupService(new SecuritySearchService(List.of(yahoo)),
        List.of(yahoo), List.of(), new ConnectorProbe(), assetclassJpaRepository, stockexchangeJpaRepository)
            .lookup("US1630921096", "USD", "XNAS", null, SHARES, Locale.GERMAN);

    assertTrue(response.candidates().isEmpty());
    assertEquals(1, response.hints().size());
    SecurityLookupHint hint = response.hints().getFirst();
    assertEquals(Severity.ERROR, hint.severity(), "without any candidate the chosen exchange is probably wrong");
    assertEquals(LookupHints.OTHER_MIC, hint.messageKey());
    assertEquals(Map.of("mic", "XNYS", "queriedMic", "XNAS", "providers", "yahoo"), hint.args());
  }

  @Test
  @DisplayName("Share: a dividend connector without URL extension is offered and derives the distribution frequency")
  void dividendConnectorWithoutUrlExtension() throws Exception {
    productLine = "BC";
    baseDataResult = SecurityBaseDataResult.ok(SixSecuritySearch.PROVIDER_ID,
        new SecurityBaseDataDto(SixSecuritySearch.PROVIDER_ID, "CH0012005267CHF4", "CH0012005267", "CHF", "XSWX",
            "NOVARTIS N", "NOVN", null, null, null, null, null, null, null, LocalDate.of(2020, 12, 7), null,
            Map.of("securityType", "RS")));
    IFeedConnector divvy = mock(IFeedConnector.class);
    when(divvy.getID()).thenReturn("gt.datafeed.divvydiary");
    when(divvy.getReadableName()).thenReturn("DivvyDiary");
    when(divvy.isActivated()).thenReturn(true);
    when(divvy.supportsSecurity()).thenReturn(true);
    when(divvy.supports(any(), any(), any(), any())).thenReturn(true);
    when(divvy.getSecuritycurrencyFeedSupport())
        .thenReturn(Map.of(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND }));
    List<Dividend> dividends = new ArrayList<>();
    for (int year = LocalDate.now().getYear() - 5; year <= LocalDate.now().getYear(); year++) {
      dividends
          .add(new Dividend(null, LocalDate.of(year, 3, 10), null, 3.5, null, "CHF", CreateType.CONNECTOR_CREATED));
    }
    when(divvy.getDividendHistoryWithFrequency(any(), any())).thenReturn(new DividendHistoryResult(dividends, null));

    SecurityLookupCandidateDto candidate = service(List.of(divvy))
        .lookup("CH0012005267", "CHF", "XSWX", null, SHARES, Locale.GERMAN).candidates().getFirst();
    assertEquals(AssetclassType.EQUITIES, candidate.categoryType());
    ConnectorAlternativeDto dividend = candidate.dividendConnectors().getFirst();
    assertNull(dividend.urlExtension());
    assertEquals(ConnectorProbeStatus.OK, dividend.probeStatus());
    assertEquals(DistributionFrequency.DF_YEAR, dividend.distributionFrequency());
    assertEquals(3.5, dividend.sampleValue());
    assertFalse(candidate.distributesNothing());

    when(divvy.getDividendHistoryWithFrequency(any(), any()))
        .thenReturn(new DividendHistoryResult(dividends, DistributionFrequency.DF_SEMI_ANNUAL));
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL,
        service(List.of(divvy)).lookup("CH0012005267", "CHF", "XSWX", null, SHARES, Locale.GERMAN).candidates()
            .getFirst().dividendConnectors().getFirst().distributionFrequency(),
        "a frequency stated by the provider takes precedence over the derivation");

    when(divvy.getDividendHistoryWithFrequency(any(), any())).thenReturn(new DividendHistoryResult(List.of(), null));
    assertTrue(service(List.of(divvy)).lookup("CH0012005267", "CHF", "XSWX", null, SHARES, Locale.GERMAN).candidates()
        .getFirst().distributesNothing(), "no dividend in five years of a listing older than a year");
  }

  /**
   * A Yahoo provider answering an ISIN search with listings on XNAS, on an unmapped exchange and in another currency.
   */
  private static ISecuritySearchProvider fakeYahoo() {
    return new ISecuritySearchProvider() {
      @Override
      public String getProviderId() {
        return "yahoo";
      }

      @Override
      public boolean supports(SecuritySearchQuery query) {
        return true;
      }

      @Override
      public SecuritySearchResult search(SecuritySearchQuery query) {
        return new SecuritySearchResult("yahoo", SecuritySearchStatus.OK,
            List.of(yahooCandidate("AAPL", "XNAS"), yahooCandidate("AAPL.MX", null), yahooCandidate("AAPLX", "XNAS")),
            0, null);
      }

      @Override
      public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
        return true;
      }

      @Override
      public SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
        String currency = "AAPLX".equals(candidate.providerSymbol()) ? "EUR" : "USD";
        return SecurityBaseDataResult.ok("yahoo",
            new SecurityBaseDataDto("yahoo", candidate.providerSymbol(), null, currency, candidate.mic(), "Apple Inc.",
                candidate.providerSymbol(), null, LocalDate.of(2000, 1, 3), null, null, null, null, null,
                LocalDate.of(1980, 12, 12), null, Map.of("instrumentType", "EQUITY")));
      }
    };
  }

  private static SecuritySearchCandidateDto yahooCandidate(String symbol, String mic) {
    return new SecuritySearchCandidateDto("yahoo", symbol, null, null, null, IdentityEvidence.UNKNOWN, null,
        IdentityEvidence.UNKNOWN, mic, mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, "Apple",
        "EQUITY");
  }

  @Test
  @DisplayName("Yahoo: only the listing on the queried exchange in the queried currency; probes get the queried ISIN")
  void yahooListing() throws Exception {
    ISecuritySearchProvider yahoo = fakeYahoo();
    IFeedConnector divvy = mock(IFeedConnector.class);
    when(divvy.getID()).thenReturn("gt.datafeed.divvydiary");
    when(divvy.getReadableName()).thenReturn("DivvyDiary");
    when(divvy.isActivated()).thenReturn(true);
    when(divvy.supportsSecurity()).thenReturn(true);
    when(divvy.supports(any(), any(), any(), any())).thenReturn(true);
    when(divvy.getSecuritycurrencyFeedSupport())
        .thenReturn(Map.of(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND }));
    when(divvy.getDividendHistoryWithFrequency(any(), any())).thenAnswer(inv -> {
      assertEquals("US0378331005", ((grafioschtrader.entities.Security) inv.getArgument(0)).getIsin());
      return new DividendHistoryResult(List.of(), DistributionFrequency.DF_QUARTERLY);
    });
    SecurityLookupService service = new SecurityLookupService(new SecuritySearchService(List.of(yahoo)), List.of(yahoo),
        List.of(divvy), new ConnectorProbe(), assetclassJpaRepository, stockexchangeJpaRepository);

    List<SecurityLookupCandidateDto> candidates = service
        .lookup("US0378331005", "USD", "XNAS", null, SHARES, Locale.GERMAN).candidates();
    assertEquals(1, candidates.size(), "an unmapped exchange and another currency are other listings");
    SecurityLookupCandidateDto apple = candidates.getFirst();
    assertEquals("AAPL", apple.tickerSymbol());
    assertEquals(AssetclassType.EQUITIES, apple.categoryType());
    assertEquals(List.of("yahoo"), apple.providerIds());
    assertEquals("https://www.nasdaq.com/de/market-activity/stocks/aapl", apple.stockexchangeLink());
    assertEquals(SecurityLookupService.DERIVED_SOURCE, apple.fieldSources().get("stockexchangeLink"));
    assertEquals(DistributionFrequency.DF_QUARTERLY, apple.dividendConnectors().getFirst().distributionFrequency());
  }

  @Test
  @DisplayName("A connector keeps the instrument id of its own provider when that listing is merged into another")
  void ownProviderDataAfterMerge() throws Exception {
    ISecuritySearchProvider six = fakeSix();
    SecurityBaseDataDto onvistaData = new SecurityBaseDataDto("onvista", "BOND/1/eod_history?idNotation=9", BOND_ISIN,
        "CHF", "XSWX", "GIV 21-27", "GIV21", null, null, null, null, null, null, null, null, null, Map.of());
    ISecuritySearchProvider onvista = new ISecuritySearchProvider() {
      @Override
      public String getProviderId() {
        return "onvista";
      }

      @Override
      public Optional<String> getFeedConnectorShortId() {
        return Optional.of("onvista");
      }

      @Override
      public boolean supports(SecuritySearchQuery query) {
        return true;
      }

      @Override
      public SecuritySearchResult search(SecuritySearchQuery query) {
        return new SecuritySearchResult("onvista", SecuritySearchStatus.OK,
            List.of(new SecuritySearchCandidateDto("onvista", "GIV21", "BOND/1/eod_history?idNotation=9", "SWX",
                BOND_ISIN, IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX",
                IdentityEvidence.RETURNED, "GIV 21-27", "BOND")),
            0, null);
      }

      @Override
      public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
        return true;
      }

      @Override
      public SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
        return SecurityBaseDataResult.ok("onvista", onvistaData);
      }
    };
    IFeedConnector onvistaConnector = connector("onvista", true, FeedSupport.FS_HISTORY);
    when(onvistaConnector.getShortID()).thenReturn("onvista");
    when(onvistaConnector.deriveUrlExtension(any(), eq(FeedSupport.FS_HISTORY))).thenAnswer(inv -> {
      SecurityBaseDataDto data = inv.getArgument(0);
      return "onvista".equals(data.providerId()) ? Optional.of(data.providerInstrumentId()) : Optional.empty();
    });
    // A connector without a provider of its own, like comdirect, reads the notation of the onvista listing
    IFeedConnector comdirectConnector = connector("comdirect", true, FeedSupport.FS_HISTORY);
    when(comdirectConnector.getShortID()).thenReturn("comdirect");
    when(comdirectConnector.deriveUrlExtension(any(), eq(FeedSupport.FS_HISTORY))).thenAnswer(inv -> {
      SecurityBaseDataDto data = inv.getArgument(0);
      return "onvista".equals(data.providerId()) ? Optional.of("notation " + data.providerInstrumentId())
          : Optional.empty();
    });
    SecurityLookupService service = new SecurityLookupService(new SecuritySearchService(List.of(six, onvista)),
        List.of(six, onvista), List.of(onvistaConnector, comdirectConnector), new ConnectorProbe(),
        assetclassJpaRepository, stockexchangeJpaRepository);

    SecurityLookupCandidateDto candidate = service.lookup(BOND_ISIN, "CHF", "XSWX", null, BONDS, Locale.GERMAN)
        .candidates().getFirst();
    assertEquals(List.of("six", "onvista"), candidate.providerIds(), "the onvista row is merged into the SIX row");
    Map<String, String> extensions = candidate.historyConnectors().stream()
        .collect(Collectors.toMap(ConnectorAlternativeDto::idConnector, ConnectorAlternativeDto::urlExtension));
    assertEquals("BOND/1/eod_history?idNotation=9", extensions.get("gt.datafeed.onvista"));
    assertEquals("notation BOND/1/eod_history?idNotation=9", extensions.get("gt.datafeed.comdirect"),
        "the master data of the other providers of the row are passed as well");
  }

  @Test
  @DisplayName("A provider is asked only when the price connector of its data source supports the chosen type")
  void providerCoverageByCompanionConnector() throws Exception {
    ISecuritySearchProvider six = fakeSix();
    ISecuritySearchProvider sixWithCompanion = new ISecuritySearchProvider() {
      @Override
      public String getProviderId() {
        return six.getProviderId();
      }

      @Override
      public Optional<String> getFeedConnectorShortId() {
        return Optional.of("six");
      }

      @Override
      public boolean supports(SecuritySearchQuery query) {
        return true;
      }

      @Override
      public SecuritySearchResult search(SecuritySearchQuery query) {
        return six.search(query);
      }

      @Override
      public SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
        return six.getSecurityBaseData(candidate, locale);
      }
    };
    IFeedConnector sixConnector = connector("six", false, FeedSupport.FS_HISTORY);
    when(sixConnector.getShortID()).thenReturn("six");
    when(sixConnector.supports(any(), any(), eq(AssetclassType.FIXED_INCOME), any())).thenReturn(false);
    SecurityLookupService service = new SecurityLookupService(new SecuritySearchService(List.of(sixWithCompanion)),
        List.of(sixWithCompanion), List.of(sixConnector), new ConnectorProbe(), assetclassJpaRepository,
        stockexchangeJpaRepository);

    SecurityLookupResponse bonds = service.lookup(BOND_ISIN, "CHF", "XSWX", null, BONDS, Locale.GERMAN);
    assertTrue(bonds.providerStatus().isEmpty(), "the connector does not support bonds, so SIX is not asked");
    assertTrue(bonds.candidates().isEmpty());

    SecurityLookupResponse shares = service.lookup(BOND_ISIN, "CHF", "XSWX", null, SHARES, Locale.GERMAN);
    assertEquals(1, shares.providerStatus().size(), "an inactive connector still declares the coverage");
  }

  private static ConnectorAlternativeDto alternative(String id, String urlExtension, ConnectorProbeStatus status) {
    return new ConnectorAlternativeDto(id, id, urlExtension, status, null, null, 0, null, null);
  }

  @Test
  @DisplayName("An empty dividend or split answer counts only when the same symbol delivered prices")
  void emptyEventFeedsNeedKnownSymbol() {
    Map<FeedSupport, List<ConnectorAlternativeDto>> probed = new EnumMap<>(FeedSupport.class);
    probed.put(FeedSupport.FS_HISTORY,
        List.of(alternative("gt.datafeed.yahoo", "GMEXICOB", ConnectorProbeStatus.REQUEST_FAILED),
            alternative("gt.datafeed.six", "CH1CHF4", ConnectorProbeStatus.OK)));
    probed.put(FeedSupport.FS_INTRA,
        List.of(alternative("gt.datafeed.yahoo", "GMEXICOB", ConnectorProbeStatus.NO_DATA)));
    probed.put(FeedSupport.FS_DIVIDEND,
        List.of(alternative("gt.datafeed.yahoo", "GMEXICOB", ConnectorProbeStatus.NO_DATA),
            alternative("gt.datafeed.six", "CH1CHF4", ConnectorProbeStatus.NO_DATA),
            alternative("gt.datafeed.divvydiary", null, ConnectorProbeStatus.NO_DATA)));
    probed.put(FeedSupport.FS_SPLIT,
        List.of(alternative("gt.datafeed.yahoo", "GMEXICOB.MX", ConnectorProbeStatus.NO_DATA)));

    Map<FeedSupport, List<ConnectorAlternativeDto>> confirmed = SecurityLookupService.confirmEmptyEventFeeds(probed);
    List<ConnectorAlternativeDto> dividend = confirmed.get(FeedSupport.FS_DIVIDEND);
    assertEquals(ConnectorProbeStatus.UNKNOWN_SYMBOL, dividend.get(0).probeStatus(),
        "Yahoo delivered no prices either");
    assertEquals(ConnectorProbeStatus.NO_DATA, dividend.get(1).probeStatus(), "SIX delivered prices for the symbol");
    assertEquals(ConnectorProbeStatus.NO_DATA, dividend.get(2).probeStatus(), "DivvyDiary has no price probe");
    assertEquals(ConnectorProbeStatus.NO_DATA, confirmed.get(FeedSupport.FS_SPLIT).getFirst().probeStatus(),
        "another URL extension was not price-probed");
    assertEquals(probed.get(FeedSupport.FS_HISTORY), confirmed.get(FeedSupport.FS_HISTORY));
  }

  @Test
  @DisplayName("Dividend and split feeds follow the rules of the security form for the chosen asset class")
  void feedApplicability() {
    assertEquals(new SecurityLookupService.FeedApplicability(false, false),
        SecurityLookupService.feedApplicability(new SecurityLookupService.ChosenType(1, AssetclassType.FIXED_INCOME,
            SpecialInvestmentInstruments.DIRECT_INVESTMENT), null));
    assertEquals(new SecurityLookupService.FeedApplicability(true, true),
        SecurityLookupService.feedApplicability(new SecurityLookupService.ChosenType(1, AssetclassType.EQUITIES,
            SpecialInvestmentInstruments.DIRECT_INVESTMENT), null));
    assertEquals(new SecurityLookupService.FeedApplicability(true, true), SecurityLookupService.feedApplicability(
        new SecurityLookupService.ChosenType(1, AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF), null));
    Stockexchange privatePapers = new Stockexchange();
    privatePapers.setNoMarketValue(true);
    assertEquals(new SecurityLookupService.FeedApplicability(false, false),
        SecurityLookupService.feedApplicability(new SecurityLookupService.ChosenType(1, AssetclassType.EQUITIES,
            SpecialInvestmentInstruments.DIRECT_INVESTMENT), privatePapers));
  }

  @Test
  @DisplayName("Funds are compatible with each other; an unknown asset class type contradicts nothing")
  void contradictsChosenType() {
    SecurityLookupService.ChosenType etf = new SecurityLookupService.ChosenType(1, AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.ETF);
    assertFalse(SecurityLookupService.contradictsChosenType(Optional.empty(), etf));
    assertFalse(SecurityLookupService
        .contradictsChosenType(Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND)), etf));
    assertFalse(SecurityLookupService.contradictsChosenType(
        Optional.of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF)), etf));
    assertTrue(SecurityLookupService.contradictsChosenType(
        Optional.of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF)), etf));
    assertTrue(SecurityLookupService.contradictsChosenType(
        Optional.of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT)), etf));
  }

  @Test
  @DisplayName("The SIX connector derives the ValorId of SIX master data, otherwise ISIN + currency + 4")
  void sixUrlExtension() {
    SixFeedConnector connector = new SixFeedConnector();
    SecurityBaseDataDto fromSix = baseDataResult.data();
    assertEquals(Optional.of(BOND_VALOR_ID), connector.deriveUrlExtension(fromSix, FeedSupport.FS_HISTORY));
    assertEquals(Optional.of(BOND_VALOR_ID), connector.deriveUrlExtension(fromSix, FeedSupport.FS_DIVIDEND));
    assertTrue(connector.deriveUrlExtension(fromSix, FeedSupport.FS_SPLIT).isEmpty());
    SecurityBaseDataDto fromOther = new SecurityBaseDataDto("yahoo", "NOVN.SW", "CH0012005267", "CHF", "XSWX", null,
        null, null, null, null, null, null, null, null, null, null, null);
    assertEquals(Optional.of("CH0012005267CHF4"), connector.deriveUrlExtension(fromOther, FeedSupport.FS_INTRA));
  }

  @Test
  @DisplayName("A URL extension rejected by the connector check is reported as regex failure")
  void regexFailure() {
    IFeedConnector connector = mock(IFeedConnector.class);
    when(connector.getID()).thenReturn("gt.datafeed.x");
    doThrow(new GeneralNotTranslatedWithArgumentsException("gt.connector.regex.url", new Object[] { "x", "^$" }))
        .when(connector).checkAndClearSecuritycurrencyUrlExtend(any(), any());
    ConnectorAlternativeDto result = new ConnectorProbe().probe(connector, FeedSupport.FS_HISTORY,
        ConnectorProbe.draftSecurity(baseDataResult.data(), null, null, null), "bad",
        new ConnectorProbe.ProbeContext(null, null, LocalDate.now()));
    assertEquals(ConnectorProbeStatus.REGEX_FAILED, result.probeStatus());

    doThrow(new GeneralNotTranslatedWithArgumentsException("gt.connector.euronext.setting.failure", null))
        .when(connector).checkAndClearSecuritycurrencyUrlExtend(any(), any());
    result = new ConnectorProbe().probe(connector, FeedSupport.FS_HISTORY,
        ConnectorProbe.draftSecurity(baseDataResult.data(), null, null, null), null,
        new ConnectorProbe.ProbeContext(null, null, LocalDate.now()));
    assertEquals(ConnectorProbeStatus.CHECK_FAILED, result.probeStatus(), "a rejection other than the URL pattern");
  }

  @Test
  @DisplayName("Euronext is offered only on the exchanges its setting check accepts")
  void euronextCoverage() {
    EuronextFeedConnector euronext = new EuronextFeedConnector();
    assertFalse(
        euronext.supports("XFRA", "DE", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    assertTrue(
        euronext.supports("XPAR", "FR", AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    assertTrue(euronext.supports("XMSM", "IE", AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF));
  }
}
