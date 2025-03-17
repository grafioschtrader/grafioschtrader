
package grafioschtrader.connector.instrument.yahoo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*-
 * Stock, Bond, ETF:
 * There is a regex pattern check for the URL. The URL check with connection establishment cannot
 * be used as the download links are not used for downloading the data.
 *
 * Dividend:
 * Dividend data may not include all payments, see IEBB.SW for example.
 *
 * Split:
 *
 */
@Component
public class YahooFeedConnectorCOM extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String DIVDEND_EVENT = "div";
  private static final String SPLIT_EVENT = "splits";
  private static final String URL_NORMAL_REGEX = "^\\^?[A-Za-z\\-0-9]+(\\.[A-Za-z]+)?$";
  private static final String URL_FOREX_REGEX = "^([A-Za-z]{6}=X)|([A-Za-z]{3}\\-[A-Za-z]{3})$";
  private static final String URL_COMMODITIES = "^[A-Za-z]+=F$";

  private static final String DOMAIN_NAME_WITH_7_VERSION_Q2 = "https://query2.finance.yahoo.com/v7/finance/";
  private static final String DOMAIN_NAME_WITH_8_VERSION_Q2 = "https://query2.finance.yahoo.com/v8/finance/";
  private static final String DOMAIN_NAME_WITH_10_VERSION = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/";
  private static final boolean USE_V10_LASTPRICE = false;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public YahooFeedConnectorCOM() {
    super(supportedFeed, YahooHelper.YAHOO, "Yahoo USA Finance", null, EnumSet.noneOf(UrlCheck.class));

  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    String symbol = URLEncoder.encode(security.getUrlIntraExtend(), StandardCharsets.UTF_8);
    return USE_V10_LASTPRICE ? DOMAIN_NAME_WITH_10_VERSION + symbol + "?modules=price"
        : DOMAIN_NAME_WITH_7_VERSION_Q2 + "quote?symbols=" + symbol;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    readLastPriceWithCrumb(getSecurityIntradayDownloadLink(security), security);
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return USE_V10_LASTPRICE ? DOMAIN_NAME_WITH_10_VERSION + getCurrencyPairSymbol(currencypair) + "?modules=price"
        : DOMAIN_NAME_WITH_7_VERSION_Q2 + "quote?symbols=" + getCurrencyPairSymbol(currencypair);
  }

  @Override
  public String getContentOfPageRequest(String httpPageUrl) {
    String contentPage = null;
    InputStream is = null;
    try {
      is = loadContentWithCrump(httpPageUrl);
      contentPage = IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (Exception e) {
      contentPage = "Failure!";
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        contentPage = "Failure!";
      }
    }
    return contentPage;
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    readLastPriceWithCrumb(getCurrencypairIntradayDownloadLink(currencypair), currencypair);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getHistoricalDownloadLink(security.getUrlHistoryExtend());
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return getHistoricalDownloadLink(getCurrencyPairSymbol(currencypair));
  }

  private String getCurrencyPairSymbol(final Currencypair currencypair) {
    if (GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(currencypair.getFromCurrency())
        || GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(currencypair.getToCurrency())) {
      return currencypair.getFromCurrency() + "-" + currencypair.getToCurrency();
    } else {
      return currencypair.getFromCurrency() + currencypair.getToCurrency() + "=X";
    }
  }

  private String getHistoricalDownloadLink(final String symbol) {
    return YahooHelper.YAHOO_FINANCE_QUOTE + symbol + "/history?p=" + symbol;
  }

  private <T extends Securitycurrency<T>> void readLastPriceWithCrumb(String urlStr, final T securitycurrency)
      throws ClientProtocolException, IOException {
    boolean success = false;
    for (int i = 0; i <= 2 && !success; i++) {
      i++;
      InputStream is = loadContentWithCrump(urlStr);
      if (urlStr.startsWith(DOMAIN_NAME_WITH_10_VERSION)) {
        success = processV10LastPrice(is, securitycurrency);
      } else {
        success = processV7LastPrice(is, securitycurrency);
      }
      if (!success) {
        CrumbManager.resetCookieCrumb();
      }
    }
  }

  private InputStream loadContentWithCrump(String urlStr) throws ClientProtocolException, IOException {
    HttpClient client = HttpClientBuilder.create()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    HttpClientContext context = HttpClientContext.create();
    String cookie = CrumbManager.getCookie();
    HttpGet request = new HttpGet(urlStr + "&crumb=" + CrumbManager.getCrumb());
    request.addHeader("Cookie", cookie);
    request.addHeader("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);

    HttpResponse response = client.execute(request, context);
    HttpEntity entity = response.getEntity();
    return entity.getContent();
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    return EnumSet.of(DownloadLink.DL_INTRA_FORCE_BACKEND, DownloadLink.DL_LAZY_INTRA);
  }

  private <T extends Securitycurrency<T>> boolean processV7LastPrice(InputStream is, final T securitycurrency)
      throws StreamReadException, DatabindException, IOException {
    Quote quote = objectMapper.readValue(is, Quote.class);
    if (quote.quoteResponse != null) {
      setQuoteResult(quote, securitycurrency);
      return true;
    }
    return false;
  }

  private <T extends Securitycurrency<T>> boolean processV10LastPrice(InputStream is, final T securitycurrency)
      throws StreamReadException, DatabindException, IOException {
    final QuoteSummaryV10 quoteSummary = objectMapper.readValue(is, QuoteSummaryV10.class);
    if (quoteSummary.quoteSummary != null) {
      setQuoteSummary(quoteSummary, securitycurrency);
      return true;
    }
    return false;
  }

  private <T extends Securitycurrency<T>> void setQuoteSummary(final QuoteSummaryV10 quoteSummary,
      final T securitycurrency) {
    if (quoteSummary.quoteSummary.result.length == 1) {
      double divider = FeedConnectorHelper.getGBXLondonDivider(securitycurrency);
      Price price = quoteSummary.quoteSummary.result[0].price;
      securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
      securitycurrency.setSLast(price.regularMarketPrice.raw / divider);
      securitycurrency.setSHigh(price.regularMarketDayHigh.raw / divider);
      securitycurrency.setSLow(price.regularMarketDayLow.raw / divider);
      securitycurrency.setSOpen(price.regularMarketOpen.raw / divider);
      securitycurrency.setSChangePercentage(price.regularMarketChangePercent.raw * 100);
      if (securitycurrency instanceof Security s) {
        s.setSVolume(price.regularMarketVolume.raw);
      }
    }
  }

  private <T extends Securitycurrency<T>> void setQuoteResult(final Quote quote, final T securitycurrency) {
    if (quote.quoteResponse.result.length == 1) {
      double divider = FeedConnectorHelper.getGBXLondonDivider(securitycurrency);
      final Result result = quote.quoteResponse.result[0];
      securitycurrency.setSTimestamp(new Date(System.currentTimeMillis() - result.sourceInterval * 60 * 1000));
      securitycurrency.setSLast(result.regularMarketPrice / divider);
      securitycurrency.setSHigh(result.regularMarketDayHigh / divider);
      securitycurrency.setSLow(result.regularMarketDayLow / divider);
      securitycurrency.setSOpen(result.regularMarketOpen / divider);
      securitycurrency.setSChangePercentage(result.regularMarketChangePercent);
      securitycurrency.setSPrevClose(result.regularMarketPreviousClose / divider);
      if (securitycurrency instanceof Security) {
        ((Security) securitycurrency).setSVolume(result.regularMarketVolume);
      }
    }
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return this.getEodHistory(security.getUrlHistoryExtend(), from, to, false,
        FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {

    return FeedConnectorHelper.checkFirstLastHistoryquoteAndRemoveWhenOutsideDateRange(from, to,
        getEodHistory(getCurrencyPairSymbol(currencyPair), from, to, true, 1.0), currencyPair.getName());
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend,
        errorMsgKey, feedIdentifier, specialInvestmentInstruments, assetclassType);
    if (!clear) {
      switch (specialInvestmentInstruments) {
      case CFD, NON_INVESTABLE_INDICES:
        checkUrlExtendsionWithRegex(new String[] { URL_NORMAL_REGEX, URL_COMMODITIES }, urlExtend);
        break;
      case FOREX:
        checkUrlExtendsionWithRegex(new String[] { URL_FOREX_REGEX }, urlExtend);
        break;
      default:
        checkUrlExtendsionWithRegex(new String[] { URL_NORMAL_REGEX }, urlExtend);
      }
    }
    return clear;
  }

  @Override
  protected boolean isConnectionOk(HttpURLConnection huc) {
    return !huc.getURL().getPath().contains("lookup");
  }

  private List<Historyquote> getEodHistory(String symbol, Date startDate, Date endDate, final boolean isCurrency,
      final double divider) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
    symbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);

    List<Historyquote> historyquotes = new ArrayList<>();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    String urlStr = String.format(
        DOMAIN_NAME_WITH_8_VERSION_Q2 + "chart/%s?period1=%s&period2=%s&interval=1d&events=history", symbol,
        startDate.getTime() / 1000, endDate.getTime() / 1000 + 23 * 60 * 60);

    final TopLevelChart topLevelChart = objectMapper.readerWithView(Views.TimestampIndicatorsView.class)
        .forType(TopLevelChart.class).readValue(FeedConnectorHelper.getByHttpClient(urlStr).body());
    ResultData resultData = topLevelChart.chart.result.get(0);
    List<Long> timestamps = resultData.timestamp;
    Quotes quotes = resultData.indicators.quote.get(0);
    for (int i = 0; i < timestamps.size(); i++) {
      Historyquote historyquote = new Historyquote();
      if (quotes.close.get(i) != null) {
        historyquotes.add(historyquote);
        historyquote.setClose(quotes.close.get(i) / divider);
        historyquote.setHigh(quotes.high.get(i) / divider);
        historyquote.setLow(quotes.low.get(i) / divider);
        historyquote.setOpen(quotes.open.get(i) / divider);
        historyquote.setVolume(quotes.volume.get(i));
        historyquote.setDate(DateHelper.setTimeToZeroAndAddDay(new Date(timestamps.get(i) * 1000), 0));
      }
    }
    return historyquotes;
  }

  @Override
  public boolean isDividendSplitAdjusted() {
    return true;
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return getSplitHistoricalDownloadLink(security.getUrlSplitExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), LocalDate.now(), DIVDEND_EVENT);
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    final double divider = FeedConnectorHelper.getGBXLondonDivider(security);

    Events events = getSplitDividendEvent(security.getUrlDividendExtend(), fromDate, LocalDate.now(), DIVDEND_EVENT);
    return events.dividends.entrySet().stream()
        .map(entry -> new Dividend(security.getIdSecuritycurrency(),
            Instant.ofEpochSecond(entry.getValue().date)
            .atZone(ZoneId.systemDefault())
            .toLocalDate(), null, null, entry.getValue().amount / divider,
            security.getCurrency(), CreateType.CONNECTOR_CREATED))
        .collect(Collectors.toList());
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getSplitHistoricalDownloadLink(security.getUrlSplitExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), LocalDate.now(), SPLIT_EVENT);
  }

  private String getSplitHistoricalDownloadLink(String symbol, LocalDate fromDate, LocalDate toDate, String event) {
    return String.format(
        DOMAIN_NAME_WITH_8_VERSION_Q2 + "chart/%s?period1=%s&period2=%s&interval=1d&events=" + event
            + "&includeAdjustedClose=true",
        symbol, DateHelper.LocalDateToEpocheSeconds(fromDate),
        DateHelper.LocalDateToEpocheSeconds(toDate) + (24 * 60 * 60) - 1);
  }

  // TODO
  @Override
  protected void checkUrl(String url, String failureMsgKey, FeedSupport feedSupport) {

  }
  
  private Events getSplitDividendEvent(String urlExtend, LocalDate fromDate, LocalDate toDate, String event) throws Exception {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final TopLevelChart topLevelChart = objectMapper.readerWithView(Views.EventsView.class).forType(TopLevelChart.class)
        .readValue(FeedConnectorHelper
            .getByHttpClient(getSplitHistoricalDownloadLink(urlExtend, fromDate, toDate, event))
            .body());
    return topLevelChart.chart.result.get(0).events;
  }

  @Override
  public List<Securitysplit> getSplitHistory(final Security security, LocalDate fromDate, LocalDate toDate)
      throws Exception {
    Events events = getSplitDividendEvent(security.getUrlSplitExtend(), fromDate, toDate, SPLIT_EVENT);
    return events.splits.entrySet().stream()
        .map(entry -> new Securitysplit(security.getIdSecuritycurrency(),
            DateHelper.setTimeToZeroAndAddDay(new Date(entry.getValue().date * 1000), 0), entry.getValue().denominator,
            entry.getValue().numerator, CreateType.CONNECTOR_CREATED))
        .collect(Collectors.toList());
  }
  
  static class Quote {
    public QuoteResponse quoteResponse;
  }

  static class QuoteResponse {
    public Result result[];
    public String error;
  }

  static class Result {
    public double regularMarketPrice;
    public double regularMarketChangePercent;
    public double regularMarketPreviousClose;
    public double regularMarketOpen;
    public double regularMarketDayHigh;
    public double regularMarketDayLow;
    public int sourceInterval;
    public String exchangeTimezoneName;
    public String exchangeTimezoneShortName;
    public Long regularMarketVolume;
  }

  
  static class QuoteSummaryV10 {
    public QuoteSummary quoteSummary;
  }

  static class QuoteSummary {
    public PriceV10 result[];
  }

  static class PriceV10 {
    public Price price;
  }

  static class Price {
    public RegularMarketChangePercent regularMarketChangePercent;
    public RegularMarketPrice regularMarketPrice;
    public RegularMarketDayHigh regularMarketDayHigh;
    public RegularMarketDayLow regularMarketDayLow;
    public RegularMarketOpen regularMarketOpen;
    public RegularMarketVolume regularMarketVolume;
  }

  static class RegularMarketChangePercent {
    public double raw;
  }

  static class RegularMarketPrice {
    public double raw;
  }

  static class RegularMarketDayHigh {
    public double raw;
  }

  static class RegularMarketDayLow {
    public double raw;
  }

  static class RegularMarketOpen {
    public double raw;
  }

  static class RegularMarketVolume {
    public Long raw;
  }

  /// EOD Historical
  ////////////////////////////////////
  static class Views {
    static class EventsView {
    }

    static class TimestampIndicatorsView {
    }
  }

  static class TopLevelChart {
    @JsonView({ Views.EventsView.class, Views.TimestampIndicatorsView.class })
    public ChartData chart;
  }

  static class ChartData {
    @JsonView({ Views.EventsView.class, Views.TimestampIndicatorsView.class })
    public List<ResultData> result;
    @JsonView({ Views.EventsView.class, Views.TimestampIndicatorsView.class })
    public String error;
  }

  static class ResultData {
    @JsonView({ Views.EventsView.class, Views.TimestampIndicatorsView.class })
    public Meta meta;
    @JsonView(Views.TimestampIndicatorsView.class)
    public List<Long> timestamp;
    @JsonView(Views.TimestampIndicatorsView.class)
    public Indicators indicators;
    @JsonView(Views.EventsView.class)
    public Events events;
  }

  static class Meta {
    public String currency;
    public String symbol;
    public String exchangeName;
    public String fullExchangeName;
    public String instrumentType;
    public long firstTradeDate;
    public long regularMarketTime;
    public boolean hasPrePostMarketData;
    public int gmtoffset;
    public String timezone;
    public String exchangeTimezoneName;
    public double regularMarketPrice;
    public double fiftyTwoWeekHigh;
    public double fiftyTwoWeekLow;
    public double regularMarketDayHigh;
    public double regularMarketDayLow;
    public long regularMarketVolume;
    public String longName;
    public String shortName;
    public double chartPreviousClose;
    public int priceHint;
    public CurrentTradingPeriod currentTradingPeriod;
    public String dataGranularity;
    public String range;
    public List<String> validRanges;
  }

  static class CurrentTradingPeriod {
    public TimePeriod pre;
    public TimePeriod regular;
    public TimePeriod post;
  }

  static class TimePeriod {
    public String timezone;
    public long start;
    public long end;
    public int gmtoffset;
  }

  static class Indicators {
    public List<Quotes> quote;
    public List<AdjClose> adjclose;
  }

  static class Quotes {
    public List<Double> high;
    public List<Long> volume;
    public List<Double> low;
    public List<Double> open;
    public List<Double> close;
  }

  static class AdjClose {
    public List<Double> adjclose;
  }

  static class Events {
    @JsonView(Views.EventsView.class)
    public Map<String, Split> splits;
    @JsonView(Views.EventsView.class)
    public Map<String, DividendJson> dividends;
  }

  static class Split {
    public long date;
    public int numerator;
    public int denominator;
    public String splitRatio;

  }

  static class DividendJson {
    public long date;
    public double amount;
  }

}
