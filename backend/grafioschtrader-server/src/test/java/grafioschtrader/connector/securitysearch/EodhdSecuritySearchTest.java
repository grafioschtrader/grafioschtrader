package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.eodhistoricaldata.EodHistoricalDataConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * EODHD search against a recorded answer for SAP (DE0007164600), which EODHD lists on XETRA, F, STU, LSE and US, and
 * the URL extension the EODHD connector derives from it.
 */
class EodhdSecuritySearchTest {

  private static final String SAP = "DE0007164600";

  private FixtureHttpServer server;
  private MicProviderMapRepository repository;

  @BeforeEach
  void setUp() {
    server = new FixtureHttpServer();
    repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(EodhdSecuritySearch.PROVIDER_ID))
        .thenReturn(List.of(row("XETR", "XETRA"), row("XFRA", "F"), row("XSTU", "STU"), row("XLON", "LSE"),
            row("XNAS", "US"), row("XNYS", "US"), row("XSWX", "SW"), row("BIVA", "MX")));
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private static MicProviderMap row(String mic, String code) {
    return new MicProviderMap(EodhdSecuritySearch.PROVIDER_ID, mic, code, null);
  }

  private EodhdSecuritySearch eodhd(String apiKey) {
    return new EodhdSecuritySearch(repository, () -> apiKey, server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("Search: one listing per exchange code, placed on the queried MIC; ISIN and currency delivered")
  void search() {
    server.fixture("/search/" + SAP, "eodhd/search-sap.json");
    SecuritySearchResult result = eodhd("key").search(SecuritySearchQuery.of(SAP, "EUR", "XETR", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto xetra = result.candidates().stream().filter(c -> "XETR".equals(c.mic())).findFirst()
        .orElseThrow();
    assertEquals("SAP.XETRA", xetra.providerSymbol());
    assertEquals(IdentityEvidence.RETURNED, xetra.micEvidence());
    assertEquals(IdentityEvidence.RETURNED, xetra.isinEvidence());
    assertEquals("EUR", xetra.currency());
    assertTrue(server.requests().getFirst().contains("api_token=key"));

    SecurityBaseDataDto data = eodhd("key").getSecurityBaseData(xetra, Locale.GERMAN).data();
    assertEquals("SAP", data.tickerSymbol());
    assertEquals("SAP SE", data.name());
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        EodhdInstrumentTypeMapper.map(xetra.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("The collective code US is placed only on a queried US exchange; without API key no request")
  void exchangeMappingAndKey() {
    EodhdSecuritySearch.ExchangeMapping mapping = new EodhdSecuritySearch.ExchangeMapping(
        repository.findByIdProviderMic_IdProvider(EodhdSecuritySearch.PROVIDER_ID));
    assertEquals("XNYS", mapping.deriveMic("XNYS", "US"));
    assertNull(mapping.deriveMic(null, "US"), "US names several exchanges");
    assertEquals("XFRA", mapping.deriveMic("XETR", "F"), "another listing keeps its own MIC");
    assertNull(mapping.deriveMic("XETR", "PA"), "a code without row stays unknown");
    assertEquals("BIVA", mapping.deriveMic("BIVA", "MX"), "EODHD's Mexican code MX lands on GT's BIVA");

    assertFalse(eodhd(null).supports(SecuritySearchQuery.of(SAP, "EUR", "XETR", null)));
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY,
        eodhd(" ").search(SecuritySearchQuery.of(SAP, "EUR", "XETR", null)).status());
    assertTrue(server.requests().isEmpty());
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.ETF),
        EodhdInstrumentTypeMapper.map("ETF").orElseThrow());
    assertTrue(EodhdInstrumentTypeMapper.map("Preferred Stock").isEmpty());
  }

  @Test
  @DisplayName("Index list: found by ISIN where EODHD has one, otherwise by the code; placed on the queried MIC")
  void indexList() {
    String list = """
        [{"Code":"GDAXI","Name":"DAX Index","Country":"Germany","Exchange":"INDX","Currency":"EUR","Type":"INDEX","Isin":"DE0008469008"},
         {"Code":"OMXH25","Name":"OMX Helsinki 25","Country":"Finland","Exchange":"INDX","Currency":"EUR","Type":"INDEX","Isin":null}]""";
    EodhdSecuritySearch.ExchangeMapping mapping = new EodhdSecuritySearch.ExchangeMapping(
        repository.findByIdProviderMic_IdProvider(EodhdSecuritySearch.PROVIDER_ID));
    SecuritySearchCandidateDto dax = EodhdSecuritySearch
        .findIndex(list, SecuritySearchQuery.of("DE0008469008", "EUR", "XETR", null), mapping).getFirst();
    assertEquals("GDAXI.INDX", dax.providerSymbol());
    assertEquals("XETR", dax.mic());
    SecuritySearchCandidateDto omxh25 = EodhdSecuritySearch
        .findIndex(list, SecuritySearchQuery.of("FI0008900212", "EUR", "XHEL", "OMXH25"), mapping).getFirst();
    assertEquals("OMXH25.INDX", omxh25.providerSymbol());
    assertEquals("XHEL", omxh25.mic());
    assertTrue(EodhdSecuritySearch
        .findIndex(list, SecuritySearchQuery.of("FI0008900212", "EUR", "XHEL", null), mapping).isEmpty());
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES),
        EodhdInstrumentTypeMapper.map(EodhdSecuritySearch.TYPE_INDEX).orElseThrow());
  }

  @Test
  @DisplayName("The EODHD connector takes the EODHD symbol, or builds ticker.code for a listing of another provider")
  void connectorUrlExtension() {
    EodHistoricalDataConnector connector = new EodHistoricalDataConnector();
    ReflectionTestUtils.setField(connector, "micProviderMapRepository", repository);
    SecurityBaseDataDto fromEodhd = new SecurityBaseDataDto(EodhdSecuritySearch.PROVIDER_ID, "SAP.XETRA", SAP, "EUR",
        "XETR", "SAP SE", "SAP", null, null, null, null, null, null, null, null, null, Map.of());
    assertEquals(Optional.of("SAP.XETRA"), connector.deriveUrlExtension(fromEodhd, FeedSupport.FS_DIVIDEND));
    SecurityBaseDataDto fromSix = new SecurityBaseDataDto("six", "CH0012005267CHF4", "CH0012005267", "CHF", "XSWX",
        "NOVARTIS N", "NOVN", null, null, null, null, null, null, null, null, null, Map.of());
    assertEquals(Optional.of("NOVN.SW"), connector.deriveUrlExtension(fromSix, FeedSupport.FS_HISTORY));
    SecurityBaseDataDto berkshire = new SecurityBaseDataDto("yahoo", "BRK-B", "US0846707026", "USD", "XNYS", null,
        "BRK.B", null, null, null, null, null, null, null, null, null, Map.of());
    assertEquals(Optional.of("BRK-B.US"), connector.deriveUrlExtension(berkshire, FeedSupport.FS_HISTORY));
    SecurityBaseDataDto paris = new SecurityBaseDataDto("yahoo", "AF.PA", "FR0000031122", "EUR", "XPAR", null, "AF",
        null, null, null, null, null, null, null, null, null, Map.of());
    assertTrue(connector.deriveUrlExtension(paris, FeedSupport.FS_HISTORY).isEmpty(), "no code row for XPAR here");
    SecurityBaseDataDto mexico = new SecurityBaseDataDto("yahoo", "GMEXICOB.MX", "MXP370841019", "MXN", "BIVA", null,
        "GMEXICOB", null, null, null, null, null, null, null, null, null, Map.of());
    assertEquals(Optional.of("GMEXICOB.MX"), connector.deriveUrlExtension(mexico, FeedSupport.FS_HISTORY));
  }
}
