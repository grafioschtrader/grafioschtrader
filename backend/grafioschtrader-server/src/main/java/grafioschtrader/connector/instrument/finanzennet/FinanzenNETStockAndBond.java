package grafioschtrader.connector.instrument.finanzennet;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.finanzen.FinanzenBase;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/*-
*
*
* Stock (DE0007100000, Daimler):
* https://www.finanzen.net/aktien/Daimler-Aktie
* https://www.finanzen.net/historische-kurse/Daimler Link: historische-kurse/Daimler use form data and cookie
*
* Obligation (CH0398013778):
* https://www.finanzen.net/anleihen/a19vaz-rallye-anleihe-historisch
* Link: anleihen/a19vaz-rallye-anleihe-historisch use form data and cookie
*
* ETF (LU0489337690):
* https://www.finanzen.net/etf/xtrackers_ftse_developed_europe_real_estate_ucits_etf_1c
* https://www.finanzen.net/etf/historisch/xtrackers_ftse_developed_europe_real_estate_ucits_etf_1c
* https://www.finanzen.net/ajax/FundController_HistoricPriceList/xtrackers_ftse_developed_europe_real_estate_ucits_etf_1c/FSE/1.9.2015_8.10.2018
* Link: etf/historisch/xtrackers_ftse_developed_europe_real_estate_ucits_etf_1c use: __atcrv, __ath, __atts
*
*
*
*/
public class FinanzenNETStockAndBond extends FinanzenBase<Security> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public FinanzenNETStockAndBond(String domain, IFeedConnector feedConnector, Locale locale) {
    super(domain, feedConnector, locale);
  }

  @Override
  public List<Historyquote> getHistoryquotes(final Security security, final Date from, final Date to,
      int[] headerColMapping) throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    final CookieStore cookieStore = new BasicCookieStore();

    final RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30000)
        .setConnectionRequestTimeout(30000).setSocketTimeout(30000).setCookieSpec(CookieSpecs.STANDARD).build();

    try (CloseableHttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
      dummyConnection(httpclient, security, cookieStore);
      String pkBHTs = readPkBHTs(httpclient, cookieStore);
      if (pkBHTs != null) {
        if (collectData(httpclient, security, from, to, historyquotes, pkBHTs, cookieStore, headerColMapping)
            && historyquotes.isEmpty()) {
          // Sometimes where a lot of data should be returned, only a single line comes
          // back
          // In this case try once again to collect the data
          log.info("In {} 2nd try for {}", feedConnector.getID(), security.getName());
          collectData(httpclient, security, from, to, historyquotes, pkBHTs, cookieStore, headerColMapping);
        }
      }
    }
    return historyquotes;
  }

  /**
   * It works better when this dummy connection is used
   *
   * @param httpclient
   * @param security
   * @param cookieStore
   * @throws ClientProtocolException
   * @throws IOException
   */
  private void dummyConnection(CloseableHttpClient httpclient, Security security, CookieStore cookieStore)
      throws IOException {
    HttpGet httpGet = new HttpGet(feedConnector.getSecurityHistoricalDownloadLink(security).toLowerCase());
    try (CloseableHttpResponse httpResponse = httpclient.execute(httpGet)) {
      addToCookieStore(httpResponse, cookieStore);
    }
  }

  private String readPkBHTs(CloseableHttpClient httpclient, CookieStore cookieStore) throws IOException {
    String pkBHTs = null;
    HttpGet httpGet = new HttpGet(domain + "holedaten.asp?strFrag=vdBHTSService");
    try (CloseableHttpResponse httpResponse = httpclient.execute(httpGet)) {
      HttpEntity entity = httpResponse.getEntity();
      pkBHTs = EntityUtils.toString(entity);
      addToCookieStore(httpResponse, cookieStore);
    }
    return pkBHTs;
  }

  private boolean collectData(CloseableHttpClient httpclient, Security security, final Date from, final Date to,
      final List<Historyquote> historyquotes, String pkBHTs, CookieStore cookieStore, int[] headerColMapping)
      throws IOException, ParseException {
    int rows = 0;
    Calendar calFrom = Calendar.getInstance();
    calFrom.setTime(from);
    Calendar calTo = Calendar.getInstance();
    calTo.setTime(to);

    ArrayList<NameValuePair> postParameters = new ArrayList<>();
    postParameters.add(new BasicNameValuePair("inTag1", "" + calFrom.get(Calendar.DAY_OF_MONTH)));
    postParameters.add(new BasicNameValuePair("inMonat1", "" + (calFrom.get(Calendar.MONTH) + 1)));
    postParameters.add(new BasicNameValuePair("inJahr1", "" + calFrom.get(Calendar.YEAR)));
    postParameters.add(new BasicNameValuePair("inTag2", "" + calTo.get(Calendar.DAY_OF_MONTH)));
    postParameters.add(new BasicNameValuePair("inMonat2", "" + (calTo.get(Calendar.MONTH) + 1)));
    postParameters.add(new BasicNameValuePair("inJahr2", "" + calTo.get(Calendar.YEAR)));
    postParameters.add(new BasicNameValuePair("strBoerse",
        FinanzenHelper.getNormalMappedStockexchangeSymbol(security.getStockexchange().getMic())));
    postParameters.add(new BasicNameValuePair("pkBHTs", pkBHTs));

    HttpPost httpPost = new HttpPost(feedConnector.getSecurityHistoricalDownloadLink(security).toLowerCase());
    httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
    httpPost.setHeader("cookie", getCookiesAsString(cookieStore));
    try (CloseableHttpResponse httpResponse = httpclient.execute(httpPost)) {
      HttpEntity entity = httpResponse.getEntity();
      rows = parseContent(Jsoup.parse(EntityUtils.toString(entity)), historyquotes, headerColMapping);
    }

    return DateHelper.getDateDiff(from, to, TimeUnit.DAYS) > 10 && rows >= 1 && rows <= 2;
  }

  private int parseContent(Document doc, final List<Historyquote> historyquotes, int[] headerColMapping)
      throws ParseException {
    Elements elements = doc.getElementsByClass("box table-quotes");
    Element table = elements.get(0).select("table").get(0);
    return super.parseTableContent(table, historyquotes, headerColMapping);
  }

  protected void addToCookieStore(HttpResponse httpResponse, CookieStore cookieStore) {
    Header[] headers = httpResponse.getHeaders("Set-Cookie");

    if (headers != null) {
      // cookieStore.clear();
      for (Header header : headers) {

        String cookie = header.getValue();
        String[] cookievalues = cookie.split(";");

        for (String cookievalue : cookievalues) {
          String[] keyPair = cookievalue.split("=", 2);
          String key = keyPair[0].trim();
          if (key.startsWith("CAP")) {
            String value = keyPair.length > 1 ? keyPair[1].trim() : "";
            BasicClientCookie newCookie = new BasicClientCookie(key, value);
            cookieStore.addCookie(newCookie);
          }
        }
      }
    }
  }

  protected String getCookiesAsString(CookieStore cookieStore) {
    String cookiesString = "";
    for (Cookie cookie : cookieStore.getCookies()) {
      cookiesString += cookie.getName() + "=" + cookie.getValue() + "; ";
    }
    return cookiesString;
  }

}