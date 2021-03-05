package grafioschtrader.connector.instrument.finanzench;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.finanzen.FinanzenBase;
import grafioschtrader.connector.instrument.finanzen.FinanzenWithAjaxControllerCallCurrencypair;
import grafioschtrader.connector.instrument.finanzen.FinanzenWithAjaxControllerCallSecurity;
import grafioschtrader.connector.instrument.finanzen.UseLastPartUrl;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@Component
public class FinanzenCHFeedConnector extends BaseFeedConnector {

  protected static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  static String domain = "https://www.finanzen.ch/";
  private static Locale FC_LOCALE = new Locale("de", "CH");

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public FinanzenCHFeedConnector() {
    super(supportedFeed, "finanzench", "Finanzen CH");
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return domain + security.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final String url = getSecurityIntradayDownloadLink(security);
    
    HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
  
    final Document doc = Jsoup.parse(response.body());
    final Elements elementPrices = doc.select("table.pricebox th");
    
    final Iterator<Element> iter = elementPrices.iterator();

    final double lastPrice = NumberFormat.getNumberInstance(FC_LOCALE).parse(iter.next().text().replace("'", ""))
        .doubleValue();
    final double dalyChange = NumberFormat.getNumberInstance(FC_LOCALE).parse(iter.next().text().replace("'", ""))
        .doubleValue();
    final double changePercentage = NumberFormat.getNumberInstance(FC_LOCALE)
        .parse(iter.next().text().replaceAll("[ %]", "")).doubleValue();

    security.setSLast(lastPrice);
    security.setSPrevClose(lastPrice + dalyChange);
    security.setSChangePercentage(changePercentage);
    security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
    
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return domain + security.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {

    FinanzenBase<Security> finanzenBase = new FinanzenWithAjaxControllerCallSecurity(domain,
        FinanzenWithAjaxControllerCallSecurity.HISTORICAL_PRICE_LIST, this, FC_LOCALE,
        UseLastPartUrl.AS_STOCKEXCHANGE_SYMBOL, 2);
    int[] headerColMapping = null;

    if (security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
            || security.getAssetClass().getCategoryType() == AssetclassType.CONVERTIBLE_BOND)) {
      headerColMapping = new int[] { 0, 1, 2, -1, -1, -1 };
    }

    return finanzenBase.getHistoryquotes(security, from, to, headerColMapping);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return domain + currencypair.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    FinanzenBase<Currencypair> finanzenBase = new FinanzenWithAjaxControllerCallCurrencypair(domain, this, FC_LOCALE,
        2);
    return finanzenBase.getHistoryquotes(currencyPair, from, to);
  }
}
