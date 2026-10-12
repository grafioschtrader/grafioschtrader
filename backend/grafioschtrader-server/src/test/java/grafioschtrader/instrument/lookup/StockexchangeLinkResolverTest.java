package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.SpecialInvestmentInstruments;

/** Stock exchange hyperlinks derived from ticker or ISIN, with the forms checked against the live pages. */
class StockexchangeLinkResolverTest {

  private static final SpecialInvestmentInstruments SHARE = SpecialInvestmentInstruments.DIRECT_INVESTMENT;
  private static final SpecialInvestmentInstruments ETF = SpecialInvestmentInstruments.ETF;

  private static String link(String mic, String ticker, String isin, String name, SpecialInvestmentInstruments sii) {
    return StockexchangeLinkResolver.resolve(mic, ticker, isin, name, sii, Locale.GERMAN);
  }

  @Test
  @DisplayName("NASDAQ (stocks/etf, language), NYSE and GPW")
  void americaAndWarsaw() {
    assertEquals("https://www.nasdaq.com/de/market-activity/stocks/aapl",
        link("XNAS", "AAPL", "US0378331005", "Apple", SHARE));
    assertEquals("https://www.nasdaq.com/market-activity/etf/qqq",
        StockexchangeLinkResolver.resolve("XNAS", "QQQ", null, null, ETF, Locale.ENGLISH));
    assertEquals("https://www.nyse.com/quote/XNYS:PG", link("XNYS", "pg", null, null, SHARE));
    assertEquals("https://www.gpw.pl/company-factsheet?isin=PLPKO0000016",
        link("XWAR", "PKO", "PLPKO0000016", null, SHARE));
    assertNull(link("XWAR", null, "PLBEFTF00018", null, ETF), "the GPW factsheet serves shares only");
    assertNull(link("XNAS", null, null, null, SHARE));
  }

  @Test
  @DisplayName("Ticker based: Tokyo, London, Toronto, Sydney, Wellington, Hong Kong and Moscow")
  void byTicker() {
    assertEquals("https://quote.jpx.co.jp/jpxhp/main/index.aspx?f=e_stock_detail&qcode=7203",
        link("XTKS", "7203", "JP3633400001", "Toyota Motor Corp", SHARE));
    assertEquals("https://quote.jpx.co.jp/jpxhp/main/index.aspx?f=e_stock_detail&qcode=130A",
        link("XTKS", "130a", null, null, SHARE), "codes with letters since 2024");
    assertNull(link("XTKS", "TOM", null, null, SHARE), "a German symbol is no Tokyo code");
    assertEquals("https://www.londonstockexchange.com/stock/VOD/vodafone-group-plc/company-page",
        link("XLON", "VOD", "GB00BH4HKS39", "Vodafone Group Plc", SHARE));
    assertEquals("https://www.londonstockexchange.com/stock/RR/rolls-royce-holdings/company-page",
        link("XLON", "RR", null, "Rolls-Royce Holdings", SHARE));
    assertEquals("https://money.tmx.com/en/quote/RY", link("XTSX", "RY", null, null, SHARE));
    assertEquals("https://money.tmx.com/fr/quote/RY",
        StockexchangeLinkResolver.resolve("XTSX", "RY", null, null, SHARE, Locale.FRENCH));
    assertEquals("https://www.asx.com.au/markets/company/CBA", link("XASX", "cba", null, null, SHARE));
    assertEquals("https://www.nzx.com/instruments/FBU", link("XNZE", "FBU", null, null, SHARE));
    assertEquals("https://www.hkex.com.hk/Market-Data/Securities-Prices/Equities/Equities-Quote?sym=700&sc_lang=en",
        link("XHKG", "0700", null, null, SHARE), "Hong Kong drops the leading zeros");
    assertEquals("https://www.moex.com/en/issue.aspx?board=TQBR&code=AFLT", link("MISX", "AFLT", null, null, SHARE));
  }

  @Test
  @DisplayName("ISIN based: Madrid, Milan (shares and ETFs), Korea, Stuttgart, Munich, BX Swiss and OTC-X")
  void byIsin() {
    assertEquals("https://www.bolsamadrid.es/esp/aspx/Empresas/FichaValor.aspx?ISIN=ES0113900J37",
        link("XMAD", "SAN", "ES0113900J37", null, SHARE));
    assertEquals("https://www.borsaitaliana.it/borsa/azioni/scheda/IT0003132476.html?lang=en",
        link("XMIL", "ENI", "IT0003132476", null, SHARE));
    assertEquals("https://www.borsaitaliana.it/borsa/etf/scheda/IE00B4L5Y983.html?lang=en",
        link("XMIL", null, "IE00B4L5Y983", null, ETF));
    assertEquals("https://www.boerse-stuttgart.de/de-de/produkte/aktien/stuttgart/DE0007164600",
        link("XSTU", "SAP", "DE0007164600", null, SHARE));
    assertNull(link("XSTU", null, "IE00B4L5Y983", null, ETF), "Stuttgart and Munich serve shares only");
    assertEquals("https://www.boerse-muenchen.de/aktie/DE0007164600", link("XMUN", null, "DE0007164600", null, SHARE));
    assertEquals("https://www.bxswiss.com/instruments/LU0496786905", link("XBRN", null, "LU0496786905", null, ETF));
    assertEquals("https://www.otc-x.ch/security/CH0016290014", link("OTXB", null, "CH0016290014", null, SHARE));
    assertEquals("https://engkind.krx.co.kr/common/companysummary.do?method=searchCompanySummary&strIsurCd=40234",
        link("XKRX", "402340", "KR7402340004", null, SHARE), "the KRX issuer code is part of the ISIN");
    assertNull(link("XKRX", null, "KRD020020008", null, SHARE), "no share ISIN");
    assertNull(link("XETR", "SAP", "DE0007164600", null, SHARE), "Deutsche Börse sets its own link");
    assertNull(link("XSTO", "NOVO B", "DK0062498333", null, SHARE), "Nordic pages need an order book id");
  }

  @Test
  @DisplayName("London slug: accents removed, separators collapsed, a default without name")
  void slug() {
    assertEquals("nestle-s-a", StockexchangeLinkResolver.slug("Nestlé S.A."));
    assertEquals("company", StockexchangeLinkResolver.slug(null));
    assertEquals("company", StockexchangeLinkResolver.slug(" .. "));
  }
}
