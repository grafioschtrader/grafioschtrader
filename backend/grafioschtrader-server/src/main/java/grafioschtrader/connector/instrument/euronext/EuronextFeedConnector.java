package grafioschtrader.connector.instrument.euronext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Price connector for the Euronext exchanges Amsterdam, Brussels, Dublin, Lisbon, Oslo and Paris. It reads the same
 * endpoints the pages of {@code live.euronext.com} use and needs no key and no URL extension: an instrument is addressed
 * by {@code {ISIN}-{MIC}}, for Dublin with the operating MIC {@code XDUB} instead of the segment MIC {@code XMSM} GT
 * stores ({@link #mappingMicOperation}).
 *
 * <p>
 * Endpoints:
 * </p>
 * <ul>
 * <li>History: {@code intraday_chart/getChartData/{ISIN}-{MIC}/{period}}, a JSON array of daily closes with time,
 * price and volume. The period is {@code 1M} for an update of the last 30 days and {@code max} for anything older;
 * the server offers no other range, so the result is filtered to the requested days.</li>
 * <li>Intraday: {@code ajax/getDetailedQuote/{ISIN}-{MIC}} for last price and change, and
 * {@code intraday_chart/getDetailedQuoteAjax/{ISIN}-{MIC}/full} for open, high, low and previous close. Both are HTML
 * fragments requested by POST with {@code theme_name=euronext_live}.</li>
 * </ul>
 *
 * <p>
 * Euronext encrypts these answers. Instead of the data the server returns {@code {"ct":…,"iv":…,"s":…}} in the format
 * of CryptoJS, which {@link #decrypt(String)} reverses with the passphrase built into the script of the site. If
 * Euronext changes that passphrase, every request fails with a decryption error naming this cause.
 * </p>
 *
 * <p>
 * There is no regular expression for a URL extension, since none is used, and no URL check on saving, since the
 * endpoints answer HTTP 200 for any instrument. The setting check instead requires an ISIN and one of the six exchanges,
 * which are also declared as the coverage of the connector.
 * </p>
 */
@Component
public class EuronextFeedConnector extends BaseFeedConnector {

  public static final String STOCK_EX_MIC_AMSTERDAM = "XAMS";
  public static final String STOCK_EX_MIC_BRUSSELS = "XBRU";
  public static final String STOCK_EX_MIC_DUBLIN = "XMSM";
  public static final String STOCK_EX_MIC_LISBON = "XLIS";
  public static final String STOCK_EX_MIC_OSLO = "XOSL";
  public static final String STOCK_EX_MIC_PARIS = "XPAR";

  public static final String STOCK_EX_OPERATION_DUBLIN = "XDUB";

  /** GT MIC to the MIC Euronext uses in its URLs, where they differ: Dublin {@code XMSM} to {@code XDUB}. */
  public static final Map<String, String> mappingMicOperation = new HashMap<>();
  private static final Set<String> EN_STOCK_EXCHANGES_SET = Set.of(STOCK_EX_MIC_AMSTERDAM, STOCK_EX_MIC_BRUSSELS,
      STOCK_EX_MIC_DUBLIN, STOCK_EX_MIC_LISBON, STOCK_EX_MIC_OSLO, STOCK_EX_MIC_PARIS);

  private static final String DOMAIN_NAME = "https://live.euronext.com/";
  private static final String DOMAIN_NAME_WITH_CHART = DOMAIN_NAME + "en/intraday_chart/getChartData/";
  private static final String DOMAIN_NAME_INTRA_LAST_PRICE = DOMAIN_NAME + "en/ajax/getDetailedQuote/";
  private static final String DOMAIN_NAME_INTRA_DETAILED_QUOTE = DOMAIN_NAME
      + "en/intraday_chart/getDetailedQuoteAjax/";
  private static final String PERIOD_1M = "1M";
  private static final String PERIOD_MAX = "max";
  private static final ObjectMapper objectMapper = JsonMapper.builder().build();
  /**
   * Passphrase of the CryptoJS encryption, taken from the script of {@code live.euronext.com}. It is no secret of GT
   * but part of the public page; when Euronext replaces it, {@link #decrypt(String)} fails and it has to be updated.
   */
  private static final String CRYPTO_PASSPHRASE = "24ayqVo7yJma";
  /** Key length of AES-256 in bytes. */
  private static final int KEY_LENGTH = 32;
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    mappingMicOperation.put(STOCK_EX_MIC_DUBLIN, STOCK_EX_OPERATION_DUBLIN);
  }

  public EuronextFeedConnector() {
    super(supportedFeed, "euronext", "Euronext", null, EnumSet.noneOf(UrlCheck.class));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.NON_INVESTABLE_INDICES, AssetclassCategory.EQUITIES,
        AssetclassCategory.FIXED_INCOME, AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND,
        AssetclassCategory.REAL_ESTATE_FUND, AssetclassCategory.ISSUER_RISK_PRODUCT);
    // The same exchanges the setting check accepts, so that no listing elsewhere is offered this connector
    parseGeoRestrictions(String.join(" ", EN_STOCK_EXCHANGES_SET));
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_INTRA_LAST_PRICE + getISINWithOperationMic(security);
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    scanLastPriceAndChange(security);
    scanDetailFull(security);
  }

  private void scanLastPriceAndChange(Security security) throws IOException {
    Document doc = getIntraDoc(getSecurityIntradayDownloadLink(security));
    final Element price = doc.select("#header-instrument-price").first();
    security.setSTimestamp(LocalDateTime.now().minusSeconds(getIntradayDelayedSeconds()));
    security.setSLast(FeedConnectorHelper.parseDoubleUS(price.text()));
    Elements spans = doc.select("div:containsOwn(Since Previous Close)").first().parent().select("span");
    security.setSChangePercentage(
        FeedConnectorHelper.parseDoubleUS((spans.get(1).text().replaceAll("[" + Pattern.quote("()%") + "]", ""))));
  }

  private void scanDetailFull(Security security) throws IOException {
    Document doc = getIntraDoc(DOMAIN_NAME_INTRA_DETAILED_QUOTE + getISINWithOperationMic(security) + "/full");
    Element table = doc.select("table").get(0);
    Elements rows = table.select("tr");
    for (Element row : rows) {
      Elements cols = row.select("td");
      switch (cols.get(0).text()) {
      case "Open":
        security.setSOpen(FeedConnectorHelper.parseDoubleUS(cols.get(1).text()));
        break;
      case "High":
        security.setSHigh(FeedConnectorHelper.parseDoubleUS(cols.get(1).text()));
        break;
      case "Low":
        security.setSLow(FeedConnectorHelper.parseDoubleUS(cols.get(1).text()));
        break;
      case "Previous Close":
        security.setSPrevClose(FeedConnectorHelper.parseDoubleUS(cols.get(1).text()));
        break;
      }
    }
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    return EnumSet.of(DownloadLink.DL_INTRA_FORCE_BACKEND, DownloadLink.DL_LAZY_INTRA);
  }

  @Override
  public String getContentOfPageRequest(String httpPageUrl) {
    String contentPage = null;
    try {
      return getIntraDoc(httpPageUrl).html();
    } catch (IOException e) {
      contentPage = "Failure!";
    }
    return contentPage;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 60;
  }

  /**
   * Requests an intraday HTML fragment the way the page does, by POST with the theme of the site, and decrypts it.
   *
   * @param url the endpoint including {@code {ISIN}-{MIC}}
   * @return the parsed fragment
   * @throws IOException when the request fails or the answer cannot be decrypted
   */
  private Document getIntraDoc(String url) throws IOException {
    String encryptedResponse = Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept-Language", "en").header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
        .requestBody("theme_name=euronext_live").method(org.jsoup.Connection.Method.POST).ignoreContentType(true)
        .execute().body();
    String decryptedHtml = decrypt(encryptedResponse);
    return Jsoup.parse(decryptedHtml);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getSecurityHistoricalDownloadLink(security, PERIOD_1M);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, String periodSuffix) {
    return DOMAIN_NAME_WITH_CHART + getISINWithOperationMic(security) + "/" + periodSuffix;
  }

  private String getISINWithOperationMic(final Security security) {
    return security.getIsin() + "-"
        + mappingMicOperation.getOrDefault(security.getStockexchange().getMic(), security.getStockexchange().getMic());
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend,
        errorMsgKey, feedIdentifier, specialInvestmentInstruments, assetclassType);
    if (securitycurrency instanceof Security security && (StringUtils.isBlank(security.getIsin())
        || !EN_STOCK_EXCHANGES_SET.contains(security.getStockexchange().getMic()))) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.euronext.setting.failure", null);
    }
    return clear;
  }

  @Override
  public boolean needHistoricalGapFiller(final Security security) {
    return true;
  }

  /**
   * Decrypts an answer of Euronext. The server wraps the actual content - JSON of the chart endpoint or an HTML fragment
   * of the quote endpoints - in the CryptoJS envelope {@code {"ct":…,"iv":…,"s":…}}:
   * <ul>
   * <li>{@code s}: hex salt, {@code iv}: hex initialisation vector, {@code ct}: Base64 cipher text;</li>
   * <li>the key is derived from {@link #CRYPTO_PASSPHRASE} and the salt with {@code EVP_BytesToKey} of OpenSSL: MD5
   * blocks {@code D1 = MD5(pass + salt)}, {@code Di = MD5(Di-1 + pass + salt)}, concatenated to 32 bytes;</li>
   * <li>the cipher is AES-256-CBC with PKCS#5 padding.</li>
   * </ul>
   * The plain text is itself a JSON string literal when the content is HTML and is unescaped then. An answer that is
   * not such an envelope is returned unchanged, so that an endpoint Euronext stops encrypting keeps working.
   *
   * @param response the body returned by Euronext
   * @return the decrypted content
   * @throws IOException when the envelope cannot be decrypted, e.g. because Euronext changed the passphrase
   */
  public static String decrypt(String response) throws IOException {
    if (response == null || !response.stripLeading().startsWith("{\"ct\"")) {
      return response;
    }
    try {
      final JsonSecurity jsonSecurity = objectMapper.readValue(response, JsonSecurity.class);
      byte[] salt = Hex.decodeHex(jsonSecurity.s.toCharArray());
      byte[] iv = Hex.decodeHex(jsonSecurity.iv);
      byte[] key = deriveKey(CRYPTO_PASSPHRASE.getBytes(StandardCharsets.UTF_8), salt);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      String decrypted = new String(cipher.doFinal(Base64.getDecoder().decode(jsonSecurity.ct)),
          StandardCharsets.UTF_8);
      return decrypted.startsWith("\"") ? objectMapper.readValue(decrypted, String.class) : decrypted;
    } catch (Exception e) {
      throw new IOException("Euronext answer could not be decrypted; the passphrase of live.euronext.com may have "
          + "changed: " + e.getMessage(), e);
    }
  }

  /**
   * {@code EVP_BytesToKey} of OpenSSL with MD5 and one iteration, as CryptoJS uses it for a passphrase.
   *
   * @param pass the passphrase
   * @param salt the salt of the envelope
   * @return the 32 byte AES key
   */
  private static byte[] deriveKey(byte[] pass, byte[] salt) throws NoSuchAlgorithmException {
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    byte[] key = new byte[KEY_LENGTH];
    byte[] block = null;
    int offset = 0;
    while (offset < KEY_LENGTH) {
      md5.reset();
      if (block != null) {
        md5.update(block);
      }
      md5.update(pass);
      md5.update(salt);
      block = md5.digest();
      int len = Math.min(block.length, KEY_LENGTH - offset);
      System.arraycopy(block, 0, key, offset, len);
      offset += len;
    }
    return key;
  }

  /**
   * Reads the daily closes. The chart endpoint knows only the periods {@code 1M} and {@code max}; a range starting more
   * than 30 days ago therefore loads the whole history, which is filtered to {@code from}..{@code to}.
   */
  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {

    final List<Historyquote> historyquotes = new ArrayList<>();
    String maxOr1M = ChronoUnit.DAYS.between(from, LocalDate.now()) > 30 ? PERIOD_MAX : PERIOD_1M;
    String url = getSecurityHistoricalDownloadLink(security, maxOr1M);
    String content = FeedConnectorHelper.getByHttpClient(url).body();
    content = decrypt(content);

    final DailyClose[] dailyCloseArr = objectMapper.readValue(content, DailyClose[].class);
    for (DailyClose dailyClose : dailyCloseArr) {
      LocalDate date = LocalDate.parse(dailyClose.time.substring(0, 10));
      if (!date.isBefore(from) && !date.isAfter(to)) {
        Historyquote historyquote = new Historyquote();
        historyquote.setClose(DataBusinessHelper.round(dailyClose.price));
        historyquote.setVolume(dailyClose.volume);
        historyquote.setDate(date);
        historyquotes.add(historyquote);
      }
    }
    return historyquotes;
  }

  /** One element of the chart answer: ISO time of the day, closing price and volume. */
  private static class DailyClose {
    public String time;
    public double price;
    public Long volume;
  }

  /** The CryptoJS envelope of an encrypted answer; see {@link EuronextFeedConnector#decrypt(String)}. */
  private static class JsonSecurity {
    public String ct;
    public String iv;
    public String s;
  }
}
