package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Boursorama against recorded search answers: TotalEnergies (FR0000120271), iShares Core MSCI World (IE00B4L5Y983),
 * Apple (US0378331005) and the OAT 3% 2033 (FR001400H7V7).
 */
class BoursoramaSecuritySearchTest {

  private static final List<MicProviderMap> ROWS = List.of(row("XPAR", "1rP", null), row("XAMS", "1rA", null),
      row("XETR", "1z", null), row("XLON", "1u", ".L"), row("XSWX", "2a", null), row("XMIL", "1g", null),
      row("XNAS", "US", null), row("XNYS", "US", null));

  private static MicProviderMap row(String mic, String code, String suffix) {
    return new MicProviderMap(BoursoramaSecuritySearch.PROVIDER_ID, mic, code, suffix);
  }

  private static SecuritySearchCandidateDto on(List<SecuritySearchCandidateDto> candidates, String mic) {
    return candidates.stream().filter(c -> mic.equals(c.mic())).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("Share: every market with a GT stock exchange; the prefix decides the MIC, the price the currency")
  void share() {
    List<SecuritySearchCandidateDto> candidates = BoursoramaSecuritySearch
        .parse(FixtureHttpServer.readFixture("boursorama/search-total.html"), ROWS);
    SecuritySearchCandidateDto paris = on(candidates, "XPAR");
    assertEquals("1rPTTE", paris.providerInstrumentId());
    assertEquals("TTE", paris.providerSymbol());
    assertEquals("EUR", paris.currency());
    assertEquals(IdentityEvidence.UNKNOWN, paris.isinEvidence(), "the full-text search repeats no ISIN");
    assertEquals("TTE", on(candidates, "XNYS").providerInstrumentId());
    assertEquals("USD", on(candidates, "XNYS").currency());
    assertEquals("CHF", on(candidates, "XSWX").currency());
    assertTrue(candidates.stream().noneMatch(c -> c.providerInstrumentId().startsWith("3r")), "no derivative");
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        BoursoramaInstrumentTypeMapper.map(paris.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("ETF on several markets and currencies; a bond quoted in percent has no currency")
  void etfAndBond() {
    List<SecuritySearchCandidateDto> etf = BoursoramaSecuritySearch
        .parse(FixtureHttpServer.readFixture("boursorama/search-iwda.html"), ROWS);
    assertEquals("1rAIWDA", on(etf, "XAMS").providerInstrumentId());
    assertEquals("1zEUNL", on(etf, "XETR").providerInstrumentId());
    assertEquals("1gSWDA", on(etf, "XMIL").providerInstrumentId());
    assertTrue(etf.stream().anyMatch(c -> "1uSWDA.L".equals(c.providerInstrumentId()) && "GBX".equals(c.currency())));
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.ETF),
        BoursoramaInstrumentTypeMapper.map(on(etf, "XAMS").providerInstrumentType()).orElseThrow());

    SecuritySearchCandidateDto oat = on(
        BoursoramaSecuritySearch.parse(FixtureHttpServer.readFixture("boursorama/search-oat.html"), ROWS), "XPAR");
    assertEquals("1rPFR001400H7V7", oat.providerInstrumentId());
    assertNull(oat.currency());
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        BoursoramaInstrumentTypeMapper.map(oat.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("The connector takes the Boursorama symbol, or builds prefix, ticker and suffix from another provider")
  void connectorUrlExtension() {
    MicProviderMapRepository repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(BoursoramaSecuritySearch.PROVIDER_ID)).thenReturn(ROWS);
    BoursoramaFeedConnector connector = new BoursoramaFeedConnector();
    ReflectionTestUtils.setField(connector, "micProviderMapRepository", repository);
    SecuritySearchCandidateDto apple = on(
        BoursoramaSecuritySearch.parse(FixtureHttpServer.readFixture("boursorama/search-apple.html"), ROWS), "XNAS");
    assertEquals("AAPL", apple.providerInstrumentId());
    assertEquals(Optional.of("1zSAP"), connector.deriveUrlExtension(
        base("six", "SAP", "XETR"), FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("2aNESN"), connector.deriveUrlExtension(base("six", "NESN", "XSWX"), FeedSupport.FS_INTRA));
    assertEquals(Optional.of("1uTTE.L"), connector.deriveUrlExtension(base("yahoo", "TTE", "XLON"),
        FeedSupport.FS_HISTORY));
    assertEquals(Optional.of("MSFT"), connector.deriveUrlExtension(base("yahoo", "MSFT", "XNAS"),
        FeedSupport.FS_HISTORY));
    assertTrue(connector.deriveUrlExtension(base("vienna", "ANDR", "XVIE"), FeedSupport.FS_HISTORY).isEmpty());
    SecurityBaseDataDto own = new SecurityBaseDataDto(BoursoramaSecuritySearch.PROVIDER_ID, "1rPTTE", null, "EUR",
        "XPAR", "TOTALENERGIES", "TTE", null, null, null, null, null, null, null, null, null, Map.of());
    assertEquals(Optional.of("1rPTTE"), connector.deriveUrlExtension(own, FeedSupport.FS_HISTORY));
    assertNull(BoursoramaSecuritySearch.currency("92,1200 %"));
    assertEquals("CAD", BoursoramaSecuritySearch.currency("243,830 CAD"));
  }

  private static SecurityBaseDataDto base(String provider, String ticker, String mic) {
    return new SecurityBaseDataDto(provider, null, null, null, mic, null, ticker, null, null, null, null, null, null,
        null, null, null, Map.of());
  }
}
