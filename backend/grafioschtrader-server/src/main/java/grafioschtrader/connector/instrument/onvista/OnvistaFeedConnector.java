package grafioschtrader.connector.instrument.onvista;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

/*-
 * Supports history quotes for securities. Unfortunately, quotes are only
 * exported with an accuracy of two decimal places. Currency is disabled because
 * for currency it is not useful.
 *
 * It produces Server returned HTTP response code: 429 (Too many requests)
 *
 * Regex pattern recognition for checking the URL extension is not implemented.
 * However, the URL extension is checked with a connection to the connector.
 */
@Component
public class OnvistaFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final int MAX_YEAR_DOWNLOAD = 5;
  private static MonthToString[] monthToStrings;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    // supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] {
    // FeedIdentifier.SECURITY_URL,
    // FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    monthToStrings = new MonthToString[] { new MonthToString(1, "M1"), new MonthToString(3, "M3"),
        new MonthToString(6, "M6"), new MonthToString(12, "Y1"), new MonthToString(36, "Y3"),
        new MonthToString(60, "Y5") };
  }

  public OnvistaFeedConnector() {
    super(supportedFeed, "onvista", "onvista", null, EnumSet.of(UrlCheck.HISTORY));
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    return this.getHistoricalData(currencyPair.getUrlHistoryExtend(), from, to);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return getDownloadLink(currencypair);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getDownloadLink(security);
  }

  private <S extends Securitycurrency<S>> String getDownloadLink(S securitycurrency) {
    final Calendar fromCal = Calendar.getInstance();
    fromCal.add(Calendar.YEAR, -MAX_YEAR_DOWNLOAD);
    return this.getSecurityHistoricalDownloadLink(securitycurrency.getUrlHistoryExtend(), fromCal.getTime(),
        monthToStrings[monthToStrings.length - 1].traslate);
  }

  private String getSecurityHistoricalDownloadLink(final String productUrlPart, final Date startRange,
      String timeSpan) {
    return "https://api.onvista.de/api/v1/instruments/" + productUrlPart + "&startDate="
        + new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT).format(startRange) + "&range=" + timeSpan;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return this.getHistoricalData(security.getUrlHistoryExtend(), from, to);
  }

  /**
   * Synchronized is introduced to avoid the response 429 (Too many requests)
   */
  private List<Historyquote> getHistoricalData(String productUrlPart, final Date from, final Date to) throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate lFrom = DateHelper.getLocalDate(from);
    LocalDate lTo = DateHelper.getLocalDate(to);
    LocalDate calcLFrom;
    do {
      long durationMonths = ChronoUnit.MONTHS.between(lFrom, lTo);
      Optional<MonthToString> optionalMts = Arrays.stream(monthToStrings)
          .filter(months -> durationMonths < months.month).findFirst();
      MonthToString mts = optionalMts.isPresent() ? optionalMts.get() : monthToStrings[monthToStrings.length - 1];

      readHistoricalData(productUrlPart, lFrom, mts.traslate, from, to, historyquotes);
      calcLFrom = lFrom.plusMonths(mts.month);
      if (!historyquotes.isEmpty()) {
        lFrom = DateHelper.getLocalDate(historyquotes.get(historyquotes.size() - 1).getDate()).plusDays(1);
      } else {
        lFrom = calcLFrom;
      }
    } while (!calcLFrom.isAfter(lTo));

    return historyquotes;
  }

  private void readHistoricalData(final String productUrlPart, final LocalDate startRange, final String timeSpan,
      final Date from, final Date to, List<Historyquote> historyquotes) throws Exception {

    String url = getSecurityHistoricalDownloadLink(productUrlPart, DateHelper.getDateFromLocalDate(startRange),
        timeSpan);
    final Quotes quotes = objectMapper.readValue(new URI(url).toURL(), Quotes.class);
    for (int i = 0; i < quotes.datetimeLast.length; i++) {
      Date date = DateHelper.setTimeToZeroAndAddDay(new Date(quotes.datetimeLast[i] * 1000), 0);
      if (!to.before(date)) {
        final Historyquote historyquote = new Historyquote();
        historyquotes.add(historyquote);
        historyquote.setDate(date);
        historyquote.setClose(quotes.last[i]);
        historyquote.setOpen(quotes.first[i]);
        historyquote.setHigh(quotes.high[i]);
        historyquote.setLow(quotes.low[i]);
        historyquote.setVolume(quotes.volume[i]);
      }
    }
  }

  private static class Quotes {
    public long[] datetimeLast;
    public double[] first;
    public double[] last;
    public double[] high;
    public double[] low;
    public long[] volume;
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
