package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Master data of the SIX adapter against detail responses recorded without any key: the Givaudan bond GIV21, the
 * Invesco ETF MWEQ and the Novartis share.
 */
class SixSecurityBaseDataTest {

  private static final String BOND_PATH = "/sheldon/bond_details/v3/CH1148308724CHF4/details/info.json";
  private static final String FUND_PATH = "/sheldon/fund_details/v2/IE000OEF25S1CHF4/fund/info.json";
  private static final String SHARE_PATH = "/sheldon/share_details/v3/CH0012005267CHF4/share/info.json";

  private static final SecuritySearchCandidateDto BOND = candidate("CH1148308724", "GIV21", "0.125 GIV 21-27", "DB");
  private static final SecuritySearchCandidateDto FUND = candidate("IE000OEF25S1", "MWEQ", "Invesco MSCI World EW Acc",
      "ET");
  private static final SecuritySearchCandidateDto SHARE = candidate("CH0012005267", "NOVN", "NOVARTIS N", "BC");

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private SixSecuritySearch six() {
    return new SixSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  private static SecuritySearchCandidateDto candidate(String isin, String symbol, String name, String productLine) {
    return new SecuritySearchCandidateDto(SixSecuritySearch.PROVIDER_ID, symbol, isin + "CHF4", null, isin,
        IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX", IdentityEvidence.RETURNED, name,
        productLine);
  }

  @Test
  @DisplayName("Bond: denomination, coupon frequency, issue and maturity become GT fields")
  void bond() {
    server.fixture(BOND_PATH, "six/bond-details.json");
    SecurityBaseDataResult result = six().getSecurityBaseData(BOND, Locale.GERMAN);
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecurityBaseDataDto data = result.data();
    assertEquals("0.125 GIV 21-27", data.name());
    assertEquals("GIV21", data.tickerSymbol());
    assertEquals(5000, data.denomination());
    assertEquals(DistributionFrequency.DF_YEAR, data.distributionFrequency());
    assertEquals(LocalDate.of(2021, 12, 3), data.activeFromDate());
    assertEquals(LocalDate.of(2027, 6, 7), data.activeToDate());
    assertEquals("CH", data.issuerCountry());
    assertEquals("https://www.six-group.com/de/market-data/bonds/bond-explorer/bond-details.CH1148308724CHF4.html#/",
        data.stockexchangeLink(), "the SIX page is the stock exchange hyperlink, in the user's language");
    assertNull(data.productLink(), "SIX is the exchange, not the issuer");
    assertEquals("0.125", data.providerAttributes().get("couponInfo.couponRate"));
    assertEquals("DB", data.providerAttributes().get("productLine"));
    assertFalse(data.providerAttributes().containsKey("smallestDenomination"), "mapped values are not repeated");
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        SixInstrumentTypeMapper.map("DB", data.providerAttributes()).orElseThrow());
  }

