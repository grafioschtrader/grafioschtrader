package grafioschtrader.connector.instrument.finnhub;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import grafiosch.common.DateHelper;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.connector.fundamental.FundamentalAnswerMapper;
import grafioschtrader.connector.fundamental.FundamentalFeedSupport;
import grafioschtrader.connector.fundamental.FundamentalFeedSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.IFundamentalFeedConnector;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.SubscriptionType;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finnhub connector
 *
 * Stock, Bond, ETF:<br>
 * Splits were once free but anymore. The implementation was not removed.
 *
 * <p>
 * It is also a source of fundamental base values ({@code stock/metric}). Only US listings are asked, whatever the
 * subscription: the free plan refuses every other symbol, and the symbol scheme of a paid plan is not measured.
 * </p>
 */
@Component
public class FinnhubConnector extends BaseFeedApiKeyConnector implements IFundamentalFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private final static String TOKEN_PARAM_NAME = "token";

  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    // supportedFeed.put(FeedSupport.SPLIT, new FeedIdentifier[] {
    // FeedIdentifier.SPLIT_URL });
  }

  private static final String DOMAIN_NAME_WITH_VERSION = "https://finnhub.io/api/v1/";

  public FinnhubConnector() {
    super(supportedFeed, "finnhub", "Finnhub", null, EnumSet.noneOf(UrlCheck.class));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.ETF);
    parseGeoRestrictions("US");
  }

  /**
   * The lookup offers this connector for a listing on NASDAQ or NYSE with the exchange ticker another provider found,
   * see {@link FeedConnectorHelper#deriveUsTicker(SecurityBaseDataDto)}.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    return FeedConnectorHelper.deriveUsTicker(baseData);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate toDate = LocalDate.now();
    LocalDate fromDate = toDate.minusDays(7);
    return getSecurityHistoricalDownloadLink(security, fromDate, toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, LocalDate from, LocalDate to) {
    return DOMAIN_NAME_WITH_VERSION + "stock/candle?symbol=" + security.getUrlHistoryExtend().toUpperCase() + "&from="
        + DateHelper.LocalDateToEpocheSeconds(from) + "&to=" + DateHelper.LocalDateToEpocheSeconds(to) + "&resolution=D"
        + getTokenParam();
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_VERSION + "quote?symbol=" + security.getUrlIntraExtend().toUpperCase() + getTokenParam();
  }

  private String getTokenParam() {
    return "&" + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    URL url = new URI(getSecurityHistoricalDownloadLink(security, from, to)).toURL();
    final CandleData candleData = objectMapper.readValue(url.openStream(), CandleData.class);

    if (candleData.s.equals("ok")) {
      for (int i = 0; i < candleData.t.length; i++) {
        LocalDate date = Instant.ofEpochSecond(candleData.t[i]).atZone(ZoneId.systemDefault()).toLocalDate();
        if (!date.isBefore(from) && !date.isAfter(to)) {
          final Historyquote histroyquote = new Historyquote();
          historyquotes.add(histroyquote);
          histroyquote.setDate(date);
          histroyquote.setClose(candleData.c[i]);
          histroyquote.setOpen(candleData.o[i]);
          histroyquote.setLow(candleData.l[i]);
          histroyquote.setHigh(candleData.h[i]);
          histroyquote.setVolume(candleData.v[i]);
        }
      }
    }
    return historyquotes;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    var urlStr = UriUtils.encodeFragment(getSecurityIntradayDownloadLink(security), StandardCharsets.UTF_8);
    URL url = new URI(urlStr).toURL();
    final Quote quote = objectMapper.readValue(url.openStream(), Quote.class);
    security.setSLast(quote.c);
    security.setSOpen(quote.o);
    security.setSLow(quote.l);
    security.setSHigh(quote.h);
    security.setSPrevClose(quote.pc);
    security.setSTimestamp(LocalDateTime.ofInstant(Instant.ofEpochSecond(quote.t), ZoneId.systemDefault()));
    security.setSChangePercentage(quote.dp);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 0;
  }

  @Override
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, TOKEN_PARAM_NAME);
  }

  @Override
  public <S extends Securitycurrency<S>> void hasAPISubscriptionSupport(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport) {
    if (getSubscriptionType() == SubscriptionType.FINNHUB_FREE && securitycurrency instanceof Security security) {
      if (!security.getStockexchange().getCountryCode().equals(Locale.US.getCountry())) {
        throw new GeneralNotTranslatedWithArgumentsException("gt.connector.subscription.failure",
            new Object[] { getReadableName(), feedSupport.name() });
      }
    }
  }

  @Override
  public FundamentalSource getFundamentalSource() {
    return FundamentalSource.FINNHUB;
  }

  @Override
  public boolean isFundamentalAvailable() {
    return isActivated();
  }

  @Override
  public boolean supportsFundamentals(Security security) {
    return FundamentalFeedSupport.isUsListing(security) && fundamentalSymbol(security) != null;
  }

  @Override
  public Duration getFundamentalPause() {
    // The free plan allows 60 requests per minute.
    return Duration.ofMillis(1100);
  }

  @Override
  public FundamentalAnswer loadFundamentals(Security security) throws Exception {
    String symbol = fundamentalSymbol(security);
    HttpAnswer answer = FundamentalFeedSupport.get(DOMAIN_NAME_WITH_VERSION + "stock/metric?metric=all&symbol="
        + UriUtils.encodeQueryParam(symbol, StandardCharsets.UTF_8) + getTokenParam(), Map.of());
    if (answer.status() != 200) {
      return FundamentalAnswer.failed("HTTP " + answer.status() + " for " + symbol, answer.status());
    }
    return FundamentalAnswerMapper.finnhub(FundamentalFeedSupport.parse(answer.body()),
        security.getIdSecuritycurrency(), security.getCurrency(), LocalDateTime.now(ZoneOffset.UTC));
  }

  /** The connector's own extension when the security is priced by Finnhub, otherwise the ticker. */
  private String fundamentalSymbol(Security security) {
    if (getID().equals(security.getIdConnectorIntra()) && security.getUrlIntraExtend() != null
        && !security.getUrlIntraExtend().isBlank()) {
      return security.getUrlIntraExtend().trim().toUpperCase(Locale.ROOT);
    }
    return security.getTickerSymbol() == null || security.getTickerSymbol().isBlank() ? null
        : security.getTickerSymbol().trim().toUpperCase(Locale.ROOT);
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getSplitHistoricalDownloadLink(security, LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY),
        LocalDate.now());
  }

  private String getSplitHistoricalDownloadLink(Security security, LocalDate from, LocalDate to) {
    return DOMAIN_NAME_WITH_VERSION + "stock/split?symbol=" + security.getUrlSplitExtend().toUpperCase() + "&from="
        + from + "&to=" + to + getTokenParam();
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception {
    URL url = new URI(getSplitHistoricalDownloadLink(security, fromDate, toDate)).toURL();
    final List<Securitysplit> securitysplits = new ArrayList<>();
    final Split[] splits = objectMapper.readValue(url.openStream(), Split[].class);
    for (Split split : splits) {
      Securitysplit securitysplit = new Securitysplit(security.getIdSecuritycurrency(), split.date, split.fromFactor,
          split.toFactor, CreateType.CONNECTOR_CREATED);
      securitysplits.add(securitysplit);

    }
    return securitysplits;
  }

  private static class CandleData {
    /**
     * List of close prices for returned candles.
     */
    public double[] c;

    /**
     * List of high prices for returned candles.
     */
    public double[] h;
    /**
     * List of low prices for returned candles.
     */
    public double[] l;

    /**
     * List of open prices for returned candles.
     */
    public double[] o;
    /**
     * Status of the response. This field can either be ok or no_data.
     */
    public String s;
    /**
     * List of timestamp for returned candles.
     */
    public long[] t;
    /**
     * List of volume data for returned candles.
     */
    public long[] v;

  }

  private static class Quote {
    public double o;
    public double h;
    // public double d;
    public double dp;
    public double l;
    public double c;
    public double pc;
    public long t;

  }

  private static class Split {
    // public String symbol;
    public LocalDate date;
    public int fromFactor;
    public int toFactor;

  }

}
