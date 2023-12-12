
package grafioschtrader.connector.instrument.yahoo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
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

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String DATE_FORMAT_SECURITY = "yy-MM-dd"; //$NON-NLS-1$
  private static final String DATE_FORMAT_SECURITY_OLD = "dd-MMM-yy"; //$NON-NLS-1$
  private static final String DATE_FORMAT_SPLIT = "yyyy-MM-dd";
  private static final String DIVDEND_EVENT = "dividend";
  private static final String SPLIT_EVENT = "split";
  private static final String URL_NORMAL_REGEX = "^\\^?[A-Za-z\\-0-9]+(\\.[A-Za-z]+)?$";
  private static final String URL_FOREX_REGEX = "^([A-Za-z]{6}=X)|([A-Za-z]{3}\\-[A-Za-z]{3})$";
  private static final String URL_COMMODITIES = "^[A-Za-z]+=F$";
  private static final String DOMAIN_NAME_WITH_7_VERSION = "https://query1.finance.yahoo.com/v7/finance/";
  private static final String DOMAIN_NAME_WITH_7_VERSION_Q2 = "https://query2.finance.yahoo.com/v7/finance/";
  private static final String DOMAIN_NAME_WITH_10_VERSION = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/";
  private static final boolean USE_V10_LASTPRICE = false;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public YahooFeedConnectorCOM() {
    super(supportedFeed, "yahoo", "Yahoo USA Finance", null, EnumSet.noneOf(UrlCheck.class));

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
    return "https://finance.yahoo.com/quote/" + symbol + "/history?p=" + symbol;
  }

  private <T extends Securitycurrency<T>> void readLastPriceWithCrumb(String urlStr, final T securitycurrency)
      throws ClientProtocolException, IOException {

    boolean success = false;
    for (int i = 0; i <= 2 && !success; i++) {
      i++;
      HttpClient client = HttpClientBuilder.create()
          .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
      HttpClientContext context = HttpClientContext.create();
      String cookie = CrumbManager.getCookie();
      HttpGet request = new HttpGet(urlStr + "&crumb=" + CrumbManager.getCrumb());
      request.addHeader("Cookie", cookie);
      request.addHeader("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);

      HttpResponse response = client.execute(request, context);
      HttpEntity entity = response.getEntity();

      if (urlStr.startsWith(DOMAIN_NAME_WITH_10_VERSION)) {
        success = processV10LastPrice(entity.getContent(), securitycurrency);
      } else {
        ;
        success = processV7LastPrice(entity.getContent(), securitycurrency);
      }

      if (!success) {
        CrumbManager.resetCookieCrumb();
      }
    }
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
      throws IOException, ParseException, URISyntaxException {

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
      final double divider) throws IOException, URISyntaxException {

    List<Historyquote> historyquotes = null;
    symbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
    CookieStore cookieStore = new BasicCookieStore();
    HttpClient client = HttpClientBuilder.create()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(cookieStore);
    historyquotes = getEodHistory(symbol, startDate, endDate, isCurrency, divider, client, context);

    return historyquotes;
  }

  private List<Historyquote> getEodHistory(String symbol, Date startDate, Date endDate, final boolean isCurrency,
      final double divider, HttpClient client, HttpClientContext context) throws IOException, URISyntaxException {

    List<Historyquote> historyquotes = null;
    String url = String.format(
        DOMAIN_NAME_WITH_7_VERSION + "download/%s?period1=%s&period2=%s&interval=1d&events=history", symbol,
        startDate.getTime() / 1000, endDate.getTime() / 1000 + 23 * 60 * 60);

    HttpGet request = new HttpGet(url);
    request.addHeader("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);
    HttpResponse response = client.execute(request, context);
    HttpEntity entity = response.getEntity();

    if (entity != null) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
        historyquotes = this.readBackfillStream(in, isCurrency, divider);
      }
    }
    HttpClientUtils.closeQuietly(response);
    return historyquotes;
  }

  private List<Historyquote> readBackfillStream(final BufferedReader in, final boolean isCurrency, final double divider)
      throws IOException {
    // first line is header
    final List<Historyquote> historyquotes = new ArrayList<>();
    final SimpleDateFormat dateFormatSecurity = new SimpleDateFormat(DATE_FORMAT_SECURITY);
    final SimpleDateFormat dateFormatSecurityOld = new SimpleDateFormat(DATE_FORMAT_SECURITY_OLD, Locale.US);

    String inputLine = in.readLine();
    while ((inputLine = in.readLine()) != null) {
      if (!Character.isDigit(inputLine.charAt(0))) {
        continue;
      }
      try {
        final Historyquote historyquote = parseResponseLine(inputLine, dateFormatSecurity, dateFormatSecurityOld,
            isCurrency, divider);
        if (historyquote != null && isDifferentDay(historyquotes, historyquote)) {
          historyquotes.add(historyquote);
        }
      } catch (final ParseException e) {
        log.error("Error parsing data: " + inputLine);
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
    double divider = FeedConnectorHelper.getGBXLondonDivider(security);
    return getDividendSplitHistory(security, fromDate, LocalDate.now(), DIVDEND_EVENT,
        (in, idSecurity, dividends, currency) -> {
          String inputLine = in.readLine();
          while ((inputLine = in.readLine()) != null) {
            if (!Character.isDigit(inputLine.charAt(0))) {
              continue;
            }
            String[] values = inputLine.split(",");
            LocalDate exdDate = LocalDate.parse(values[0]);
            Double amountAdjusted = Double.parseDouble(values[1]);
            if (amountAdjusted > 0.0) {
              dividends.add(new Dividend(idSecurity, exdDate, null, null, amountAdjusted / divider, currency,
                  CreateType.CONNECTOR_CREATED));
            }
          }
        });
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getSplitHistoricalDownloadLink(security.getUrlSplitExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), LocalDate.now(), SPLIT_EVENT);
  }

  private String getSplitHistoricalDownloadLink(String symbol, LocalDate fromDate, LocalDate toDate, String event) {
    return String.format(
        DOMAIN_NAME_WITH_7_VERSION + "download/%s?period1=%s&period2=%s&interval=1d&events=" + event
            + "&includeAdjustedClose=true",
        symbol, DateHelper.LocalDateToEpocheSeconds(fromDate),
        DateHelper.LocalDateToEpocheSeconds(toDate) + (24 * 60 * 60) - 1);
  }

  // TODO
  @Override
  protected void checkUrl(String url, String failureMsgKey, FeedSupport feedSupport) {

  }

  @Override
  public List<Securitysplit> getSplitHistory(final Security security, LocalDate fromDate, LocalDate toDate)
      throws Exception {
    return getDividendSplitHistory(security, fromDate, toDate, SPLIT_EVENT,
        (in, idSecurity, securitysplits, currency) -> {
          final SimpleDateFormat dateFormatSplit = new SimpleDateFormat(DATE_FORMAT_SPLIT, Locale.US);
          FractionFormat fractionFormat = new FractionFormat(NumberFormat.getInstance(Locale.US));
          String inputLine = in.readLine();
          while ((inputLine = in.readLine()) != null) {
            if (!Character.isDigit(inputLine.charAt(0))) {
              continue;
            }
            String[] values = inputLine.split(",");
            Date splitDate = dateFormatSplit.parse(values[0]);
            String fractionStr = values[1].replace(":", "/").replaceAll("\\s+", "");
            Fraction fraction = fractionFormat.parse(fractionStr);
            securitysplits.add(new Securitysplit(idSecurity, splitDate, fraction.getDenominator(),
                fraction.getNumerator(), CreateType.CONNECTOR_CREATED));
          }
        });
  }

  private <S> List<S> getDividendSplitHistory(Security security, LocalDate fromDate, LocalDate toDate, String event,
      IReadData<S> reader) throws Exception {
    List<S> records = new ArrayList<>();
    CookieStore cookieStore = new BasicCookieStore();
    HttpClient client = HttpClientBuilder.create()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(cookieStore);
    String symbol = URLEncoder.encode(
        event.equals(SPLIT_EVENT) ? security.getUrlSplitExtend() : security.getUrlDividendExtend(),
        StandardCharsets.UTF_8);

    String url = this.getSplitHistoricalDownloadLink(symbol, fromDate, toDate, event);
    HttpGet request = new HttpGet(url);
    request.addHeader("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);

    HttpResponse response = client.execute(request, context);
    HttpEntity entity = response.getEntity();

    if (entity != null) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()))) {
        reader.readData(in, security.getIdSecuritycurrency(), records, security.getCurrency());
      }
    }
    HttpClientUtils.closeQuietly(response);

    return records;
  }

  /**
   * Yahoo creates sometimes more than only one quote for the same day.
   *
   * @param historyquotes
   * @param historyquote
   * @return
   */
  private boolean isDifferentDay(final List<Historyquote> historyquotes, final Historyquote historyquote) {
    return historyquotes.size() < 1 || historyquotes.size() > 0
        && !historyquote.getDate().equals(historyquotes.getLast().getDate());
  }

  private Historyquote parseResponseLine(final String inputLine, final SimpleDateFormat dateFormatSecurity,
      final SimpleDateFormat dateFormatSecurityOld, boolean isCurrency, final double divider) throws ParseException {
    final String[] item = inputLine.split(","); //$NON-NLS-1$
    if (item.length < 7 || !item[4].matches("[0-9.]*")) {
      return null;
    }

    final Calendar day = Calendar.getInstance();
    try {
      day.setTime(dateFormatSecurity.parse(item[0]));
    } catch (final ParseException e) {
      try {
        day.setTime(dateFormatSecurityOld.parse(item[0]));
      } catch (final ParseException e1) {
        throw e1;
      }
    }

    DateHelper.setTimeToZero(day);

    final Historyquote historyQuote = new Historyquote();
    historyQuote.setDate(day.getTime());
    historyQuote.setOpen(parseAndRound(item[1], divider));
    historyQuote.setHigh(parseAndRound(item[2], divider));
    historyQuote.setLow(parseAndRound(item[3], divider));
    historyQuote.setClose(parseAndRound(item[4], divider));
    if (!isCurrency) {
      historyQuote.setVolume(Long.parseLong(item[6]));
    }

    return historyQuote;

  }

  private double parseAndRound(final String doubleValueStr, final double divider) {
    final double value = Double.parseDouble(doubleValueStr);
    return DataHelper.round(value / divider);
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

  private interface IReadData<S> {
    void readData(final BufferedReader in, Integer idSecurity, List<S> securitysplits, String currency)
        throws IOException, ParseException;
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

}
