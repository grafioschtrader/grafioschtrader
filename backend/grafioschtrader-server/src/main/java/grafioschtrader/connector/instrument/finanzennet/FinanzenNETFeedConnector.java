package grafioschtrader.connector.instrument.finanzennet;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.instrument.finanzen.FinanzenConnetorBase;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*-
* At one time, historical data could also be queried via this connector.
 * However, access to these pages was better protected against non-browser
 * queries. At present, the source code does not yet fully reflect this
 * situation.
 *
 * For some securities, there is a redirect to the corresponding country page.
 * These securities can therefore not be queried. There is a finance.net, ch, and at.
 * Therefore, Finanzen.net should not be used to query the prices of Austrian and Swiss securities.
 *
 * Stocks, Bond, ETF:<br>
 * It difficult to check the url extension with a regex pattern
 *
 * Dividend: Value are summarized, can not be used in this application.
 * Splits: Not supported
 *
 * A regex pattern check is not active. However, the accessibility of the instrument is checked.
 */
@Component
public class FinanzenNETFeedConnector extends FinanzenConnetorBase {

  public static String domain = "https://www.finanzen.net/";
//	private static String dateTimeFormatStr = "dd.MM.yyyy HH:mm:ss";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  
  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public FinanzenNETFeedConnector() {
    super(supportedFeed, "finanzennet", "Finanzen NET", null, EnumSet.of(UrlCheck.INTRADAY));
    initalizeHttpClient(domain);
    
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    String[] firstPartUrl = security.getUrlHistoryExtend().split(Pattern.quote("|"));
    return domain + firstPartUrl[0];
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return null;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return domain + currencypair.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return null;
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
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final Document doc = getDoc(getSecurityIntradayDownloadLink(security));
    
    var sii = security.getAssetClass().getSpecialInvestmentInstrument();
    String select = "div.pricebox table tr";
    var assetClassType = security.getAssetClass().getCategoryType();
    switch (sii) {
    case CFD:
    case DIRECT_INVESTMENT:
      if (assetClassType == AssetclassType.FIXED_INCOME) {
        select = "table.table--headline-first-col.table--content-right:contains(Kurszeit) tr";
      } else if (assetClassType == AssetclassType.EQUITIES) {

        select = "[data-sg-tab-region-content=0] table tr";
      } else if (assetClassType == AssetclassType.COMMODITIES) {
        select = "div.table-quotes table tr";
      }
      break;
    case ETF:
      select = "div#SnapshotQuoteData table tr";
      break;
    case MUTUAL_FUND:
      select = "table.table--headline-first-col.table--content-right:contains(Kurszeit) tr";
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
        String quote = value.replace('%', ' ');
        quote = quote.substring(0, quote.indexOf(' '));
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
        String[] openDayBefore = value.split(" / ");
        if(openDayBefore.length == 1) {
          openDayBefore = value.split(" ");
        }
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