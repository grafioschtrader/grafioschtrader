package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.vienna.ViennaStockExchangeFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Wiener Börse against shortened recordings of its quote pages (Andritz, a Lower Austria bond, the home page an
 * unlisted ISIN redirects to) and of the chart endpoint.
 */
class ViennaSecuritySearchTest {

  private static final String PAGE_PATH = "/marktdaten/aktien-sonstige/preisdaten/";
  private static final String CHART_PATH = "/interactive-chart/canvas/data/";
  private static final String ANDRITZ = "AT0000730007";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private ViennaSecuritySearch vienna() {
    return new ViennaSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("Share: notation, ticker, name and kind from the quote page; ISIN and MIC delivered")
  void sharePage() {
    server.fixture(PAGE_PATH, "vienna/page-andritz.html");
    SecuritySearchResult result = vienna().search(SecuritySearchQuery.of(ANDRITZ, "EUR", "XVIE", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto c = result.candidates().getFirst();
    assertEquals("740752", c.providerInstrumentId());
    assertEquals("ANDR", c.providerSymbol());
    assertEquals("Andritz AG", c.name());
    assertEquals(ViennaSecuritySearch.TYPE_SHARE, c.providerInstrumentType());
    assertEquals(IdentityEvidence.RETURNED, c.isinEvidence());
    assertEquals("XVIE", c.mic());
  }

  @Test
  @DisplayName("Bond page: name from the title, no ticker; an unlisted ISIN lands on the home page without notation")
  void bondAndUnlisted() {
    server.fixture(PAGE_PATH, "vienna/page-bond.html");
    SecuritySearchCandidateDto bond = vienna().search(SecuritySearchQuery.of("AT0000A11772", null, "XVIE", null))
        .candidates().getFirst();
    assertEquals("90108510", bond.providerInstrumentId());
    assertEquals("Land NOE var. Schuldv. 13-28", bond.name());
    assertNull(bond.providerSymbol());
    assertEquals(ViennaSecuritySearch.TYPE_BOND, bond.providerInstrumentType());

    server.fixture(PAGE_PATH, "vienna/page-home.html");
    assertEquals(SecuritySearchStatus.NO_MATCH,
        vienna().search(SecuritySearchQuery.of("IE00B4L5Y983", null, "XVIE", null)).status());
    assertEquals(SecuritySearchStatus.UNSUPPORTED_QUERY,
        vienna().search(SecuritySearchQuery.of(ANDRITZ, null, "XETR", null)).status());
  }

  @Test
  @DisplayName("OeKB fund: the name segment of the title, kind fund although the name contains 'Anleihe'")
  void fundPage() {
    server.fixture(PAGE_PATH, "vienna/page-fund.html");
    SecuritySearchCandidateDto fund = vienna().search(SecuritySearchQuery.of("AT0000744594", null, "XVIE", null))
        .candidates().getFirst();
    assertEquals("8595610", fund.providerInstrumentId());
    assertEquals("3 Banken Anleihefonds-Selektion (T)", fund.name());
    assertEquals(ViennaSecuritySearch.TYPE_FUND, fund.providerInstrumentType());
    SecurityBaseDataDto data = ViennaSecuritySearch.parseChart(
        "{\"instrumentName\":\"3 Banken Anleihefonds-Selektion (T)\",\"currency\":\"EUR\",\"exchangeCode\":\"@AT\"}",
        fund, Locale.GERMAN);
    assertEquals("EUR", data.currency(), "the OeKB fund data source @AT is accepted");
  }

  @Test
  @DisplayName("Master data: name and currency from the chart, first price day, quote page link with notation")
  void baseData() {
    server.fixture(PAGE_PATH, "vienna/page-andritz.html").fixture(CHART_PATH, "vienna/chart-andritz.json");
    SecuritySearchCandidateDto c = vienna().search(SecuritySearchQuery.of(ANDRITZ, "EUR", "XVIE", null)).candidates()
        .getFirst();
    SecurityBaseDataDto data = vienna().getSecurityBaseData(c, Locale.GERMAN).data();
    assertEquals("EUR", data.currency());
    assertEquals("Andritz AG", data.name());
    assertEquals("ANDR", data.tickerSymbol());
    assertEquals(LocalDate.of(2001, 6, 25), data.listedSince());
    assertEquals(LocalDate.of(2001, 6, 25), data.activeFromDate());
    assertEquals(
        "https://www.wienerborse.at/marktdaten/aktien-sonstige/preisdaten/?ISIN=AT0000730007&ID_NOTATION=740752",
        data.stockexchangeLink());
    assertEquals(Optional.of("740752"),
        new ViennaStockExchangeFeedConnector().deriveUrlExtension(data, FeedSupport.FS_HISTORY));
  }

  @Test
  @DisplayName("Bond terms: maturity, first listing, denomination and coupon frequency from the master data modules")
  void bondTerms() {
    Map<String, String> terms = new LinkedHashMap<>();
    terms.putAll(ViennaSecuritySearch.parseModule(FixtureHttpServer.readFixture("vienna/module-bond-general.json"),
        "c10724"));
    terms.putAll(
        ViennaSecuritySearch.parseModule(FixtureHttpServer.readFixture("vienna/module-bond-issue.json"), "c10726"));
    terms.putAll(
        ViennaSecuritySearch.parseModule(FixtureHttpServer.readFixture("vienna/module-bond-coupon.json"), "c94047"));
    assertEquals("03.10.2029", terms.get(ViennaSecuritySearch.TERM_MATURITY));
    assertEquals("500,00", terms.get(ViennaSecuritySearch.TERM_DENOMINATION));
    assertFalse(terms.containsKey("Nächste Zinsanpassung"), "a '-' is no value");

    SecurityBaseDataDto chart = new SecurityBaseDataDto(ViennaSecuritySearch.PROVIDER_ID, "477879098", "AT0000A3FA05",
        "EUR", "XVIE", "voestalpine3.75%GreenN.24-29/1", null, null, LocalDate.of(2024, 10, 4), null, null, null, null,
        "https://www.wienerborse.at/", LocalDate.of(2024, 10, 4), null, Map.of("idNotation", "477879098"));
    SecurityBaseDataDto bond = ViennaSecuritySearch.withBondTerms(chart, terms);
    assertEquals("voestalpine AG 3.75% Green Notes 2024-2029/Series 1", bond.name());
    assertEquals(LocalDate.of(2024, 10, 3), bond.activeFromDate());
    assertEquals(LocalDate.of(2024, 10, 3), bond.listedSince());
    assertEquals(LocalDate.of(2029, 10, 3), bond.activeToDate());
    assertEquals(500, bond.denomination());
    assertEquals(DistributionFrequency.DF_YEAR, bond.distributionFrequency());
    assertEquals("3,750%", bond.providerAttributes().get("Zinssatz"));
    assertEquals("477879098", bond.providerAttributes().get("idNotation"));
    assertEquals(chart, ViennaSecuritySearch.withBondTerms(chart, Map.of()), "without terms the chart data remain");

    assertEquals(100000, ViennaSecuritySearch.termInteger("100.000,00"));
    assertNull(ViennaSecuritySearch.termInteger("0,50"));
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, ViennaSecuritySearch.couponFrequencyFromMonths("6"));
    assertNull(ViennaSecuritySearch.couponFrequencyFromMonths("24"));
  }

  @Test
  @DisplayName("Types: share, bond, fund with open asset class; the notation is only derived from Vienna master data")
  void typesAndForeignMasterData() {
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        ViennaInstrumentTypeMapper.map(ViennaSecuritySearch.TYPE_SHARE).orElseThrow());
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND),
        ViennaInstrumentTypeMapper.map(ViennaSecuritySearch.TYPE_FUND).orElseThrow());
    assertTrue(ViennaInstrumentTypeMapper.map(null).isEmpty());
    SecurityBaseDataDto yahoo = new SecurityBaseDataDto("yahoo", "ANDR.VI", ANDRITZ, "EUR", "XVIE", null, "ANDR", null,
        null, null, null, null, null, null, null, null, null);
    assertTrue(new ViennaStockExchangeFeedConnector().deriveUrlExtension(yahoo, FeedSupport.FS_HISTORY).isEmpty());
  }
}
