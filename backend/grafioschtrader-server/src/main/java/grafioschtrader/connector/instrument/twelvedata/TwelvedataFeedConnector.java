package grafioschtrader.connector.instrument.twelvedata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.SubscriptionType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * Twelvedata's pricing model regulates both the number of accesses per minute
 * and the availability of the functionality. A maximum number of accesses per
 * minute or even per day is not implemented. The "Basic Free" price model also
 * only provides US data.
 *
 * A regex check of the URL ending is not implemented. However, a check via the
 * connector is available.
 */
@Component
public class TwelvedataFeedConnector extends BaseFeedApiKeyConnector {
  private static final int MAX_DATA_POINTS = 5000;
  private static final String DOMAIN_NAME = "https://api.twelvedata.com/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String TOKEN_PARAM_NAME = "apikey";
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Bucket bucket;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
  }

  public TwelvedataFeedConnector() {
    super(supportedFeed, "twelvedata", "Twelve Data", null, EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
    Bandwidth limit = Bandwidth.classic(8, Refill.intervally(8, Duration.ofMinutes(1)));
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  private String getApiKeyString() {
    return "&" + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  

  @Override
  protected boolean isConnectionOk(HttpURLConnection huc) {
    try {
      return getBodyAsString(huc).indexOf("\"code\":404") == -1;
    } catch (IOException e) {
      log.error("Could not open connection", e);
    }
    return true;
  }

  @Override
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, TOKEN_PARAM_NAME);
  }

  @Override
  public <S extends Securitycurrency<S>> void hasAPISubscriptionSupport(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport) {
    if (getSubscriptionType() == SubscriptionType.TWELVEDATA_FREE && securitycurrency instanceof Security security) {
      if (!security.getStockexchange().getCountryCode().equals(Locale.US.getCountry())) {
        throw new GeneralNotTranslatedWithArgumentsException("gt.connector.subscription.failure",
            new Object[] { getReadableName(), feedSupport.name() });
      }
    }
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrencyHistoricalDownloadLink(security.getUrlHistoryExtend(),
        DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrencyHistoricalDownloadLink(getCurrencypairSymbol(currencypair),
        DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  private String getSecurityCurrencyHistoricalDownloadLink(String ticker, Date from, Date to) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    return DOMAIN_NAME
        +  "time_series?symbol=" + ticker.toUpperCase() + "&format=CSV&interval=1day&start_date="
                + dateFormat.format(from) + "&end_date=" + dateFormat.format(to) + "+23:59:59" + getApiKeyString();
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(security.getUrlHistoryExtend(), from, to,
        FeedConnectorHelper.getGBXLondonDivider(security), true);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(getCurrencypairSymbol(currencyPair), from, to, 1.0, false);
  }

  private String getCurrencypairSymbol(Currencypair currencyPair) {
    return currencyPair.getFromCurrency() + "/" + currencyPair.getToCurrency();
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(String ticker, final Date from, Date to, double divider,
      boolean hasVolume) throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    Date toDate = null;
    if (DateHelper.getDateDiff(from, to, TimeUnit.DAYS) / 7 * 5 > MAX_DATA_POINTS - 100) {
      toDate = DateHelper.setTimeToZeroAndAddDay(from, (MAX_DATA_POINTS - 100) / 5 * 7);
      historyquotes.addAll(getEodSecurityCurrencypairHistoryMax5000(
          new URI(getSecurityCurrencyHistoricalDownloadLink(ticker, from, toDate)).toURL(), dateFormat, divider,
          hasVolume));
    }
    Date fromDate = toDate == null ? from : DateHelper.setTimeToZeroAndAddDay(toDate, 1);
    historyquotes.addAll(getEodSecurityCurrencypairHistoryMax5000(
        new URI(getSecurityCurrencyHistoricalDownloadLink(ticker, fromDate, to)).toURL(), dateFormat, divider,
        hasVolume));

    return historyquotes;
  }

  private List<Historyquote> getEodSecurityCurrencypairHistoryMax5000(URL url, SimpleDateFormat dateFormat,
      double divider, boolean hasVolume) throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    waitForTokenOrGo(bucket);
    URLConnection connection = url.openConnection();
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        if (inputLine.trim().length() == 0 || !Character.isDigit(inputLine.charAt(0))) {
          // First line throw away
          continue;
        }
        final String[] items = inputLine.split(";");
        if (items.length == 5 + (hasVolume ? 1 : 0)) {
          Historyquote hq = parseResponseLineItems(items, dateFormat, divider, hasVolume);
          // Sometimes we get two rows for one date
          if (historyquotes.isEmpty()
              || !historyquotes.isEmpty() && !historyquotes.getLast().getDate().equals(hq.getDate())) {
            historyquotes.add(hq);
          }
        }
      }
    }
    return historyquotes;
  }

  /**
   * datetime;open;high;low;close;volume
   * 2019-01-30;40.81250;41.53750;40.05750;41.31250;244439200
   * 2019-01-29;39.06250;39.53250;38.52750;38.67000;166348800
   *
   * @param inputLine
   * @param dateFormat
   * @return
   * @throws ParseException
   */
  private Historyquote parseResponseLineItems(final String[] items, SimpleDateFormat dateFormat, double divider,
      boolean hasVolume) throws ParseException {
    Historyquote historyquote = new Historyquote();
    historyquote.setDate(dateFormat.parse(items[0]));
    historyquote.setOpen(Double.parseDouble(items[1]) / divider);
    historyquote.setHigh(Double.parseDouble(items[2]) / divider);
    historyquote.setLow(Double.parseDouble(items[3]) / divider);
    historyquote.setClose(Double.parseDouble(items[4]) / divider);
    if (hasVolume) {
      historyquote.setVolume(Long.parseLong(items[5]));
    }
    return historyquote;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return getSecurityCurrencyIntradayDownloadLink(security.getUrlIntraExtend());
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    waitForTokenOrGo(bucket);
    var quote = objectMapper.readValue(new URI(getSecurityIntradayDownloadLink(security)).toURL(), Quote.class);
    quote.setValues(security, FeedConnectorHelper.getGBXLondonDivider(security), getIntradayDelayedSeconds());
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return getSecurityCurrencyIntradayDownloadLink(getCurrencypairSymbol(currencypair));
  }

  private String getSecurityCurrencyIntradayDownloadLink(final String ticker) {
    return DOMAIN_NAME + "quote/?symbol=" + ticker.toUpperCase() + getApiKeyString();
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    waitForTokenOrGo(bucket);
    var quote = objectMapper.readValue(new URI(getCurrencypairIntradayDownloadLink(currencypair)).toURL(), Quote.class);
    quote.setValues(currencypair, 1.0, getIntradayDelayedSeconds());
  }

  private static class Quote {
    // public String symbol;
    public double open;
    public double high;
    public double low;
    public double close;
    public double previous_close;
    // public double change;
    public double percent_change;

    public void setValues(Securitycurrency<?> securitycurrency, double divider, int delaySeconds) {
      securitycurrency.setSLast(close / divider);
      securitycurrency.setSOpen(open / divider);
      securitycurrency.setSLow(low / divider);
      securitycurrency.setSHigh(high / divider);
      securitycurrency.setSChangePercentage(percent_change);
      securitycurrency.setSPrevClose(previous_close / divider);
      securitycurrency.setSTimestamp(new Date(new Date().getTime() - delaySeconds * 1000));
    }
  }

}
