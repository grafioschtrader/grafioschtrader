package grafioschtrader.connector.instrument.euronext;

import java.io.IOException;
import java.security.spec.KeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
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
import jakarta.xml.bind.DatatypeConverter;

/**
 * The Euronext connector can obtain data from several different stock exchanges in Europe.
 *
 * A regex check of the URL extension is not necessary as it is not edited by the user. The connector for checking the
 * instrument would obviously always return an HTTP OK. Both checks are omitted.
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
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    mappingMicOperation.put(STOCK_EX_MIC_DUBLIN, STOCK_EX_OPERATION_DUBLIN);
  }

  public EuronextFeedConnector() {
    super(supportedFeed, "euronext", "Euronext", null, EnumSet.noneOf(UrlCheck.class));

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
    security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
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

  private Document getIntraDoc(String url) throws IOException {
    return Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT_HTTPCLIENT).header("Accept-Language", "en")
        .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
        .requestBody("theme_name=euronext_live").post();
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

  public static String decrypt(String ciphertext) {
    try {
      final JsonSecurity jsonSecurity = objectMapper.readValue(ciphertext, JsonSecurity.class);

      byte[] salt = Hex.decodeHex(jsonSecurity.s.toCharArray());
      byte[] iv = Hex.decodeHex(jsonSecurity.iv);

      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      KeySpec spec = new PBEKeySpec("24ayqVo7yJma".toCharArray(), salt, 1000, 256);
      SecretKey tmp = factory.generateSecret(spec);
      SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

      cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
      byte[] ct = DatatypeConverter.parseBase64Binary(jsonSecurity.ct);
      byte[] plaintext = cipher.doFinal(ct);

      return new String(plaintext, "UTF-8");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {

    final List<Historyquote> historyquotes = new ArrayList<>();
    final DateFormat dateFormat = new SimpleDateFormat(BaseConstants.STANDARD_LOCAL_DATE_TIME);
    objectMapper.setDateFormat(dateFormat);
    String maxOr1M = DateHelper.getDateDiff(from, new Date(), TimeUnit.DAYS) > 30 ? PERIOD_MAX : PERIOD_1M;
    String url = getSecurityHistoricalDownloadLink(security, maxOr1M);
    String content = FeedConnectorHelper.getByHttpClient(url).body();
    // content = decrypt(content);

    final DailyClose[] dailyCloseArr = objectMapper.readValue(content, DailyClose[].class);
    for (DailyClose dailyClose : dailyCloseArr) {
      Date date = DateHelper.setTimeToZeroAndAddDay(dailyClose.time, 0);
      if (!date.before(from) && !date.after(to)) {
        Historyquote historyquote = new Historyquote();
        historyquote.setClose(DataBusinessHelper.round(dailyClose.price));
        historyquote.setVolume(dailyClose.volume);
        historyquote.setDate(date);
        historyquotes.add(historyquote);
      }
    }
    return historyquotes;
  }

  private static class DailyClose {
    public Date time;
    public double price;
    public Long volume;
  }

  private static class JsonSecurity {
    public String ct;
    public String iv;
    public String s;
  }
}
