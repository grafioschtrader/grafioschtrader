package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.xetra.XetraFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Master data of Deutsche Börse listings from recorded price information - SAP on Xetra and Bunds in Frankfurt - and
 * the signed bond terms of the live API.
 */
class XetraSecurityBaseDataTest {

  private static final String PRICE_PATH = "/live/data/price_information/single";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private XetraSecuritySearch xetra() {
    return new XetraSecuritySearch(server.baseUrl(), server.baseUrl() + "live/", server.baseUrl() + "site/",
        Duration.ofSeconds(5));
  }

  private static SecuritySearchCandidateDto candidate(String isin, String mic, String name, String type) {
    return new SecuritySearchCandidateDto(XetraSecuritySearch.PROVIDER_ID, mic + ":" + isin, null,
        "XETR".equals(mic) ? "Xetra" : "Frankfurt", isin, IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN,
        mic, IdentityEvidence.RETURNED, name, type);
  }

  @Test
  @DisplayName("Share on Xetra: currency, name, listing page with the ISIN; no ticker from Deutsche Börse")
  void share() {
    server.fixture(PRICE_PATH, "xetra/price-information-sap.json");
    SecurityBaseDataDto data = xetra()
        .getSecurityBaseData(candidate("DE0007164600", "XETR", "SAP SE", "Aktie"), Locale.GERMAN).data();
    assertEquals("EUR", data.currency());
    assertEquals("SAP SE", data.name());
    assertNull(data.tickerSymbol());
    assertEquals("https://live.deutsche-boerse.com/aktie/DE0007164600?mic=XETR", data.stockexchangeLink());
    assertEquals("false", data.providerAttributes().get("tradedInPercent"));
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        XetraInstrumentTypeMapper.map("Aktie").orElseThrow());
  }

  @Test
  @DisplayName("Bond in Frankfurt: traded in percent, bond page; ETP stays open")
  void bond() {
    server.fixture(PRICE_PATH, "xetra/price-information-bond.json");
    SecurityBaseDataDto data = xetra().getSecurityBaseData(
        candidate("DE0001102580", "XFRA", "Deutschland, Bundesrepublik 0,000% 22/32", "Anleihe"), Locale.GERMAN).data();
    assertEquals("true", data.providerAttributes().get("tradedInPercent"));
    assertEquals("https://live.deutsche-boerse.com/anleihe/DE0001102580?mic=XFRA", data.stockexchangeLink());
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        XetraInstrumentTypeMapper.map("Anleihe").orElseThrow());
    assertTrue(XetraInstrumentTypeMapper.map("ETP").isEmpty(), "an ETP may be an ETF, ETC or ETN");
  }

  @Test
  @DisplayName("Bond terms: signed requests with the salt of the site give maturity, first day and coupon frequency")
  void bondTerms() {
    server.fixture(PRICE_PATH, "xetra/price-information-bund-0440.json")
        .fixture("/site/", "xetra/site-home.html").fixture("/site/main.19b2a10d0231cd30.js", "xetra/site-main.js")
        .fixture("/live/data/master_data_bond", "xetra/master-data-bond.json")
        .fixture("/live/data/interest_rate_widget", "xetra/interest-rate-bond.json")
        .fixture("/live/data/data_sheet_header", "xetra/data-sheet-header-bond.json");
    SecurityBaseDataDto data = xetra().getSecurityBaseData(
        candidate("DE0001102440", "XFRA", "Deutschland, Bundesrepublik 0,5% 18/28", "Anleihe"), Locale.GERMAN).data();
    assertEquals(LocalDate.of(2028, 2, 15), data.activeToDate());
    assertEquals(LocalDate.of(2018, 1, 10), data.activeFromDate());
    assertEquals(LocalDate.of(2018, 1, 10), data.listedSince());
    assertEquals(DistributionFrequency.DF_YEAR, data.distributionFrequency());
    assertNull(data.denomination(), "a minimum investment of 0.01 is no whole denomination");
    assertEquals("110244", data.tickerSymbol(), "the WKN is the ticker of a bond");
    assertEquals("Deutschland, Bundesrepublik", data.providerAttributes().get("issuer"));
    assertTrue(server.requests().stream().filter(r -> r.startsWith("/site/main.")).count() == 1,
        "the salt is read once and cached");

    server.status("/live/data/master_data_bond", 200, "{}").status("/live/data/interest_rate_widget", 200, "{}");
    SecurityBaseDataDto rejected = xetra().getSecurityBaseData(
        candidate("DE0001102440", "XFRA", "Deutschland, Bundesrepublik 0,5% 18/28", "Anleihe"), Locale.GERMAN).data();
    assertNull(rejected.activeToDate(), "a rejected signature keeps the price information");
  }

  @Test
  @DisplayName("Signature headers as the script of the site builds them; terms of another ISIN are ignored")
  void signatureAndForeignTerms() {
    Map<String, String> headers = XetraSecuritySearch.signatureHeaders(
        "https://api.live.deutsche-boerse.com/v1/data/master_data_bond?isin=DE0001102440",
        "af5a8d16eb5dc49f8a72b26fd9185475c7a",
        ZonedDateTime.of(2026, 10, 11, 14, 5, 9, 0, XetraSecuritySearch.SIGNING_ZONE));
    assertEquals("2026-10-11T14:05:09+02:00", headers.get("Client-Date"));
    assertEquals("3628d146e46f150aa93896e38d1a88df", headers.get("X-Client-TraceId"));
    assertEquals("d7e5b08701b6f547b50c24695e472580", headers.get("X-Security"));
    assertEquals("main.19b2a10d0231cd30.js",
        XetraSecuritySearch.mainScript(FixtureHttpServer.readFixture("xetra/site-home.html")));
    assertEquals("af5a8d16eb5dc49f8a72b26fd9185475c7a",
        XetraSecuritySearch.parseSalt(FixtureHttpServer.readFixture("xetra/site-main.js")));

    SecurityBaseDataDto other = new SecurityBaseDataDto("xetra", "XFRA:DE0001102580", "DE0001102580", "EUR", "XFRA",
        null, null, null, null, null, null, null, null, null, null, null, Map.of());
    JsonNode master = new ObjectMapper().readTree(FixtureHttpServer.readFixture("xetra/master-data-bond.json"));
    assertEquals(other, XetraSecuritySearch.withBondTerms(other, master, null, null));

    // DZ Bank DE000DFK0JD1: no minimum investment amount, smallest tradable unit 100000
    SecurityBaseDataDto dzBank = new SecurityBaseDataDto("xetra", "XFRA:DE000DFK0JD1", "DE000DFK0JD1", "EUR", "XFRA",
        null, null, null, null, null, null, null, null, null, null, null,
        Map.of("type", "Anleihe", "minimumTradableUnit", "100000"));
    assertEquals(100000, XetraSecuritySearch.withBondTerms(dzBank, null, null, null).denomination(),
        "the smallest tradable unit needs no signed request");
    assertEquals(100000, XetraSecuritySearch.withBondTerms(dzBank,
        new ObjectMapper().readTree("{\"isin\":\"DE000DFK0JD1\",\"minimumInvestmentAmount\":null}"), null, null)
        .denomination());

    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, XetraSecuritySearch.couponFrequency("2"));
    assertEquals(DistributionFrequency.DF_NONE, XetraSecuritySearch.couponFrequency("8"));
    assertNull(XetraSecuritySearch.couponFrequency("3"), "four-monthly has no counterpart");
  }

  @Test
  @DisplayName("Price information of another listing is rejected; an empty answer is no match")
  void identity() {
    server.fixture(PRICE_PATH, "xetra/price-information.json");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        xetra().getSecurityBaseData(candidate("DE0007164600", "XETR", "SAP SE", "Aktie"), Locale.GERMAN).status());
    server.status(PRICE_PATH, 200, "{}");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        xetra().getSecurityBaseData(candidate("DE0007164600", "XETR", "SAP SE", "Aktie"), Locale.GERMAN).status());
  }

  @Test
  @DisplayName("The Xetra connector takes the ISIN as URL extension")
  void urlExtension() {
    SecurityBaseDataDto sap = new SecurityBaseDataDto("xetra", "XETR:DE0007164600", "DE0007164600", "EUR", "XETR",
        "SAP SE", null, null, null, null, null, null, null, null, null, null, null);
    assertEquals(Optional.of("DE0007164600"), new XetraFeedConnector().deriveUrlExtension(sap, FeedSupport.FS_HISTORY));
  }
}
