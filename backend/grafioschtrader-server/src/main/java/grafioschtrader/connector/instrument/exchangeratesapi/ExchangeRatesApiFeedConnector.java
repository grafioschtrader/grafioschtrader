package grafioschtrader.connector.instrument.exchangeratesapi;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

@Component
public class ExchangeRatesApiFeedConnector extends BaseFeedConnector {

  private static final String DOMAIN_NAME = "https://api.exchangeratesapi.io/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  
  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  
  public ExchangeRatesApiFeedConnector() {
    super(supportedFeed, "exchangeratesapi", "ExchangeRatesApi");
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
    return DOMAIN_NAME + "history?&base=" + currencypair.getFromCurrency()+ "&start_at=" + dateFormat.format(fromDate) + "&end_at="
        + dateFormat.format(toDate) + "&symbols=" + currencypair.getToCurrency();
  }
  
  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date fromDate, Date toDate)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    URL url = new URL(getCurrencypairHistoricalDownloadLink(currencyPair, fromDate, toDate, dateFormat));
    ExRaApiRates erar = objectMapper.readValue(url, ExRaApiRates.class);
    for(String dateStr: erar.rates.keySet()) {
      Historyquote historyquote = new Historyquote();
      historyquotes.add(historyquote);
      historyquote.setDate(dateFormat.parse(dateStr));
      historyquote.setClose(erar.rates.get(dateStr).get(currencyPair.getToCurrency()));
    }

    return historyquotes;
  } 
  
  private static class ExRaApiRates {
    public Map<String, Map<String, Double>> rates;
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public String start_at;
    public String base;
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public String end_at;
  }
}
