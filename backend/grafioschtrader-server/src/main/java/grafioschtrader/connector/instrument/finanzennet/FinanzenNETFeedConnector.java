package grafioschtrader.connector.instrument.finanzennet;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.instrument.finanzen.FinanzenBase;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.connector.instrument.finanzen.FinanzenWithAjaxControllerCallCurrencypair;
import grafioschtrader.connector.instrument.finanzen.FinanzenWithAjaxControllerCallSecurity;
import grafioschtrader.connector.instrument.finanzen.UseLastPartUrl;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*-
 * Stocks, Bond, ETF:<br>
 * It difficult to check the url extension with a regex pattern
 *
 * Dividend: Value are summarized, can not be used in this application.
 * Splits: Not supported
 */
@Component
public class FinanzenNETFeedConnector extends BaseFeedConnector {

  public static String domain = "https://www.finanzen.net/";
//	private static String dateTimeFormatStr = "dd.MM.yyyy HH:mm:ss";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public FinanzenNETFeedConnector() {
    super(supportedFeed, "finanzennet", "Finanzen NET", null);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    String[] firstPartUrl = security.getUrlHistoryExtend().split(Pattern.quote("|"));
    return domain + firstPartUrl[0];
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {

    FinanzenBase<Security> finanzenBase;
    if (security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES) {
      finanzenBase = new FinanzenNETStockAndBond(domain, this, Locale.GERMAN);
    } else {
      finanzenBase = new FinanzenWithAjaxControllerCallSecurity(domain,
          security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF
              ? FinanzenWithAjaxControllerCallSecurity.HISTORICAL_PRICE_LIST_REDESIGN
              : FinanzenWithAjaxControllerCallSecurity.HISTORICAL_PRICE_LIST,
          this, Locale.GERMAN, UseLastPartUrl.None, 1);
    }
    return finanzenBase.getHistoryquotes(security, from, to);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return domain + currencypair.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    FinanzenBase<Currencypair> finanzenBase = new FinanzenWithAjaxControllerCallCurrencypair(domain, this,
        Locale.GERMAN, 1);
    return finanzenBase.getHistoryquotes(currencyPair, from, to);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return domain + security.getUrlIntraExtend()
        + (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
            || security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF
                ? "/" + FinanzenHelper.getNormalMappedStockexchangeSymbol(security.getStockexchange().getMic())
                : "");
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws IOException, ParseException {
    final Connection finanzenConnection = Jsoup.connect(getSecurityIntradayDownloadLink(security));
    final Document doc = finanzenConnection.timeout(20000).get();
    var sii = security.getAssetClass().getSpecialInvestmentInstrument();
    String select = "div.pricebox table tr";
    var assetClassType = security.getAssetClass().getCategoryType();
    switch (sii) {
    case CFD:
    case DIRECT_INVESTMENT:
      if (assetClassType == AssetclassType.EQUITIES) {
        // select = "#ShareQuotes_1 table tr";
        select = "[data-sg-tab-region-content=0] table tr";
      } else if (assetClassType == AssetclassType.COMMODITIES) {
        select = "div.table-quotes table tr";
      }
      break;
    case ETF:
      select = "div#SnapshotQuoteData table tr";
      break;
    case MUTUAL_FUND:
      select = "div.table-responsive:eq(3) table.table-small tr";
      break;
    default:
      // Do nothing
    }
    updateSecuritycurrency(doc.select(select), security);

  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  private void updateSecuritycurrency(Elements rows, Security security) throws IOException, ParseException {

    for (final Element row : rows) {
      final Elements cols = row.select("td,th");
      String value = cols.get(1).text().strip();
      switch (cols.get(0).text().strip()) {
      case "Kurs":
        String quote = value.substring(0, value.indexOf(' '));
        security.setSLast(FeedConnectorHelper.parseDoubleGE(quote));
        security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
        break;

      case "Eröffnung":
      case "Eröffnungskurs":
        if (!value.equals("-")) {
          security.setSOpen(FeedConnectorHelper.parseDoubleGE(value));
        }
        break;
      case "Volumen (Stück)":
      case "Tagesvolumen (Stück)":
      case "Tagesvolumen in Stück":
        if (!value.equals("-") && !value.equals("n/a")) {
          security.setSVolume(FeedConnectorHelper.parseLongGE(value));
        }
        break;

      case "Eröffnung/Vortag":  
      case "Eröffnung / Vortag":
        String openDayBefore[] = value.split(" / ");
        security.setSOpen(FeedConnectorHelper.parseDoubleGE(openDayBefore[0]));
        security.setSPrevClose(FeedConnectorHelper.parseDoubleGE(openDayBefore[1]));
        break;

      case "Tageshoch/Tagestief":  
      case "Tageshoch / Tagestief":
        String highLow[] = value.split(" / ");
        if (!highLow[0].equals("-")) {
          security.setSHigh(FeedConnectorHelper.parseDoubleGE(highLow[0]));
          security.setSLow(FeedConnectorHelper.parseDoubleGE(highLow[1]));
        }
        break;
      case "Tageshoch":
        security.setSHigh(FeedConnectorHelper.parseDoubleGE(value));
        break;

      case "Tagestief":
        security.setSLow(FeedConnectorHelper.parseDoubleGE(value));
        break;

      case "Vortag":
      case "Schlusskurs Vortag":
        security.setSPrevClose(FeedConnectorHelper.parseDoubleGE(value));
        break;
      /*
       * case "Kurszeit": dateTimeValue = value.replace("[a-zA-Z]", "").strip();
       * break; case "Kursdatum": dateTimeValue = value + " " + dateTimeValue; break;
       */

      }

    }
    // security.setSTimestamp(dateTimeFormat.parse(dateTimeValue.strip()));

  }

}