  @Test
  @DisplayName("Bond of a foreign issuer (product line FB): read from the bond endpoint like a domestic bond")
  void foreignBond() {
    server.fixture(BOND_PATH, "six/bond-details.json");
    SecurityBaseDataDto data = six()
        .getSecurityBaseData(candidate("CH1148308724", "GIV21", "0.125 GIV 21-27", "FB"), Locale.GERMAN).data();
    assertEquals(LocalDate.of(2027, 6, 7), data.activeToDate());
    assertEquals(5000, data.denomination());
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        SixInstrumentTypeMapper.map("FB", data.providerAttributes()).orElseThrow());
  }

  @Test
  @DisplayName("ETF: domicile becomes the issuer country, an accumulating fund distributes nothing")
  void fund() {
    server.fixture(FUND_PATH, "six/fund-info.json");
    SecurityBaseDataDto data = six().getSecurityBaseData(FUND, Locale.ENGLISH).data();
    assertEquals("MWEQ", data.tickerSymbol());
    assertEquals("https://www.six-group.com/en/market-data/etf/etf-explorer/etf-detail.IE000OEF25S1CHF4.html#/",
        data.stockexchangeLink());
    assertEquals("IE", data.issuerCountry());
    assertNull(data.activeFromDate(), "the first trading day of a fund listing is not that of the fund");
    assertEquals(Boolean.FALSE, data.distributing(), "dividendEntitlement 0 marks an accumulating fund");
    assertEquals(DistributionFrequency.DF_NONE, data.distributionFrequency());
    assertEquals("Equity Strategy", data.providerAttributes().get("general.assetClass"));
    assertEquals(LocalDate.of(2024, 10, 23), data.listedSince(),
        "the listing date is kept apart from the first active day");
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF),
        SixInstrumentTypeMapper.map("ET", data.providerAttributes()).orElseThrow());
  }

  @Test
  @DisplayName("Share: firstTradingDate is only the listing date, countryCode stays a raw attribute")
  void share() {
    server.fixture(SHARE_PATH, "six/share-info.json");
    SecurityBaseDataDto data = six().getSecurityBaseData(SHARE, Locale.ITALIAN).data();
    assertEquals("NOVN", data.tickerSymbol());
    assertEquals("https://www.six-group.com/en/market-data/shares/share-explorer/share-details.CH0012005267CHF4.html#/",
        data.stockexchangeLink(), "a language other than German or English gets the English page");
    assertNull(data.activeFromDate());
    assertNull(data.issuerCountry());
    assertEquals(LocalDate.of(2020, 12, 7), data.listedSince());
    assertFalse(data.providerAttributes().containsKey("firstTradingDate"));
    assertEquals("US", data.providerAttributes().get("countryCode"));
    assertEquals("SMI,SMIEXP,SLI", data.providerAttributes().get("indexSymbols"));
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        SixInstrumentTypeMapper.map("BC", data.providerAttributes()).orElseThrow());
  }

  @Test
  @DisplayName("A detail answer contradicting the candidate is rejected; foreign candidates are not supported")
  void identityAndUnsupported() {
    server.fixture(SHARE_PATH, "six/share-info.json");
    SecuritySearchCandidateDto usd = new SecuritySearchCandidateDto(SixSecuritySearch.PROVIDER_ID, "NOVN",
        "CH0012005267CHF4", null, "CH0012005267", IdentityEvidence.RETURNED, "USD", IdentityEvidence.RETURNED, "XSWX",
        IdentityEvidence.RETURNED, "NOVARTIS N", "BC");
    assertEquals(SecuritySearchStatus.NO_MATCH, six().getSecurityBaseData(usd, Locale.GERMAN).status());
    SecuritySearchCandidateDto yahoo = new SecuritySearchCandidateDto("yahoo", "NOVN.SW", null, "EBS", null,
        IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, "XSWX", IdentityEvidence.RETURNED, "Novartis",
        "EQUITY");
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY, six().getSecurityBaseData(yahoo, Locale.GERMAN).status());
  }

  @Test
  @DisplayName("HTTP 429, an empty item list and a timeout become statuses")
  void failures() {
    server.status(BOND_PATH, 429, "");
    assertEquals(SecuritySearchStatus.RATE_LIMITED, six().getSecurityBaseData(BOND, Locale.GERMAN).status());
    server.status(BOND_PATH, 200, "{\"status\":\"Ok\",\"totalCount\":0,\"itemList\":[]}");
    assertEquals(SecuritySearchStatus.NO_MATCH, six().getSecurityBaseData(BOND, Locale.GERMAN).status());
    server.delayed(BOND_PATH, 1500);
    SecurityBaseDataResult timeout = new SixSecuritySearch(server.baseUrl(), Duration.ofMillis(300))
        .getSecurityBaseData(BOND, Locale.GERMAN);
    assertEquals(SecuritySearchStatus.TIMEOUT, timeout.status());
    assertTrue(timeout.status().isTransient());
  }

  @Test
  @DisplayName("Type mapping stays open for unknown combinations; coupon frequencies without GT counterpart stay open")
  void mapperAndFrequency() {
    assertEquals(
        new InstrumentType(null, SpecialInvestmentInstruments.ETF), SixInstrumentTypeMapper
            .map("ET", Map.of("general.secType", "EF", "general.assetClass", "Alternative")).orElseThrow(),
        "an unknown fund asset class keeps the financial instrument only");
    assertTrue(
        SixInstrumentTypeMapper.map("ET", Map.of("general.secType", "EP", "general.assetClass", "Equity")).isEmpty());
    assertTrue(SixInstrumentTypeMapper.map("BC", Map.of()).isEmpty());
    assertTrue(SixInstrumentTypeMapper.map(null, Map.of()).isEmpty());
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, SixSecuritySearch.couponFrequency("2"));
    assertNull(SixSecuritySearch.couponFrequency("0"));
    assertNull(SixSecuritySearch.couponFrequency(null));
    assertEquals(Boolean.TRUE, SixSecuritySearch.fundDistributing("1"));
    assertEquals(Boolean.FALSE, SixSecuritySearch.fundDistributing("0"));
    assertNull(SixSecuritySearch.fundDistributing(""));
  }
}
