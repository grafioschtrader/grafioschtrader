package grafioschtrader.connector.instrument.onvista;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/*-
 * Supports history quotes for securities. Unfortunately, quotes are only
 * exported with an accuracy of two decimal places. Currency is disabled because
 * for currency it is not useful.
 * 
 * It produces Server returned HTTP response code: 429 (Too many requests)
 * 
 * @author Hugo Graf
 *
 */
@Component
public class OnVistaFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final int MAX_YEAR_DOWNLOAD = 5;
  private static final int TIMEOUT = 5000;
  private static final String DATE_FORMAT = "dd.MM.yyyy";
  private static MonthToString[] monthToStrings;

  static {
    supportedFeed = new HashMap<>();
    // supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] {
    // FeedIdentifier.SECURITY_URL,
    // FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    monthToStrings = new MonthToString[] { new MonthToString(1, "M1"), new MonthToString(3, "M3"),
        new MonthToString(6, "M6"), new MonthToString(12, "Y1"), new MonthToString(36, "Y3"),
        new MonthToString(60, "Y5") };
  }

  public OnVistaFeedConnector() {
    super(supportedFeed, "onvista", "Onvista", null);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return this.getHistoricalData(currencyPair.getUrlHistoryExtend(), from, to);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    final Calendar fromCal = Calendar.getInstance();
    fromCal.add(Calendar.YEAR, -MAX_YEAR_DOWNLOAD);
    return this.getSecurityHistoricalDownloadLink(security.getUrlHistoryExtend(), fromCal.getTime(),
        monthToStrings[monthToStrings.length - 1].traslate);
  }

  private String getSecurityHistoricalDownloadLink(final String productUrlPart, final Date startRange,
      String timeSpan) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    return "https://www.onvista.de/onvista/boxes/historicalquote/export.csv?notationId=" + productUrlPart
        + "&dateStart=" + dateFormat.format(startRange) + "&interval=" + timeSpan;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {

    return this.getHistoricalData(security.getUrlHistoryExtend(), from, to);
  }

  private List<Historyquote> getHistoricalData(String productUrlPart, final Date from, final Date to) throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();

    LocalDate lFrom = DateHelper.getLocalDate(from);
    LocalDate lTo = DateHelper.getLocalDate(to);
    LocalDate calcLFrom;
    int against429 = 0;

    do {
      long durationMonths = ChronoUnit.MONTHS.between(lFrom, lTo);
      Optional<MonthToString> optionalMts = Arrays.stream(monthToStrings)
          .filter(months -> durationMonths < months.month).findFirst();
      MonthToString mts = optionalMts.isPresent() ? optionalMts.get() : monthToStrings[monthToStrings.length - 1];

      if (against429 > 0) {
        Thread.sleep(1000);
      }
      readHistoricalData(productUrlPart, lFrom, mts.traslate, from, to, historyquotes, dateFormat);
      calcLFrom = lFrom.plusMonths(mts.month);
      if (!historyquotes.isEmpty()) {
        lFrom = DateHelper.getLocalDate(historyquotes.get(historyquotes.size() - 1).getDate()).plusDays(1);
      } else {
        lFrom = calcLFrom;
      }
      against429++;
    } while (!calcLFrom.isAfter(lTo));

    return historyquotes;
  }

  private void readHistoricalData(final String productUrlPart, final LocalDate startRange, final String timeSpan,
      final Date from, final Date to, List<Historyquote> historyquotes, SimpleDateFormat dateFormat) throws Exception {

    String url = getSecurityHistoricalDownloadLink(productUrlPart, DateHelper.getDateFromLocalDate(startRange),
        timeSpan);
    URL request = new URL(url);
    URLConnection connection = request.openConnection();
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        inputLine = inputLine.trim();
        if (inputLine.length() == 0 || !Character.isDigit(inputLine.charAt(0))) {
          // First line throw away
          continue;
        }
        final Historyquote historyquote = FeedConnectorHelper.parseResponseLineGE(inputLine, from, to, dateFormat);
        if (historyquote != null) {
          historyquotes.add(historyquote);
        } else {
          break;
        }
      }
    }
  }

}

class MonthToString {
  public long month;
  public String traslate;

  public MonthToString(long month, String traslate) {
    this.month = month;
    this.traslate = traslate;
  }

}
