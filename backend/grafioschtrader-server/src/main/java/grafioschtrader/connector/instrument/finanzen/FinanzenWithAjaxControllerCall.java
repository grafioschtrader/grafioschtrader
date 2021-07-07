package grafioschtrader.connector.instrument.finanzen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;

public abstract class FinanzenWithAjaxControllerCall<T extends Securitycurrency<T>> extends FinanzenBase<T> {

  protected static String DEFAULT_URL_CONTROLLER = "FundController";
  protected static final String URL_DATE_FORMAT = "d.M.yyyy";

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private HeaderCollector headerCollector = new HeaderCollector();

  protected abstract String getAjaxUrl(final T security, final Date from, final Date to);

  protected abstract String getHistoricalDownloadLink(T security);

  public FinanzenWithAjaxControllerCall(String domain, IFeedConnector feedConnector, Locale locale) {
    super(domain, feedConnector, locale);
  }

  @Override
  public List<Historyquote> getHistoryquotes(final T securityCurrency, final Date from, final Date to,
      int[] headerColMapping) throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    final RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30000)
        .setConnectionRequestTimeout(30000).setSocketTimeout(30000).setCookieSpec(CookieSpecs.STANDARD).build();
    try (CloseableHttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
      getHeaderParamsFromPage(httpclient, securityCurrency);
      collectData(getAjaxUrl(securityCurrency, from, to), httpclient, historyquotes, headerColMapping);
    }

    return historyquotes;
  }

  /**
   * This call is used to collect some attributes in the cookies.
   *
   * @param httpclient
   * @param securityCurrency
   * @throws ClientProtocolException
   * @throws IOException
   */
  private void getHeaderParamsFromPage(final CloseableHttpClient httpclient, final T securityCurrency)
      throws ClientProtocolException, IOException {
    HttpGet httpGet = new HttpGet(getHistoricalDownloadLink(securityCurrency));

    try (CloseableHttpResponse httpResponse = httpclient.execute(httpGet)) {
      HttpEntity entity = httpResponse.getEntity();
      boolean allSet = false;
      if (entity != null) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
          String inputLine = null;
          while ((inputLine = in.readLine()) != null && !allSet) {
            allSet = headerCollector.checkLineForAttribute(inputLine);
          }
        }
      }
      if (entity == null || !allSet) {
        log.warn("In {} for security/currency pair {} is URL for page call {} not found!", feedConnector.getID(),
            securityCurrency.getName(), getHistoricalDownloadLink(securityCurrency));
      }
    }
  }

  private void collectData(final String ajaxUrl, final CloseableHttpClient httpclient,
      final List<Historyquote> historyquotes, int[] headerColMapping)
      throws org.apache.http.ParseException, ParseException, IOException {

    HttpPost httpPost = new HttpPost(ajaxUrl);
    headerCollector.addToHeader(httpPost);
    try (CloseableHttpResponse httpResponse = httpclient.execute(httpPost)) {
      HttpEntity entity = httpResponse.getEntity();
      parseContent(Jsoup.parse(EntityUtils.toString(entity)), historyquotes, headerColMapping);
    }
  }

  private int parseContent(Document doc, final List<Historyquote> historyquotes, int[] headerColMapping)
      throws ParseException {
    Element table = doc.select("table").get(0);
    return parseTableContent(table, historyquotes, headerColMapping);
  }

}
