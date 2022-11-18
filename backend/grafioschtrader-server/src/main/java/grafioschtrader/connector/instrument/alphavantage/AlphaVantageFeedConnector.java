package grafioschtrader.connector.instrument.alphavantage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/*-
 * Only 5 API request per minutes and 500 per day.
 *
 * Alphavantage does not provide intraday data for the current trading day. 
 * Neither with the function "GLOBAL_QUOTE" nor with "TIME_SERIES_INTRADAY". 
 * Instead, the data from the previous trading day is delivered.
 *
 * Dividend: Not Supported
 * Splits: Not Supported
 *
 */
@Component
@ConfigurationProperties(prefix = "gt.connector.alphavantage")
public class AlphaVantageFeedConnector extends BaseFeedApiKeyConnector {

  private static final String ALPHAVANTAGE_BASE = "https://www.alphavantage.co/query?function=";
  private static final String URL_NORMAL_REGEX = "^\\^?[A-Za-z\\-0-9]+(\\.[A-Za-z]+)?$";
  private static final int TIMEOUT = 15000;
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  /**
   * returns only the latest 100 data points
   */
  private static final String COMPACT = "compact";

  /**
   * returns the full-length time series of up to 20 years of historical data
   */
  private static final String FULL = "full";

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public AlphaVantageFeedConnector() {
    super(supportedFeed, "alphavantage", "Alpha Vantage", URL_NORMAL_REGEX);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return ALPHAVANTAGE_BASE + "GLOBAL_QUOTE&datatype=csv&symbol=" + security.getUrlIntraExtend() + "&apikey="
        + getApiKey();
  }

  public String getSecurityHistoricalDownloadLink(final Security security, String outputsize) {
    return ALPHAVANTAGE_BASE + "TIME_SERIES_DAILY_ADJUSTED&datatype=csv&symbol=" + security.getUrlHistoryExtend()
        + "&outputsize=" + outputsize + "&apikey=" + getApiKey();
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getSecurityHistoricalDownloadLink(security, COMPACT);
  }

  /*-
   * An update of the last price is not carried out if the market is closed.
   * symbol,open,high,low,price,volume,latestDay,previousClose,change,changePercent
   * CAT,237.1000,239.4000,232.7450,234.5900,3371100,2022-11-15,236.5000,-1.9100,-0.8076%
   * 
   */
  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {

    Date date = new Date();
    final List<Historyquote> historyquotes = this.getEodSecurityHistory(security, date, date, true);
    if (historyquotes.size() == 1) {
      Historyquote historyquote = historyquotes.get(0);
      security.setSOpen(historyquote.getOpen());
      security.setSHigh(historyquote.getHigh());
      security.setSLow(historyquote.getLow());
      security.setSLast(historyquote.getClose());
      security.setSTimestamp(new Date());
    }
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 0;
  }

  /*-
   *  timestamp,open,high,low,close,adjusted_close,volume,dividend_amount,split_coefficient
   *  2020-11-23,117.18,117.6202,113.75,113.85,113.85,127959318,0.0000,1.0
   *  2020-11-20,118.64,118.77,117.29,117.34,117.34,73604287,0.0000,1.0
   */
  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodSecurityHistory(security, from, to, false);
  }

  public synchronized List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to,
      boolean intraday) throws Exception {

    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    String outputsize = DateHelper.getDateDiff(from, new Date(), TimeUnit.DAYS) / 7 * 5 >= 100.0 ? FULL : COMPACT;

    URL request = new URL((intraday) ? getSecurityIntradayDownloadLink(security)
        : getSecurityHistoricalDownloadLink(security, outputsize));
    URLConnection connection = request.openConnection();
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        if (inputLine.trim().length() == 0 || !Character.isDigit(inputLine.charAt(0))) {
          // First line throw away
          continue;
        }
        if (intraday) {
          parseResponseLineAndSetIndraday(security, inputLine);
        } else {
          final Historyquote historyquote = parseResponseLine(inputLine, from, to, dateFormat);
          if (historyquote != null) {
            historyquotes.add(historyquote);
          }
        }
      }
    }

    return historyquotes;
  }

  private Historyquote parseResponseLine(final String inputLine, final Date from, final Date to,
      final SimpleDateFormat dateFormat) throws ParseException {
    Historyquote historyquote = null;
    final String[] item = inputLine.split(",");
    final Calendar day = Calendar.getInstance();
    day.setTime(dateFormat.parse(item[0]));
    DateHelper.setTimeToZero(day);
    if (day.getTime().getTime() >= from.getTime() && day.getTime().getTime() <= to.getTime()) {
      historyquote = new Historyquote();
      historyquote.setDate(day.getTime());
      historyquote.setOpen(Double.parseDouble(item[1]));
      historyquote.setHigh(Double.parseDouble(item[2]));
      historyquote.setLow(Double.parseDouble(item[3]));
      historyquote.setClose(Double.parseDouble(item[5]));
      historyquote.setVolume(Long.parseLong(item[6]));
    }
    return historyquote;
  }

  private void parseResponseLineAndSetIndraday(final Security security, final String inputLine) throws ParseException {
    final String[] item = inputLine.split(",");
    security.setSOpen(Double.parseDouble(item[1]));
    security.setSHigh(Double.parseDouble(item[2]));
    security.setSLow(Double.parseDouble(item[3]));
    security.setSLast(Double.parseDouble(item[4]));
    security.setSVolume(Long.parseLong(item[5]));
    security.setSPrevClose(Double.parseDouble(item[7]));
    security.setSChangePercentage(Double.parseDouble(item[9].replace("%", "")));
  }

}
