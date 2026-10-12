package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.SecurityBaseDataResult;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Merging the listings several providers found for the same instrument, e.g. the SIX and Yahoo listings of Novartis. */
class LookupListingMergerTest {

  private static LookupListing listing(String providerId, String ticker, String currency, String mic,
      LocalDate activeFrom, Optional<InstrumentType> type) {
    SecuritySearchCandidateDto candidate = new SecuritySearchCandidateDto(providerId, ticker, null, null, null,
        IdentityEvidence.UNKNOWN, currency, IdentityEvidence.UNKNOWN, mic, IdentityEvidence.RETURNED, null, null);
    SecurityBaseDataDto baseData = new SecurityBaseDataDto(providerId, ticker, "CH0012005267", currency, mic,
        providerId + " name", ticker, null, activeFrom, null, null, null, null,
        "six".equals(providerId) ? "https://www.six-group.com/x" : null, null, null, Map.of("type", providerId));
    return LookupListing.of(candidate, SecurityBaseDataResult.ok(providerId, baseData), baseData, type);
  }

  private static final Optional<InstrumentType> SHARE = Optional
      .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));

  @Test
  @DisplayName("The same listing becomes one row: SIX values first, Yahoo fills the first trading day")
  void mergeSameListing() {
    List<LookupListing> merged = LookupListingMerger
        .merge(List.of(listing("yahoo", "NOVN", "CHF", "XSWX", LocalDate.of(2000, 1, 3), SHARE),
            listing("six", "NOVN", "CHF", "XSWX", null, SHARE)));
    assertEquals(1, merged.size());
    LookupListing novartis = merged.getFirst();
    assertEquals(List.of("six", "yahoo"), novartis.providerIds(), "the exchange provider is primary");
    assertEquals("six name", novartis.baseData().name());
    assertEquals(LocalDate.of(2000, 1, 3), novartis.baseData().activeFromDate());
    assertEquals("yahoo", novartis.fieldSources().get("activeFromDate"));
    assertEquals("six", novartis.fieldSources().get("name"));
    assertEquals("yahoo", novartis.baseData().providerAttributes().get("yahoo.type"));
    assertEquals("six", novartis.baseData().providerAttributes().get("type"));
  }

  @Test
  @DisplayName("Deutsche Börse delivers no ticker: it absorbs one listing per provider, which supplies the ticker")
  void mergeWithoutTicker() {
    LookupListing xetra = listing("xetra", null, "EUR", "XETR", null, Optional.empty());
    List<LookupListing> merged = LookupListingMerger
        .merge(List.of(listing("yahoo", "SAP", "EUR", "XETR", LocalDate.of(2000, 1, 3), SHARE), xetra,
            listing("yahoo", "SAPX", "EUR", "XETR", null, SHARE)));
    assertEquals(2, merged.size(), "a second Yahoo listing is not absorbed as well");
    LookupListing sap = merged.getFirst();
    assertEquals(List.of("xetra", "yahoo"), sap.providerIds());
    assertEquals("SAP", sap.baseData().tickerSymbol());
    assertEquals("yahoo", sap.fieldSources().get("tickerSymbol"));
    assertEquals(SHARE, sap.instrumentType(), "the open type of the ETP-free Xetra listing is taken from Yahoo");
  }

  @Test
  @DisplayName("OpenFIGI supplements the row of its exchange; it forms a row only when no other provider found one")
  void openFigiSupplements() {
    LookupListing xetra = listing("xetra", null, "EUR", "XFRA", null, Optional.empty());
    List<LookupListing> merged = LookupListingMerger
        .merge(List.of(listing("openfigi", "ADS", "EUR", "XFRA", null, SHARE), xetra,
            listing("openfigi", "ADS1", "EUR", "XFRA", null, SHARE)));
    assertEquals(1, merged.size(), "a second OpenFIGI hit adds no row");
    LookupListing adidas = merged.getFirst();
    assertEquals(List.of("xetra", "openfigi"), adidas.providerIds());
    assertEquals("ADS", adidas.baseData().tickerSymbol());
    assertEquals("openfigi", adidas.fieldSources().get("tickerSymbol"));
    assertEquals(SHARE, adidas.instrumentType());

    List<LookupListing> alone = LookupListingMerger
        .merge(List.of(listing("openfigi", "ASML", "EUR", "XAMS", null, SHARE)));
    assertEquals(List.of("openfigi"), alone.getFirst().providerIds(), "the only provider forms the row");
  }

  @Test
  @DisplayName("Euronext delivers no currency; its listing absorbs the one of another provider and takes its currency")
  void euronextWithoutCurrency() {
    LookupListing euronext = listing("euronext", "TTE", null, "XPAR", null, SHARE);
    List<LookupListing> merged = LookupListingMerger
        .merge(List.of(listing("yahoo", "TTE", "EUR", "XPAR", LocalDate.of(2000, 1, 3), SHARE), euronext));
    assertEquals(1, merged.size());
    assertEquals(List.of("euronext", "yahoo"), merged.getFirst().providerIds());
    assertEquals("EUR", merged.getFirst().baseData().currency());
    assertEquals("yahoo", merged.getFirst().fieldSources().get("currency"));
  }

  @Test
  @DisplayName("A listing without ticker naming the same ISIN is merged into the row with ticker")
  void secondaryWithoutTicker() {
    List<LookupListing> merged = LookupListingMerger.merge(List.of(listing("six", "FON25", "CHF", "XSWX", null, SHARE),
        listing("onvista", null, "CHF", "XSWX", null, SHARE)));
    assertEquals(1, merged.size());
    assertEquals(List.of("six", "onvista"), merged.getFirst().providerIds());
    assertEquals("FON25", merged.getFirst().baseData().tickerSymbol());
  }

  @Test
  @DisplayName("Whatever the provider order, a Yahoo row takes the ISIN by ticker and absorbs a listing without ticker")
  void withoutTickerAfterIsinByTicker() {
    LookupListing yahoo = listing("yahoo", "KO", "USD", "XNYS", null, SHARE);
    SecurityBaseDataDto withoutIsin = new SecurityBaseDataDto("yahoo", "KO", null, "USD", "XNYS", "yahoo name", "KO",
        null, null, null, null, null, null, null, null, null, Map.of());
    yahoo = LookupListing.of(yahoo.candidate(), SecurityBaseDataResult.ok("yahoo", withoutIsin), withoutIsin, SHARE);
    LookupListing onvista = listing("onvista", "KO", "USD", "XNYS", null, SHARE);
    LookupListing consorsbank = listing("consorsbank", null, "USD", "XNYS", null, SHARE);
    for (List<LookupListing> order : List.of(List.of(consorsbank, yahoo, onvista),
        List.of(yahoo, consorsbank, onvista))) {
      List<LookupListing> merged = LookupListingMerger.merge(order);
      assertEquals(1, merged.size());
      assertEquals(List.of("yahoo", "onvista", "consorsbank"), merged.getFirst().providerIds());
      assertEquals("CH0012005267", merged.getFirst().baseData().isin());
    }
  }

  @Test
  @DisplayName("Without a confirmed ISIN a listing without ticker joins the one Yahoo row of its exchange, not two")
  void withoutTickerJoinsSingleRowWithoutIsin() {
    SecurityBaseDataDto toyota = new SecurityBaseDataDto("yahoo", "7203.T", null, "JPY", "XTKS", "Toyota", "7203",
        null, null, null, null, null, null, null, null, null, Map.of());
    LookupListing yahoo = listing("yahoo", "7203", "JPY", "XTKS", null, SHARE);
    yahoo = LookupListing.of(yahoo.candidate(), SecurityBaseDataResult.ok("yahoo", toyota), toyota, SHARE);
    LookupListing consorsbank = listing("consorsbank", null, "JPY", "XTKS", null, SHARE);

    List<LookupListing> merged = LookupListingMerger.merge(List.of(consorsbank, yahoo));
    assertEquals(1, merged.size());
    assertEquals(List.of("yahoo", "consorsbank"), merged.getFirst().providerIds());
    assertEquals("CH0012005267", merged.getFirst().baseData().isin(), "the ISIN comes from the listing that has it");

    SecurityBaseDataDto other = new SecurityBaseDataDto("yahoo", "7203X.T", null, "JPY", "XTKS", "Other", "7203X",
        null, null, null, null, null, null, null, null, null, Map.of());
    LookupListing secondYahoo = LookupListing.of(listing("yahoo", "7203X", "JPY", "XTKS", null, SHARE).candidate(),
        SecurityBaseDataResult.ok("yahoo", other), other, SHARE);
    assertEquals(3, LookupListingMerger.merge(List.of(yahoo, secondYahoo, consorsbank)).size(),
        "with two candidate rows the listing stays on its own");
  }

  @Test
  @DisplayName("A bond quoted in percent has no currency and is still merged into the row of the exchange")
  void secondaryWithoutCurrency() {
    List<LookupListing> merged = LookupListingMerger.merge(List.of(listing("six", "FON25", "CHF", "XSWX", null, SHARE),
        listing("boursorama", "FON25", null, "XSWX", null, SHARE)));
    assertEquals(1, merged.size());
    assertEquals("CHF", merged.getFirst().baseData().currency());
  }

  @Test
  @DisplayName("Another currency or another ticker is another listing")
  void keepDifferentListings() {
    assertEquals(2, LookupListingMerger.merge(List.of(listing("six", "NOVN", "CHF", "XSWX", null, SHARE),
        listing("yahoo", "NOVN", "USD", "XSWX", null, SHARE))).size());
    assertEquals(2, LookupListingMerger.merge(List.of(listing("six", "NOVN", "CHF", "XSWX", null, SHARE),
        listing("yahoo", "NOVNEE", "CHF", "XSWX", null, SHARE))).size());
  }

  @Test
  @DisplayName("Instrument types: an open asset class is filled, contradicting instruments leave the type open")
  void mergeTypes() {
    Optional<InstrumentType> etfOpen = Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    Optional<InstrumentType> equityEtf = Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF));
    assertEquals(equityEtf, LookupListingMerger.mergeTypes(etfOpen, equityEtf));
    assertEquals(etfOpen, LookupListingMerger.mergeTypes(Optional.empty(), etfOpen));
    assertTrue(LookupListingMerger.mergeTypes(SHARE, etfOpen).isEmpty());
  }
}
