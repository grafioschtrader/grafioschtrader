package grafioschtrader.connector.instrument.twelvedata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

@Component
public class TwelvedataFeedConnector extends BaseFeedApiKeyConnector {
  private static final int MAX_DATA_POINTS = 5000;
  private static final String DOMAIN_NAME = "https://api.twelvedata.com/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Bucket bucket;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
  }

  public TwelvedataFeedConnector() {
    super(supportedFeed, "twelvedata", "Twelve Data", null);
    Bandwidth limit = Bandwidth.classic(8, Refill.intervally(8, Duration.ofMinutes(1)));
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  private String getApiKeyString() {
    return "&apikey=" + getApiKey();
  }

  private void waitForTokenOrGo() {
    do {
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return;
      } else {
        long waitForRefill = TimeUnit.MILLISECONDS.convert(probe.getNanosToWaitForRefill(), TimeUnit.NANOSECONDS);
        try {
          Thread.sleep(waitForRefill);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } while (true);
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
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return DOMAIN_NAME + "time_series?symbol=" + ticker.toUpperCase() + "&format=CSV&interval=1day&start_date="
        + dateFormat.format(from) + "&end_date=" + dateFormat.format(to) + getApiKeyString();
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
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    Date toDate = null;
    if (DateHelper.getDateDiff(from, to, TimeUnit.DAYS) / 7 * 5 > MAX_DATA_POINTS) {
      toDate = DateHelper.setTimeToZeroAndAddDay(from, MAX_DATA_POINTS / 5 * 7);
      historyquotes.addAll(getEodSecurityCurrencypairHistoryMax5000(
          new URL(getSecurityCurrencyHistoricalDownloadLink(ticker, from, toDate)), dateFormat, divider, hasVolume));
    }
    Date fromDate = toDate == null ? from : DateHelper.setTimeToZeroAndAddDay(toDate, 1);
    historyquotes.addAll(getEodSecurityCurrencypairHistoryMax5000(
        new URL(getSecurityCurrencyHistoricalDownloadLink(ticker, fromDate, to)), dateFormat, divider, hasVolume));

    return historyquotes;
  }

  private List<Historyquote> getEodSecurityCurrencypairHistoryMax5000(URL url, SimpleDateFormat dateFormat,
      double divider, boolean hasVolume) throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    waitForTokenOrGo();
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
          if(historyquotes.isEmpty() || !historyquotes.isEmpty() && !historyquotes.get(historyquotes.size() - 1).getDate().equals(hq.getDate())) {
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
    waitForTokenOrGo();
    var quote = objectMapper.readValue(new URL(getSecurityIntradayDownloadLink(security)), Quote.class);
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
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    waitForTokenOrGo();
    var quote = objectMapper.readValue(new URL(getCurrencypairIntradayDownloadLink(currencypair)), Quote.class);
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
