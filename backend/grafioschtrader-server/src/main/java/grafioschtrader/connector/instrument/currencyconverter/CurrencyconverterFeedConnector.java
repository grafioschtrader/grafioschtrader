package grafioschtrader.connector.instrument.currencyconverter;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;

@Component
public class CurrencyconverterFeedConnector extends BaseFeedConnector {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private String apiKey;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  public CurrencyconverterFeedConnector() {
    super(supportedFeed, "currencyconverter", "Free Currency Converter", null);
  }

  @Value("${gt.connector.currencyconverter.apikey}")
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public boolean isActivated() {
    return !apiKey.isEmpty();
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return "https://free.currconv.com/api/v7/convert?q=" + getQueryString(currencypair) + "&apiKey=" + apiKey;
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException {

    final URL url = new URL(getCurrencypairIntradayDownloadLink(currencypair));

    final CurrencyconverterInput ci = objectMapper.readValue(url, CurrencyconverterInput.class);
    currencypair.setSLast(ci.results.get(getQueryString(currencypair)).val);
    currencypair.setSTimestamp(new Date(System.currentTimeMillis() - 60 * 60 * 1000));
  }

  private String getQueryString(final Currencypair currencypair) {
    return currencypair.getFromCurrency() + "_" + currencypair.getToCurrency();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 3600;
  }

  static class CurrencyconverterInput {
    public Query query;
    public Map<String, ResultValues> results;
  }

  static class Query {
    public int count;
  }

  static class ResultValues {
    public String id;
    public double val;
    public String to;
    public String fr;

  }

}
