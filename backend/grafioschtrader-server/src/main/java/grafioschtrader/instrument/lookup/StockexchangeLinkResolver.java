package grafioschtrader.instrument.lookup;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Proposes the stock exchange hyperlink of a listing from a template per MIC, for exchanges whose listing page follows
 * from the ticker or the ISIN alone. The templates reproduce the links users maintain in the production data, e.g.
 * {@code https://www.nasdaq.com/de/market-activity/stocks/mndy}, {@code https://www.nyse.com/quote/XNYS:PG} and
 * {@code https://www.gpw.pl/company-factsheet?isin=PLPKO0000016}. The pages of SIX, Deutsche Börse, Wiener Börse and
 * Euronext are set by their own master data.
 *
 * <p>
 * Each template was checked against the live page (2026-10-12). Exchanges without such a page are left out: the Nordic
 * exchanges and BIVA address a listing by an internal id, which {@code NasdaqNordicSecuritySearch} and
 * {@code BivaSecuritySearch} resolve; Hamburg, Berlin, Shanghai, Shenzhen, Jakarta, B3
 * and Cairo offer no stable listing URL from ticker or ISIN. Where a page exists only for shares (Korea, Stuttgart, Munich,
 * Madrid, Moscow), other instruments get none.
 * </p>
 */
public final class StockexchangeLinkResolver {

  /** Tokyo Stock Exchange securities code: four characters, digits and since 2024 also letters, e.g. 7203 or 130A. */
  private static final Pattern JPX_CODE = Pattern.compile("^[0-9][0-9A-Z]{3}$");
  private static final Pattern HKEX_CODE = Pattern.compile("^[0-9]{1,5}$");
  /**
   * Korean share ISIN: {@code KR7}, the six-digit issue code and a check digit. The first five digits of the issue code
   * are the issuer code that keys the company summary of KRX KIND ({@code KR7402340004} → {@code 40234}).
   */
  private static final Pattern KRX_SHARE_ISIN = Pattern.compile("^KR7[0-9A-Z]{6}[0-9]{3}$");
  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

  private StockexchangeLinkResolver() {
  }

  /**
   * Derives the stock exchange hyperlink.
   *
   * @param mic    MIC of the listing
   * @param ticker exchange ticker, e.g. {@code AAPL}, {@code 7203} in Tokyo, {@code 0700} in Hong Kong; may be null
   * @param isin   ISIN of the listing; may be null
   * @param name   name of the instrument, which London shows in the path; may be null
   * @param sii    derived financial instrument; NASDAQ and Borsa Italiana keep ETFs on their own path, several
   *               exchanges serve shares only
   * @param locale language of the user; NASDAQ offers a German page, TMX a French one
   * @return the hyperlink, or null when no template applies
   */
  public static String resolve(String mic, String ticker, String isin, String name, SpecialInvestmentInstruments sii,
      Locale locale) {
    if (mic == null) {
      return null;
    }
    String t = ticker == null || ticker.isBlank() ? null : ticker.trim().toUpperCase(Locale.ROOT);
    boolean share = sii == SpecialInvestmentInstruments.DIRECT_INVESTMENT;
    boolean etf = sii == SpecialInvestmentInstruments.ETF;
    String language = locale == null ? "" : locale.getLanguage();
    return switch (mic) {
    case "XNAS" -> t == null ? null
        : "https://www.nasdaq.com/" + ("de".equals(language) ? "de/" : "") + "market-activity/"
            + (etf ? "etf/" : "stocks/") + t.toLowerCase(Locale.ROOT);
    case "XNYS" -> t == null ? null : "https://www.nyse.com/quote/XNYS:" + t;
    case "XWAR" -> isin != null && share ? "https://www.gpw.pl/company-factsheet?isin=" + isin : null;
    case "XTKS" -> t != null && JPX_CODE.matcher(t).matches()
        ? "https://quote.jpx.co.jp/jpxhp/main/index.aspx?f=e_stock_detail&qcode=" + t
        : null;
    case "XLON" -> t == null ? null
        : "https://www.londonstockexchange.com/stock/" + t + "/" + slug(name) + "/company-page";
    case "XTSX" -> t == null ? null : "https://money.tmx.com/" + ("fr".equals(language) ? "fr" : "en") + "/quote/" + t;
    case "XASX" -> t == null ? null : "https://www.asx.com.au/markets/company/" + t;
    case "XNZE" -> t == null ? null : "https://www.nzx.com/instruments/" + t;
    case "XHKG" -> t != null && HKEX_CODE.matcher(t).matches()
        ? "https://www.hkex.com.hk/Market-Data/Securities-Prices/Equities/Equities-Quote?sym="
            + Integer.parseInt(t) + "&sc_lang=en"
        : null;
    case "MISX" -> t != null && share ? "https://www.moex.com/en/issue.aspx?board=TQBR&code=" + t : null;
    default -> resolveByIsin(mic, isin, share, etf);
    };
  }

  /** The exchanges whose listing page is addressed by the ISIN. */
  private static String resolveByIsin(String mic, String isin, boolean share, boolean etf) {
    if (isin == null) {
      return null;
    }
    return switch (mic) {
    case "XMAD" -> share ? "https://www.bolsamadrid.es/esp/aspx/Empresas/FichaValor.aspx?ISIN=" + isin : null;
    case "XMIL" -> share || etf
        ? "https://www.borsaitaliana.it/borsa/" + (etf ? "etf" : "azioni") + "/scheda/" + isin + ".html?lang=en"
        : null;
    case "XSTU" -> share ? "https://www.boerse-stuttgart.de/de-de/produkte/aktien/stuttgart/" + isin : null;
    case "XMUN" -> share ? "https://www.boerse-muenchen.de/aktie/" + isin : null;
    case "XKRX" -> share && KRX_SHARE_ISIN.matcher(isin).matches()
        ? "https://engkind.krx.co.kr/common/companysummary.do?method=searchCompanySummary&strIsurCd="
            + isin.substring(3, 8)
        : null;
    case "XBRN" -> "https://www.bxswiss.com/instruments/" + isin;
    case "OTXB" -> "https://www.otc-x.ch/security/" + isin;
    default -> null;
    };
  }

  /** The name as London writes it in the path, e.g. {@code vodafone-group-plc}; the page does not depend on it. */
  static String slug(String name) {
    if (name == null || name.isBlank()) {
      return "company";
    }
    String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    String slug = NON_SLUG.matcher(ascii).replaceAll("-").replaceAll("^-+|-+$", "");
    return slug.isEmpty() ? "company" : slug;
  }
}
