package grafioschtrader.instrument.lookup;

import java.util.Locale;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Proposes the product page of the issuer ("product hyperlink") of a fund from an issuer template. A template is used
 * only when the issuer's URL follows from the ISIN alone; issuer pages are never read.
 *
 * <p>
 * An analysis of the production data showed that a product hyperlink is maintained for nine out of ten ETFs but hardly
 * ever for shares or bonds, and that most issuers need parts that cannot be derived: iShares an internal product
 * number, DWS/Xtrackers and Amundi a name slug. Only UBS builds its page from the ISIN. Issuer URLs also decay quickly
 * when brands merge (Lyxor, ComStage, Credit Suisse, PowerShares), so a further template is added only once a stable
 * URL from ISIN or ticker is proven.
 * </p>
 */
public final class ProductLinkResolver {

  private static final String UBS_TEMPLATE = "https://www.ubs.com/ch/%1$s/asset-management/etf-private/etf-products/"
      + "etf-product-detail.ch.%1$s.%2$s.basedata.html";

  private ProductLinkResolver() {
  }

  /**
   * Derives the product hyperlink.
   *
   * @param baseData master data of the listing; the issuer is recognised from the SIX fund manager or the name
   * @param sii      derived financial instrument; only ETF and mutual funds get a product hyperlink
   * @param locale   language of the user; UBS offers its pages in German and English
   * @return the product hyperlink, or null when no template applies
   */
  public static String resolve(SecurityBaseDataDto baseData, SpecialInvestmentInstruments sii, Locale locale) {
    if (baseData.isin() == null
        || sii != SpecialInvestmentInstruments.ETF && sii != SpecialInvestmentInstruments.MUTUAL_FUND) {
      return null;
    }
    if (isIssuer(baseData, "UBS")) {
      String language = locale != null && "de".equals(locale.getLanguage()) ? "de" : "en";
      return String.format(UBS_TEMPLATE, language, baseData.isin().toLowerCase(Locale.ROOT));
    }
    return null;
  }

  private static boolean isIssuer(SecurityBaseDataDto baseData, String issuerPrefix) {
    String fundManager = baseData.providerAttributes().get("general.fundManager");
    return startsWithIgnoreCase(fundManager, issuerPrefix) || startsWithIgnoreCase(baseData.name(), issuerPrefix);
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}
