
package grafioschtrader.connector.instrument.yahoo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
  private static final String DOMAIN_NAME_WITH_VERSION = "https://query1.finance.yahoo.com/v7/finance/";
  private static boolean USE_CRUMB = false;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public YahooFeedConnectorCOM() {
    super(supportedFeed, "yahoo", "Yahoo USA Finance", null);

  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_VERSION + "quote?symbols=" + security.getUrlIntraExtend();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {

    // https://query1.finance.yahoo.com/v7/finance/quote?symbols=DBK.DE

    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final URL url = new URL(getSecurityIntradayDownloadLink(security));
    setQuoteResult(url, security);
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return DOMAIN_NAME_WITH_VERSION + "quote?symbols=" + getCurrencyPairSymbol(currencypair);
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    // https://query1.finance.yahoo.com/v7/finance/quote?symbols=USDGBP=X
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final URL url = new URL(getCurrencypairIntradayDownloadLink(currencypair));
    setQuoteResult(url, currencypair);
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

  private <T extends Securitycurrency<T>> void setQuoteResult(final URL url, final T securitycurrency)
      throws IOException {
    final Quote quote = objectMapper.readValue(url, Quote.class);
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
  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend, errorMsgKey,
        feedIdentifier, specialInvestmentInstruments, assetclassType);
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
    symbol = URLEncoder.encode(symbol, "UTF-8");
    CookieStore cookieStore = new BasicCookieStore();
    HttpClient client = HttpClientBuilder.create()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(cookieStore);
    if (USE_CRUMB) {

      String crumb = getCrumb(symbol, client, context);
      if (crumb != null && !crumb.isEmpty()) {
        historyquotes = getEodHistory(symbol, startDate, endDate, isCurrency, divider, client, context, crumb);
      }
    } else {
      historyquotes = getEodHistory(symbol, startDate, endDate, isCurrency, divider, client, context, null);
    }
    return historyquotes;
  }

  private List<Historyquote> getEodHistory(String symbol, Date startDate, Date endDate, final boolean isCurrency,
      final double divider, HttpClient client, HttpClientContext context, String crumb)
      throws IOException, URISyntaxException {

    List<Historyquote> historyquotes = null;
    String url = String.format(
        DOMAIN_NAME_WITH_VERSION + "download/%s?period1=%s&period2=%s&interval=1d&events=history&crumb=%s", symbol,
        startDate.getTime() / 1000, endDate.getTime() / 1000 + 23 * 60 * 60, crumb);
    if (crumb != null) {
      url += url + "&crumb" + crumb;
    }

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

  private String getCrumb(String symbol, HttpClient client, HttpClientContext context) throws IOException {
    return findCrumb(splitPageData(getPage(symbol, client, context)));
  }

  private String findCrumb(List<String> lines) {
    String crumb = "";
    String rtn = "";
    for (String l : lines) {
      if (l.indexOf("CrumbStore") > -1) {
        rtn = l;
        break;
      }
    }
    // ,"CrumbStore":{"crumb":"OKSUqghoLs8"
    if (rtn != null && !rtn.isEmpty()) {
      String[] vals = rtn.split(":"); // get third item
      crumb = vals[2].replace("\"", ""); // strip quotes
      crumb = StringEscapeUtils.unescapeJava(crumb); // unescape escaped values (particularly, \u002f
    }
    return crumb;
  }

  private List<String> splitPageData(String page) {
    // Return the page as a list of string using } to split the page
    return Arrays.asList(page.split("}"));
  }

  private String getPage(String symbol, HttpClient client, HttpClientContext context) throws IOException {
    String rtn = null;
    String url = String.format("https://finance.yahoo.com/quote/%s/?p=%s", symbol, symbol);
    HttpGet request = new HttpGet(url);

    request.addHeader("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);

    HttpResponse response = client.execute(request, context);

    BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
    StringBuffer result = new StringBuffer();
    String line = "";
    while ((line = rd.readLine()) != null) {
      result.append(line);
    }
    rtn = result.toString();
    HttpClientUtils.closeQuietly(response);

    return rtn;
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
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), DIVDEND_EVENT, null);
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    double divider = FeedConnectorHelper.getGBXLondonDivider(security);
    return getDividendSplitHistory(security, fromDate, DIVDEND_EVENT, (in, idSecurity, dividends, currency) -> {
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
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), SPLIT_EVENT, null);
  }

  private String getSplitHistoricalDownloadLink(String symbol, LocalDate fromDate, String event, String crumb) {
    return String.format(
        DOMAIN_NAME_WITH_VERSION + "download/%s?period1=%s&period2=%s&interval=1d&events=" + event
            + "&includeAdjustedClose=true&crumb=%s",
        symbol, DateHelper.LocalDateToEpocheSeconds(fromDate), new Date().getTime() / 1000 + 23 * 60 * 60, crumb);
  }

  @Override
  public List<Securitysplit> getSplitHistory(final Security security, LocalDate fromDate) throws Exception {
    return getDividendSplitHistory(security, fromDate, SPLIT_EVENT, (in, idSecurity, securitysplits, currency) -> {
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
        securitysplits.add(new Securitysplit(idSecurity, splitDate, fraction.getDenominator(), fraction.getNumerator(),
            CreateType.CONNECTOR_CREATED));
      }
    });
  }

  private <S> List<S> getDividendSplitHistory(Security security, LocalDate fromDate, String event, IReadData<S> reader)
      throws Exception {
    List<S> records = new ArrayList<>();
    CookieStore cookieStore = new BasicCookieStore();
    HttpClient client = HttpClientBuilder.create()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(cookieStore);

    String symbol = URLEncoder.encode(
        event.equals(SPLIT_EVENT) ? security.getUrlSplitExtend() : security.getUrlDividendExtend(),
        StandardCharsets.UTF_8);

    String crumb = null;
    if (USE_CRUMB) {
      crumb = getCrumb(symbol, client, context);
    }

    if (!USE_CRUMB || USE_CRUMB && crumb != null && !crumb.isEmpty()) {
      String url = this.getSplitHistoricalDownloadLink(symbol, fromDate, event, crumb);
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
    }
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
        && !historyquote.getDate().equals(historyquotes.get(historyquotes.size() - 1).getDate());
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

}
