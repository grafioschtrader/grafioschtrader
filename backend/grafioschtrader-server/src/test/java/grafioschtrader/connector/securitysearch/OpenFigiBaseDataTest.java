package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** OpenFIGI master data is the search hit itself; no request is sent. */
class OpenFigiBaseDataTest {

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  @Test
  @DisplayName("adidas in Frankfurt: Bloomberg ticker, FIGI and type from the hit, without a request")
  void baseDataFromHit() {
    SecuritySearchCandidateDto adidas = new SecuritySearchCandidateDto(OpenFigiSecuritySearch.PROVIDER_ID, "ADS",
        "BBG000BBNWG1", "GF", "DE000A1EWWW0", IdentityEvidence.FILTERED, "EUR", IdentityEvidence.FILTERED, "XFRA",
        IdentityEvidence.FILTERED, "ADIDAS AG", "Common Stock");
    SecurityBaseDataResult result = new OpenFigiSecuritySearch(server.baseUrl(), Duration.ofSeconds(5))
        .getSecurityBaseData(adidas, Locale.GERMAN);
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecurityBaseDataDto data = result.data();
    assertEquals("ADS", data.tickerSymbol());
    assertEquals("XFRA", data.mic());
    assertEquals("GF", data.providerAttributes().get("exchCode"));
    assertEquals("BBG000BBNWG1", data.providerAttributes().get("figi"));
    assertTrue(server.requests().isEmpty(), "master data needs no request");
  }

  @Test
  @DisplayName("Types: Common Stock is a share, Corp and Govt are bonds, Mutual Fund (ETF or ETC) stays open")
  void types() {
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        OpenFigiInstrumentTypeMapper.map("Common Stock").orElseThrow());
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        OpenFigiInstrumentTypeMapper.map("Govt").orElseThrow());
    assertTrue(OpenFigiInstrumentTypeMapper.map("Mutual Fund").isEmpty());
    assertTrue(OpenFigiInstrumentTypeMapper.map(null).isEmpty());
  }
}
