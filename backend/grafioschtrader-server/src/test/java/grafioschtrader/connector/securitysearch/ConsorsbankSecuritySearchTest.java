package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.consorsbank.ConsorsbankFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Consorsbank against answers recorded on 2026-10-11: adidas (DE000A1EWWW0) on Xetra, the regional exchanges and SIX,
 * Apple (US0378331005) as Nasdaq Basic and on SIX in two currencies, the iShares Core MSCI World ETF (IE00B4L5Y983)
 * and the German federal bond 0.5% 2018/28 (DE0001102440) with its terms.
 */
class ConsorsbankSecuritySearchTest {

  private static final String ADIDAS = "DE000A1EWWW0";
  private static final String APPLE = "US0378331005";
  private static final String IWDA = "IE00B4L5Y983";
  private static final String BUND = "DE0001102440";

  private FixtureHttpServer server;
  private MicProviderMapRepository repository;

  @BeforeEach
  void setUp() {
    server = new FixtureHttpServer();
    repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(ConsorsbankSecuritySearch.PROVIDER_ID))
        .thenReturn(List.of(row("XETR", "GER"), row("XFRA", "FRA"), row("XSTU", "STU"), row("XMUN", "MUN"),
            row("XSWX", "SWX"), row("XAMS", "AMS"), row("XNAS", "NSB"), row("XNYS", "NSB")));
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private static MicProviderMap row(String mic, String code) {
    return new MicProviderMap(ConsorsbankSecuritySearch.PROVIDER_ID, mic, code, null);
  }

  /** GT MICs per Consorsbank code as the provider reads them from the repository. */
  private static final Map<String, List<String>> MICS_BY_CODE = Map.of("GER", List.of("XETR"), "FRA", List.of("XFRA"),
      "STU", List.of("XSTU"), "MUN", List.of("XMUN"), "SWX", List.of("XSWX"), "AMS", List.of("XAMS"), "NSB",
      List.of("XNAS", "XNYS"));

  private ConsorsbankSecuritySearch consorsbank() {
    return new ConsorsbankSecuritySearch(repository, server.baseUrl(), Duration.ofSeconds(5));
  }

