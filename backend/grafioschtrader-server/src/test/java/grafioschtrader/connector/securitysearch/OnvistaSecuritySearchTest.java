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
import grafioschtrader.connector.instrument.onvista.OnvistaFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * onvista against recorded answers: SAP (DE0007164600) on Xetra and the regional exchanges, and the US Treasury 4%
 * 2024/34 (US91282CJZ59), whose bond snapshot carries its terms.
 */
class OnvistaSecuritySearchTest {

  private static final String SAP = "DE0007164600";
  private static final String UST = "US91282CJZ59";

  private FixtureHttpServer server;
  private MicProviderMapRepository repository;

  @BeforeEach
  void setUp() {
    server = new FixtureHttpServer();
    repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(OnvistaSecuritySearch.PROVIDER_ID)).thenReturn(List.of(
        row("XETR", "GER"), row("XFRA", "FRA"), row("XSTU", "STU"), row("XMUN", "MUN"), row("XSWX", "SWX")));
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private static MicProviderMap row(String mic, String code) {
    return new MicProviderMap(OnvistaSecuritySearch.PROVIDER_ID, mic, code, null);
  }

  private OnvistaSecuritySearch onvista() {
    return new OnvistaSecuritySearch(repository, server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("Share: one candidate per mapped venue; the instrument id is the URL extension of the connector")
  void share() {
    server.fixture("/instruments/query?q=", "onvista/query-sap.json")
        .fixture("/instruments/query", "onvista/query-sap.json")
        .fixture("/instruments/STOCK/82849/snapshot", "onvista/snapshot-sap.json");
    SecuritySearchResult result = onvista().search(SecuritySearchQuery.of(SAP, "EUR", "XSTU", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto stuttgart = result.candidates().stream().filter(c -> "XSTU".equals(c.mic()))
        .findFirst().orElseThrow();
    assertTrue(stuttgart.providerInstrumentId().startsWith("STOCK/82849/eod_history?idNotation="));
    assertEquals("SAP", stuttgart.providerSymbol());
    assertEquals("EUR", stuttgart.currency());
    assertTrue(result.candidates().stream().noneMatch(c -> c.mic() == null), "Tradegate, gettex have no GT MIC");

    SecurityBaseDataDto data = onvista().getSecurityBaseData(stuttgart, Locale.GERMAN).data();
    assertEquals(Optional.of(stuttgart.providerInstrumentId()),
        new OnvistaFeedConnector().deriveUrlExtension(data, FeedSupport.FS_HISTORY));
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        OnvistaInstrumentTypeMapper.map(stuttgart.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("Bond: WKN as ticker; maturity, issue date, nominal, semi-annual coupon and issuer country")
  void bond() {
    server.fixture("/instruments/query", "onvista/query-ust.json")
        .fixture("/bonds/ISIN:" + UST + "/snapshot", "onvista/bond-snapshot-ust.json");
    SecuritySearchCandidateDto frankfurt = onvista().search(SecuritySearchQuery.of(UST, "USD", "XFRA", null))
        .candidates().stream().filter(c -> "XFRA".equals(c.mic())).findFirst().orElseThrow();
    assertEquals("A3LUB5", frankfurt.providerSymbol());
    assertEquals("USD", frankfurt.currency());
    SecurityBaseDataDto data = onvista().getSecurityBaseData(frankfurt, Locale.GERMAN).data();
    assertEquals(LocalDate.of(2034, 2, 15), data.activeToDate());
    assertEquals(LocalDate.of(2024, 2, 15), data.activeFromDate());
    assertEquals(100, data.denomination());
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, data.distributionFrequency());
    assertEquals("US", data.issuerCountry());
  }

  @Test
  @DisplayName("Without a matching instrument no candidate; foreign master data gives no onvista URL extension")
  void noMatchAndForeignData() {
    assertNull(OnvistaSecuritySearch.findInstrument("{\"list\":[{\"isin\":\"DE0007164600\",\"entityType\":\"WARRANT\""
        + ",\"entityValue\":\"1\"}]}", UST));
    assertTrue(OnvistaSecuritySearch.parseSnapshot("{\"instrument\":{\"isin\":\"" + SAP + "\"}}", SAP, Map.of())
        .isEmpty());
    SecurityBaseDataDto yahoo = new SecurityBaseDataDto("yahoo", "SAP.DE", SAP, "EUR", "XETR", null, "SAP", null, null,
        null, null, null, null, null, null, null, Map.of());
    assertTrue(new OnvistaFeedConnector().deriveUrlExtension(yahoo, FeedSupport.FS_HISTORY).isEmpty());
    assertEquals(DistributionFrequency.DF_YEAR, OnvistaSecuritySearch.couponFrequency("Jahr"));
    assertNull(OnvistaSecuritySearch.couponFrequency("unbekannt"));
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND),
        OnvistaInstrumentTypeMapper.map("FUND").orElseThrow());
  }
}
