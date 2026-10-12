package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Euronext against recorded factsheet blocks and quick search answers: the French government bond OAT 3% 2033
 * (FR001400H7V7, which the quick search does not list) and TotalEnergies on Paris.
 */
class EuronextSecuritySearchTest {

  private static final String BOND = "FR001400H7V7";
  private static final String TOTAL = "FR0000120271";
  private static final String BLOCK_PATH = "/en/ajax/getFactsheetInfoBlock/BONDS/";
  private static final String SEARCH_PATH = "/en/instrumentSearch/searchJSON";

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private EuronextSecuritySearch euronext() {
    return new EuronextSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  private void bondFixtures() {
    for (String block : new String[] { "fs_info_block", "fs_instrumentinfo_block", "fs_couponinfo_block",
        "fs_tradingcharacteristics_block", "fs_issuerinfo_block" }) {
      server.fixture(BLOCK_PATH + BOND + "-XPAR/" + block, "euronext/bond-" + block + ".html");
    }
    server.fixture(SEARCH_PATH + "?q=" + BOND, "euronext/search-bond.json");
  }

  @Test
  @DisplayName("Bond: found through its factsheet block; maturity, first day, denomination, coupon and issuer country")
  void bond() {
    bondFixtures();
    SecuritySearchResult result = euronext().search(SecuritySearchQuery.of(BOND, "EUR", "XPAR", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto c = result.candidates().getFirst();
    assertEquals("Bonds/Oat", c.providerInstrumentType());
    assertEquals(BOND + "-XPAR", c.providerInstrumentId());
    assertNull(c.providerSymbol(), "the quick search lists no bond");

    SecurityBaseDataDto data = euronext().getSecurityBaseData(c, Locale.GERMAN).data();
    assertEquals(LocalDate.of(2033, 5, 25), data.activeToDate());
    assertEquals(LocalDate.of(2023, 4, 12), data.activeFromDate());
    assertEquals(LocalDate.of(2023, 4, 12), data.listedSince());
    assertEquals(1, data.denomination());
    assertEquals(DistributionFrequency.DF_YEAR, data.distributionFrequency());
    assertEquals("FR", data.issuerCountry());
    assertEquals("REPUBLIC OF FRANCE 3.0% 2023-2033", data.name());
    assertNull(data.currency(), "Euronext names no trading currency");
    assertEquals("https://live.euronext.com/en/product/bonds/FR001400H7V7-XPAR", data.stockexchangeLink());
    assertEquals(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        EuronextInstrumentTypeMapper.map(c.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("Share: ticker and name from the quick search, first listing; no bond terms are requested")
  void share() {
    server.fixture(BLOCK_PATH + TOTAL + "-XPAR/fs_info_block", "euronext/share-fs_info_block.html")
        .fixture(BLOCK_PATH + TOTAL + "-XPAR/fs_tradingcharacteristics_block",
            "euronext/share-fs_tradingcharacteristics_block.html")
        .fixture(SEARCH_PATH + "?q=" + TOTAL, "euronext/search-total.json");
    SecuritySearchCandidateDto c = euronext().search(SecuritySearchQuery.of(TOTAL, "EUR", "XPAR", null)).candidates()
        .getFirst();
    assertEquals("TTE", c.providerSymbol());
    assertEquals("TOTALENERGIES", c.name());
    SecurityBaseDataDto data = euronext().getSecurityBaseData(c, Locale.GERMAN).data();
    assertEquals("TTE", data.tickerSymbol());
    assertEquals(LocalDate.of(2000, 9, 25), data.listedSince());
    assertNull(data.activeFromDate());
    assertNull(data.denomination(), "the nominal value of a share is no denomination");
    assertFalse(server.requests().stream().anyMatch(r -> r.contains("fs_couponinfo_block")));
    assertEquals(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        EuronextInstrumentTypeMapper.map(c.providerInstrumentType()).orElseThrow());
  }

  @Test
  @DisplayName("An ISIN not listed on the exchange gives an empty block and no candidate; the query needs a MIC")
  void notListed() {
    server.status(BLOCK_PATH + BOND + "-XAMS/fs_info_block", 200, "");
    assertTrue(euronext().search(SecuritySearchQuery.of(BOND, "EUR", "XAMS", null)).candidates().isEmpty());
    assertFalse(euronext().supports(SecuritySearchQuery.of(BOND, "EUR", null, null)));
    assertEquals(DistributionFrequency.DF_SEMI_ANNUAL, EuronextSecuritySearch.couponFrequency("Semi-annual"));
    assertNull(EuronextSecuritySearch.couponFrequency("At maturity"));
    assertEquals(new InstrumentType(null, SpecialInvestmentInstruments.ETF),
        EuronextInstrumentTypeMapper.map("ETP/Etf").orElseThrow());
    assertTrue(EuronextInstrumentTypeMapper.map("ETP/Etc").isEmpty());
  }
}