  private static SecuritySearchCandidateDto onMic(List<SecuritySearchCandidateDto> candidates, String mic,
      String currency) {
    return candidates.stream().filter(c -> mic.equals(c.mic()) && currency.equals(c.currency())).findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("Share: one candidate per mapped venue; notation and code are the URL extension of the connector")
  void share() {
    List<SecuritySearchCandidateDto> all = ConsorsbankSecuritySearch
        .parseSearch(FixtureHttpServer.readFixture("consorsbank/search-adidas.json"), ADIDAS, "XETR", MICS_BY_CODE);
    assertEquals(List.of("XETR", "XFRA", "XMUN", "XSTU", "XSWX"),
        all.stream().map(SecuritySearchCandidateDto::mic).sorted().toList(),
        "Tradegate, gettex and the off-exchange quotes have no GT MIC");
    assertNull(onMic(all, "XSWX", "CHF").providerSymbol(), "no exchange ticker outside Germany");

    server.fixture("/stocks", "consorsbank/search-adidas.json");
    SecuritySearchResult result = consorsbank().search(SecuritySearchQuery.of(ADIDAS, "EUR", "XETR", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto xetra = onMic(result.candidates(), "XETR", "EUR");
    assertEquals("_39471840,GER", xetra.providerInstrumentId());
    assertEquals("ADS", xetra.providerSymbol(), "the German symbol is the ticker on a German venue");

    SecurityBaseDataDto data = consorsbank().getSecurityBaseData(xetra, Locale.GERMAN).data();
    assertEquals("DE", data.issuerCountry());
    assertEquals("A1EWWW", data.providerAttributes().get("wkn"));
    assertEquals(Optional.of("_39471840,GER"),
        new ConsorsbankFeedConnector().deriveUrlExtension(data, FeedSupport.FS_HISTORY));
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        ConsorsbankInstrumentTypeMapper.map(xetra.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("US share: Nasdaq Basic lands on the queried US exchange; SIX carries a notation per currency")
  void usShare() {
    String body = FixtureHttpServer.readFixture("consorsbank/search-apple.json");
    List<SecuritySearchCandidateDto> nyse = ConsorsbankSecuritySearch.parseSearch(body, APPLE, "XNYS", MICS_BY_CODE);
    assertEquals("_67728090,NSB", onMic(nyse, "XNYS", "USD").providerInstrumentId());
    assertTrue(nyse.stream().noneMatch(c -> "XNAS".equals(c.mic())));
    assertTrue(ConsorsbankSecuritySearch.parseSearch(body, APPLE, "XSWX", MICS_BY_CODE).stream()
        .noneMatch(c -> "NSB".equals(c.providerExchangeCode())), "NSB is placed only on a queried US exchange");
    List<SecuritySearchCandidateDto> nasdaq = ConsorsbankSecuritySearch.parseSearch(body, APPLE, "XNAS",
        MICS_BY_CODE);
    assertEquals("_67728090,NSB", onMic(nasdaq, "XNAS", "USD").providerInstrumentId());
    assertNull(onMic(nasdaq, "XNAS", "USD").providerSymbol());
    assertEquals("_167496668,SWX", onMic(nasdaq, "XSWX", "USD").providerInstrumentId());
    assertEquals("_116588269,SWX", onMic(nasdaq, "XSWX", "CHF").providerInstrumentId());
  }

  @Test
  @DisplayName("ETF: fund without asset class type, its domicile is the country")
  void etf() {
    server.fixture("/stocks", "consorsbank/search-iwda.json").fixture("/etfs", "consorsbank/etf-iwda.json");
    SecuritySearchCandidateDto amsterdam = onMic(
        consorsbank().search(SecuritySearchQuery.of(IWDA, "EUR", "XAMS", null)).candidates(), "XAMS", "EUR");
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.ETF),
        ConsorsbankInstrumentTypeMapper.map(amsterdam.providerInstrumentType()).orElseThrow());
    assertEquals("IE", consorsbank().getSecurityBaseData(amsterdam, Locale.GERMAN).data().issuerCountry());
  }

  @Test
  @DisplayName("Bond: WKN as ticker on a German venue; maturity, issue date, annual coupon and issuer country")
  void bond() {
    server.fixture("/stocks", "consorsbank/search-bund.json").fixture("/bonds", "consorsbank/bond-bund.json");
    SecuritySearchCandidateDto frankfurt = onMic(
        consorsbank().search(SecuritySearchQuery.of(BUND, "EUR", "XFRA", null)).candidates(), "XFRA", "EUR");
    assertEquals("110244", frankfurt.providerSymbol());
    SecurityBaseDataDto data = consorsbank().getSecurityBaseData(frankfurt, Locale.GERMAN).data();
    assertEquals(LocalDate.of(2028, 2, 15), data.activeToDate());
    assertEquals(LocalDate.of(2018, 1, 12), data.activeFromDate());
    assertNull(data.denomination(), "a nominal value of 0.01 is no denomination");
    assertEquals(DistributionFrequency.DF_YEAR, data.distributionFrequency());
    assertEquals("DE", data.issuerCountry());
    assertEquals("0.5", data.providerAttributes().get("coupon"));
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        ConsorsbankInstrumentTypeMapper.map(frankfurt.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("An unknown ISIN gives no candidate; foreign master data gives no Consorsbank URL extension")
  void unknownAndForeignData() {
    String unknown = "[{\"BasicV1\":{\"ID\":{\"ISIN\":null}},\"ExchangesV2\":[],\"Info\":{\"ID\":\"XX0000000000\"}}]";
    assertTrue(ConsorsbankSecuritySearch.parseSearch(unknown, "XX0000000000", null, Map.of()).isEmpty());
    SecurityBaseDataDto yahoo = new SecurityBaseDataDto("yahoo", "ADS.DE", ADIDAS, "EUR", "XETR", null, "ADS", null,
        null, null, null, null, null, null, null, null, Map.of());
    assertTrue(new ConsorsbankFeedConnector().deriveUrlExtension(yahoo, FeedSupport.FS_HISTORY).isEmpty());
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND),
        ConsorsbankInstrumentTypeMapper.map("FUN").orElseThrow());
    assertTrue(ConsorsbankInstrumentTypeMapper.map("CER").isEmpty());
    assertEquals("funds", ConsorsbankSecuritySearch.masterDataEndpoint("FUN"));
    assertEquals("stocks", ConsorsbankSecuritySearch.masterDataEndpoint(null));
  }
}
