package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityLookupCandidateDto;
import grafioschtrader.dto.SecurityLookupResponse;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Choice of listing and connectors for the successor of an ISIN change, with a mocked lookup and mocked connectors so
 * that no request leaves the test.
 */
class SuccessorSecurityProposerTest {

  private static final String OLD_ISIN = "CH0012032048";
  private static final String NEW_ISIN = "CH1335392721";
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
  /** Older than the probe window of the lookup, so a connector delivering only recent prices does not reach it. */
  private static final LocalDate ACTION_DATE = TODAY.minusDays(90);
  private static final String SIX = "gt.datafeed.six";
  private static final String ALPHA = "gt.datafeed.alpha";
  private static final String BETA = "gt.datafeed.beta";
  private static final String DIVVY = "gt.datafeed.divvydiary";

  private SecurityLookupService securityLookupService;
  private SuccessorSecurityProposer proposer;
  private Security old;

  @BeforeEach
  void setUp() throws Exception {
    securityLookupService = mock(SecurityLookupService.class);
    // SIX and beta deliver prices from the date of the ISIN change on, alpha only the recent ones
    IFeedConnector six = historyConnector(SIX, true);
    IFeedConnector alpha = historyConnector(ALPHA, false);
    IFeedConnector beta = historyConnector(BETA, true);
    proposer = new SuccessorSecurityProposer(securityLookupService, mock(SecurityJpaRepository.class),
        new ConnectorProbe(), List.of(six, alpha, beta));
    old = oldSecurity();
  }

  @Test
  @DisplayName("The connector of the predecessor is kept, with the URL extension derived for the new ISIN")
  void sameConnectorWithNewUrlExtension() {
    lookupAnswers(true, candidate("Novartis AG", false,
        List.of(alternative(ALPHA, "A", ConnectorProbeStatus.OK), alternative(SIX, NEW_ISIN + "CHF4",
            ConnectorProbeStatus.OK)),
        List.of(alternative(DIVVY, NEW_ISIN, ConnectorProbeStatus.NO_DATA))));

    Security successor = propose();

    assertEquals(SIX, successor.getIdConnectorHistory());
    assertEquals(NEW_ISIN + "CHF4", successor.getUrlHistoryExtend());
    assertEquals(DIVVY, successor.getIdConnectorDividend());
    assertEquals(NEW_ISIN, successor.getUrlDividendExtend());
    assertEquals(NEW_ISIN, successor.getIsin());
    assertEquals(ACTION_DATE, successor.getActiveFromDate());
    assertEquals("Novartis AG", successor.getName());
  }

  @Test
  @DisplayName("Without a working predecessor connector the first one delivering prices from the change date is chosen")
  void firstConnectorDeliveringFromActionDate() {
    lookupAnswers(true, candidate("NOVARTIS N", false,
        List.of(alternative(SIX, NEW_ISIN + "CHF4", ConnectorProbeStatus.UNKNOWN_SYMBOL),
            alternative(ALPHA, "A", ConnectorProbeStatus.OK), alternative(BETA, "B", ConnectorProbeStatus.OK)),
        List.of()));

    Security successor = propose();

    assertEquals(BETA, successor.getIdConnectorHistory());
    assertEquals("B", successor.getUrlHistoryExtend());
    // A name written entirely in capitals is still better than the old name with a suffix
    assertEquals("NOVARTIS N", successor.getName());
  }

  @Test
  @DisplayName("An instrument that distributes nothing receives no dividend connector, not even the old one")
  void distributesNothingClearsDividendConnector() {
    lookupAnswers(true, candidate("Novartis AG", true,
        List.of(alternative(SIX, NEW_ISIN + "CHF4", ConnectorProbeStatus.OK)),
        List.of(alternative(DIVVY, NEW_ISIN, ConnectorProbeStatus.NO_DATA))));

    Security successor = propose();

    assertNull(successor.getIdConnectorDividend());
    assertNull(successor.getUrlDividendExtend());
  }

  @Test
  @DisplayName("Without a listing no proposal is made, so the predecessor is copied")
  void noListingNoProposal() {
    when(securityLookupService.lookup(anyString(), anyString(), anyString(), any(), any(Integer.class),
        any(Locale.class))).thenReturn(new SecurityLookupResponse(List.of(), List.of(), true, true, List.of()));

    assertTrue(proposer.propose(old, NEW_ISIN, ACTION_DATE, Locale.ENGLISH, TODAY).isEmpty());
  }

  @Test
  @DisplayName("The name of the predecessor marked as successor never exceeds the length of a security name")
  void successorNameFitsColumn() {
    String name = SuccessorSecurityProposer.successorName("x".repeat(80));
    assertEquals(80, name.length());
    assertTrue(name.endsWith(SuccessorSecurityProposer.NEW_ISIN_SUFFIX));
  }

  private Security propose() {
    Optional<Security> successor = proposer.propose(old, NEW_ISIN, ACTION_DATE, Locale.ENGLISH, TODAY);
    assertTrue(successor.isPresent());
    return successor.get();
  }

  private void lookupAnswers(boolean dividendApplicable, SecurityLookupCandidateDto candidate) {
    when(securityLookupService.lookup(eq(NEW_ISIN), eq("CHF"), eq("XSWX"), any(), eq(1), any(Locale.class)))
        .thenReturn(new SecurityLookupResponse(List.of(candidate), List.of(), dividendApplicable, true, List.of()));
  }

  /**
   * A history connector answering requests for the new ISIN. Starting on the date of the ISIN change it delivers a
   * price only when {@code fromActionDate} is set; the probe window of the last days is always served.
   */
  private static IFeedConnector historyConnector(String id, boolean fromActionDate) throws Exception {
    IFeedConnector connector = mock(IFeedConnector.class);
    when(connector.getID()).thenReturn(id);
    when(connector.getSecuritycurrencyFeedSupport(FeedSupport.FS_HISTORY))
        .thenReturn(new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    when(connector.getEodSecurityHistory(argThat(s -> s != null && NEW_ISIN.equals(s.getIsin())), any(), any()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          return from.equals(ACTION_DATE) && !fromActionDate ? List.of() : List.of(new Historyquote());
        });
    return connector;
  }

  private static Security oldSecurity() {
    Assetclass assetclass = new Assetclass();
    assetclass.setIdAssetClass(1);
    assetclass.setCategoryType(AssetclassType.EQUITIES);
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic("XSWX");
    Security security = new Security();
    security.setIsin(OLD_ISIN);
    security.setName("Novartis N");
    security.setCurrency("CHF");
    security.setTickerSymbol("NOVN");
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setIdConnectorHistory(SIX);
    security.setUrlHistoryExtend(OLD_ISIN + "CHF4");
    security.setIdConnectorDividend(DIVVY);
    security.setUrlDividendExtend(OLD_ISIN);
    return security;
  }

  private static ConnectorAlternativeDto alternative(String id, String urlExtension, ConnectorProbeStatus status) {
    return new ConnectorAlternativeDto(id, id, urlExtension, status, null, null, null, null, null, null);
  }

  private static SecurityLookupCandidateDto candidate(String name, boolean distributesNothing,
      List<ConnectorAlternativeDto> history, List<ConnectorAlternativeDto> dividend) {
    return new SecurityLookupCandidateDto("six", NEW_ISIN + "CHF4", "EQ", NEW_ISIN, "CHF", "XSWX", name, "NOVN", "CH",
        null, null, null, null, null, null, AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT, 1,
        false, null, null, Map.of(), history, List.of(), dividend, List.of(), distributesNothing, List.of("six"),
        Map.of(), List.of());
  }
}
