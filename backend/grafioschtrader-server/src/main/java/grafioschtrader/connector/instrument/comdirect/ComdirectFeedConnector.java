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
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import grafioschtrader.connector.securitysearch.ConsorsbankSecuritySearch;
import grafioschtrader.connector.securitysearch.OnvistaSecuritySearch;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

/**
 * A regex check of the URL extension is not active. The connector for checking the instrument works partially and
 * returns an unequal HTTP-OK.
 *
 * <p>
 * comdirect identifies a listing by the same notation id as Consorsbank and onvista ({@code ID_NOTATION}), so the
 * security lookup builds the URL extension {@code {type}/detail/uebersicht.html?ID_NOTATION={notation}} from the
 * listing either provider found. The type path must match the instrument - {@code aktien}, {@code etfs},
 * {@code fonds} or {@code anleihen} - otherwise comdirect answers HTTP 400.
 * </p>
 */
@Component
public class ComdirectFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String BASE_URL = "https://www.comdirect.de/inf/";
  /** comdirect path per instrument type of Consorsbank ({@code STO}, …) and onvista ({@code STOCK}, …). */
  private static final Map<String, String> TYPE_PATHS = Map.of("STO", "aktien", "STOCK", "aktien", "ETF", "etfs",
      "FUN", "fonds", "FUND", "fonds", "BON", "anleihen", "BOND", "anleihen");
  private static final Pattern CONSORSBANK_NOTATION = Pattern.compile("^_(\\d+),");
  private static final Pattern ONVISTA_NOTATION = Pattern.compile("idNotation=(\\d+)");
  private static final Pattern NO_NOTATION = Pattern.compile("(?!)");

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
  }

  public ComdirectFeedConnector() {
    super(supportedFeed, "comdirect", "comdirect", null, EnumSet.of(UrlCheck.INTRADAY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR, AssetclassCategory.EQUITIES,
        AssetclassCategory.FIXED_INCOME, AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND,
        AssetclassCategory.REAL_ESTATE_FUND, AssetclassCategory.ISSUER_RISK_PRODUCT);
  }

  /**
   * Builds the URL extension from the notation id and the instrument type of a Consorsbank or onvista listing; the
   * lookup passes the master data of every provider of the listing.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    String notation = notation(baseData);
    String typePath = TYPE_PATHS.get(baseData.providerAttributes().getOrDefault("type", ""));
    return notation == null || typePath == null ? Optional.empty()
        : Optional.of(typePath + "/detail/uebersicht.html?ID_NOTATION=" + notation);
  }

  /** The notation id of a Consorsbank ({@code _39471840,GER}) or onvista ({@code …?idNotation=39471840}) listing. */
  private static String notation(SecurityBaseDataDto baseData) {
    String id = baseData.providerInstrumentId();
    if (id == null) {
      return null;
    }
    Matcher matcher = (ConsorsbankSecuritySearch.PROVIDER_ID.equals(baseData.providerId()) ? CONSORSBANK_NOTATION
        : OnvistaSecuritySearch.PROVIDER_ID.equals(baseData.providerId()) ? ONVISTA_NOTATION : NO_NOTATION)
        .matcher(id);
    return matcher.find() ? matcher.group(1) : null;
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
    securitycurrency.setSTimestamp(LocalDateTime.now().minusSeconds(getIntradayDelayedSeconds()));
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
