package grafioschtrader.connector.instrument.comdirect;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

/**
 * A regex check of the URL extension is not active. The connector for checking
 * the instrument works partially and returns an unequal HTTP-OK.
 */
@Component
public class ComdirectFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String BASE_URL = "https://www.comdirect.de/inf/";

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  public ComdirectFeedConnector() {
    super(supportedFeed, "comdirect", "comdirect", null, EnumSet.of(UrlCheck.INTRADAY));
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return BASE_URL + security.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    updateSecuritycurrency(security, getSecurityIntradayDownloadLink(security));
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return BASE_URL + currencypair.getUrlIntraExtend();
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateSecuritycurrency(currencypair, getCurrencypairIntradayDownloadLink(currencypair));
  }

  private synchronized <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String url)
      throws Exception {
    Document doc = Jsoup.parseBodyFragment(getResponseByHttpClient(url));

    final Element div = doc.select("#keyelement_kurs_update").first();
    String[] numbers = StringUtils.normalizeSpace(div.text().replace("%", "")).split(" ");
    securitycurrency
        .setSLast(endsWithDotNumbersButNoComma(numbers[0]) ? Double.parseDouble(numbers[0].replaceAll("[A-Z]*$", ""))
            : FeedConnectorHelper.parseDoubleGE(numbers[0].replaceAll("[A-Z]*$", "")));
    var offset = FeedConnectorHelper.isCreatableGE(numbers[1]) ? 0 : 1;
    securitycurrency.setSChangePercentage(FeedConnectorHelper.parseDoubleGE(numbers[1 + offset]));
    securitycurrency.setSOpen(
        DataBusinessHelper.round(securitycurrency.getSLast() - FeedConnectorHelper.parseDoubleGE(numbers[2 + offset])));
    securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }

  private boolean endsWithDotNumbersButNoComma(String input) {
    String regex = "\\.\\d+$";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(input);
    return matcher.find();
  }

  private String getResponseByHttpClient(String url) throws IOException, InterruptedException {
    CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(Redirect.ALWAYS)
        .cookieHandler(cookieManager).build();
    HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(2))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT).uri(URI.create(url)).GET().build();
    HttpResponse<String> response = null;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (SSLHandshakeException | HttpConnectTimeoutException e) {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    return response.body();
  }
}
