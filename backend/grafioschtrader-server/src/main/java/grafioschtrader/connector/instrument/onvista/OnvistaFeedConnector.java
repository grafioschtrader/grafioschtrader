package grafioschtrader.connector.instrument.onvista;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws Exception {
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
    LocalDate fromDate = LocalDate.now().minusYears(MAX_YEAR_DOWNLOAD);
    return this.getSecurityHistoricalDownloadLink(securitycurrency.getUrlHistoryExtend(), fromDate,
        monthToStrings[monthToStrings.length - 1].traslate);
  }

  private String getSecurityHistoricalDownloadLink(final String productUrlPart, final LocalDate startRange,
      String timeSpan) {
    return "https://api.onvista.de/api/v1/instruments/" + productUrlPart + "&startDate="
        + DateTimeFormatter.ofPattern(BaseConstants.STANDARD_DATE_FORMAT).format(startRange) + "&range=" + timeSpan;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return this.getHistoricalData(security.getUrlHistoryExtend(), from, to);
  }

  /**
   * Synchronized is introduced to avoid the response 429 (Too many requests)
   */
  private List<Historyquote> getHistoricalData(String productUrlPart, final LocalDate from, final LocalDate to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate lFrom = from;
    LocalDate lTo = to;
    LocalDate calcLFrom;
    do {
      long durationMonths = ChronoUnit.MONTHS.between(lFrom, lTo);
      Optional<MonthToString> optionalMts = Arrays.stream(monthToStrings)
          .filter(months -> durationMonths < months.month).findFirst();
      MonthToString mts = optionalMts.isPresent() ? optionalMts.get() : monthToStrings[monthToStrings.length - 1];

      readHistoricalData(productUrlPart, lFrom, mts.traslate, to, historyquotes);
      calcLFrom = lFrom.plusMonths(mts.month);
      if (!historyquotes.isEmpty()) {
        lFrom = historyquotes.get(historyquotes.size() - 1).getDate().plusDays(1);
      } else {
        lFrom = calcLFrom;
      }
    } while (!calcLFrom.isAfter(lTo));

    return historyquotes;
  }

  private void readHistoricalData(final String productUrlPart, final LocalDate startRange, final String timeSpan,
      final LocalDate to, List<Historyquote> historyquotes) throws Exception {

    String url = getSecurityHistoricalDownloadLink(productUrlPart, startRange, timeSpan);
    final Quotes quotes = objectMapper.readValue(new URI(url).toURL().openStream(), Quotes.class);
    for (int i = 0; i < quotes.datetimeLast.length; i++) {
      LocalDate date = Instant.ofEpochSecond(quotes.datetimeLast[i]).atZone(ZoneId.systemDefault()).toLocalDate();
      if (!to.isBefore(date)) {
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
