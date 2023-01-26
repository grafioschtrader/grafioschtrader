package grafioschtrader.connector.instrument.exchangeratehost;

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 * This connector also provides a price for the current day, even in the first
 * hours of trading. Of course, this price is not the closing price.
 *
 */
@Component
public class ExchangerateHostFeedConnector extends BaseFeedConnector {

  private static final String DOMAIN_NAME = "https://api.exchangerate.host/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final int READ_TIME_SERIE_LIMIT = 365;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  public ExchangerateHostFeedConnector() {
    super(supportedFeed, "exchangeratehosts", "exchange.host", null);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date today = new Date();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return getCurrencypairHistoricalDownloadLink(currencypair, DateHelper.setTimeToZeroAndAddDay(today, -10), today,
        dateFormat);
  }

  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, Date fromDate, Date toDate,
      SimpleDateFormat dateFormat) {

    if (DateHelper.isSameDay(new Date(), toDate)
        && LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).getHour() < 23) {
      toDate = DateHelper.setTimeToZeroAndAddDay(toDate, -1);
    }
    return DOMAIN_NAME + "timeseries?start_date=" + dateFormat.format(fromDate) + "&end_date="
        + dateFormat.format(toDate) + "&base=" + currencypair.getFromCurrency() + "&symbols="
        + currencypair.getToCurrency();
  }

  @Override
  public synchronized List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date fromDate, Date toDate)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();

    Date startDate = fromDate;
    Date endDate = null;
    do {

      long days = DateHelper.getDateDiff(startDate, toDate, TimeUnit.DAYS);
      endDate = days > READ_TIME_SERIE_LIMIT ? DateHelper.setTimeToZeroAndAddDay(startDate, READ_TIME_SERIE_LIMIT)
          : toDate;
      log.debug("From Date: {}, To Date: {}", startDate, endDate);
      URL url = new URL(getCurrencypairHistoricalDownloadLink(currencyPair, startDate, endDate, dateFormat));
      HeaderExchangeHistorical heh = objectMapper.readValue(url, HeaderExchangeHistorical.class);
      historyquotes.addAll(addDays(heh.rates, dateFormat, currencyPair.getToCurrency()));
      startDate = DateHelper.setTimeToZeroAndAddDay(endDate, 1);
    } while (startDate.before(toDate));
    return historyquotes;
  }

  private List<Historyquote> addDays(Map<String, Map<String, Double>> rates, SimpleDateFormat dateFormat,
      String toCurrency) throws ParseException {
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (String dateString : rates.keySet()) {
      Double rate = rates.get(dateString).get(toCurrency);
      if (rate != null) {
        var historyquote = new Historyquote();
        historyquotes.add(historyquote);
        historyquote.setDate(dateFormat.parse(dateString));
        historyquote.setClose(rate);
      } else {
        // When a rate is not available for a certain year it returns a lot of
        // currencies but not the
        // expected one
        break;
      }
    }
    return historyquotes;
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class HeaderExchangeHistorical {
    public boolean success;
    public String base;
    public Map<String, Map<String, Double>> rates;
  }

}
