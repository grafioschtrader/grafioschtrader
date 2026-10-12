package grafioschtrader.connector.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.securitysearch.FixtureHttpServer;
import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.SecurityBaseDataResult;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Yahoo master data from the {@code meta} block of recorded chart responses (AAPL, SAP.DE, NOVN.SW, SPY). */
class YahooBaseDataTest {

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private YahooSymbolSearch yahoo() {
    return new YahooSymbolSearch(mock(MicProviderMapRepository.class), server.baseUrl() + "v1/finance/search",
        server.baseUrl() + "v8/finance/chart/", Duration.ofSeconds(5));
  }

  private static SecuritySearchCandidateDto candidate(String symbol, String mic) {
    return new SecuritySearchCandidateDto(YahooHelper.YAHOO, symbol, null, null, null, IdentityEvidence.UNKNOWN, null,
        IdentityEvidence.UNKNOWN, mic, IdentityEvidence.RETURNED, "search name", "EQUITY");
  }

  @Test
  @DisplayName("Share: currency, long name, exchange ticker and first trade date clamped to GT's oldest trading day")
  void share() {
    server.fixture("/v8/finance/chart/SAP.DE", "yahoo/chart-sap-de.json");
    SecurityBaseDataDto data = yahoo().getSecurityBaseData(candidate("SAP.DE", "XETR"), Locale.GERMAN).data();
    assertEquals("EUR", data.currency());
    assertEquals("SAP SE", data.name());
    assertEquals("SAP", data.tickerSymbol(), "the exchange suffix is no part of the exchange ticker");
    assertEquals("SAP.DE", data.providerInstrumentId());
    assertEquals(LocalDate.of(1998, 4, 9), data.listedSince());
    assertEquals(LocalDate.of(2000, 1, 3), data.activeFromDate(), "no price before the oldest trading day");
    assertEquals("EQUITY", data.providerAttributes().get("instrumentType"));
    assertNull(data.productLink());
    assertNull(data.stockexchangeLink(), "Yahoo is no exchange");
  }

  @Test
  @DisplayName("Instrument types: EQUITY is a share, ETF keeps its asset class open, anything else stays open")
  void instrumentTypes() {
    server.fixture("/v8/finance/chart/SPY", "yahoo/chart-spy.json");
    SecurityBaseDataDto spy = yahoo().getSecurityBaseData(candidate("SPY", "ARCX"), Locale.ENGLISH).data();
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.ETF),
        YahooInstrumentTypeMapper.map(spy.providerAttributes().get("instrumentType")).orElseThrow());
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        YahooInstrumentTypeMapper.map("EQUITY").orElseThrow());
    assertTrue(YahooInstrumentTypeMapper.map("MUTUALFUND").isEmpty());
    assertTrue(YahooInstrumentTypeMapper.map(null).isEmpty());
  }

  @Test
  @DisplayName("A chart for another symbol is rejected; a candidate of another provider is not supported")
  void identity() {
    server.fixture("/v8/finance/chart/NOVN.SW", "yahoo/chart-aapl.json");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        yahoo().getSecurityBaseData(candidate("NOVN.SW", "XSWX"), Locale.GERMAN).status());
    SecuritySearchCandidateDto six = new SecuritySearchCandidateDto("six", "NOVN", "CH0012005267CHF4", null,
        "CH0012005267", IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX", IdentityEvidence.RETURNED,
        "NOVARTIS N", "BC");
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY, yahoo().getSecurityBaseData(six, Locale.GERMAN).status());
  }

  @Test
  @DisplayName("HTTP 429 and an empty result become statuses")
  void failures() {
    server.status("/v8/finance/chart/AAPL", 429, "");
    SecurityBaseDataResult limited = yahoo().getSecurityBaseData(candidate("AAPL", "XNAS"), Locale.ENGLISH);
    assertEquals(SecuritySearchStatus.RATE_LIMITED, limited.status());
    server.status("/v8/finance/chart/AAPL", 200, "{\"chart\":{\"result\":[],\"error\":null}}");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        yahoo().getSecurityBaseData(candidate("AAPL", "XNAS"), Locale.ENGLISH).status());
  }
}
