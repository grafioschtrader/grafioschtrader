package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Product hyperlink from issuer templates; only UBS builds its page from the ISIN alone. */
class ProductLinkResolverTest {

  private static SecurityBaseDataDto fund(String isin, String name, String fundManager) {
    return new SecurityBaseDataDto("six", isin + "CHF4", isin, "CHF", "XSWX", name, null, null, null, null, null, null,
        null, null, null, null, fundManager == null ? Map.of() : Map.of("general.fundManager", fundManager));
  }

  @Test
  @DisplayName("UBS fund: template from the ISIN in the user's language")
  void ubs() {
    SecurityBaseDataDto ubs = fund("LU0879397742", "UBSETF SBIF1-5 Cd", "UBS (Lux) Fund Solutions");
    assertEquals(
        "https://www.ubs.com/ch/de/asset-management/etf-private/etf-products/"
            + "etf-product-detail.ch.de.lu0879397742.basedata.html",
        ProductLinkResolver.resolve(ubs, SpecialInvestmentInstruments.ETF, Locale.GERMAN));
    assertEquals(
        "https://www.ubs.com/ch/en/asset-management/etf-private/etf-products/"
            + "etf-product-detail.ch.en.lu0879397742.basedata.html",
        ProductLinkResolver.resolve(ubs, SpecialInvestmentInstruments.ETF, Locale.FRENCH));
    assertEquals(
        ProductLinkResolver.resolve(ubs, SpecialInvestmentInstruments.ETF, Locale.GERMAN), ProductLinkResolver
            .resolve(fund("LU0879397742", "UBS ETF SBI", null), SpecialInvestmentInstruments.ETF, Locale.GERMAN),
        "without fund manager the name identifies the issuer");
  }

  @Test
  @DisplayName("Other issuers, shares and bonds get no product hyperlink")
  void noTemplate() {
    assertNull(ProductLinkResolver.resolve(fund("IE000NFR7C63", "iSh MSCI China Tech USD A", "iShares IV plc"),
        SpecialInvestmentInstruments.ETF, Locale.GERMAN));
    assertNull(ProductLinkResolver.resolve(fund("CH0244767585", "UBS GROUP N", null),
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, Locale.GERMAN), "a UBS share is no fund");
    assertNull(ProductLinkResolver.resolve(fund("LU0879397742", "UBSETF SBIF1-5 Cd", null), null, Locale.GERMAN),
        "unknown instrument type");
  }
}
