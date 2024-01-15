package grafioschtrader.connector.instrument.cryptocompare;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 * It has a max row limit
 *
 * A regex check of the URL extension is not necessary. The connector for
 * checking the instrument apparently always returns an HTTP OK. However, the
 * body of the return contains a text that indicates an unsupported currency
 * pair. This is evaluated here.
 */
@Component
public class CryptoCompareFeedConnector extends BaseFeedApiKeyConnector {

  private static final String DOMAIN_NAME = "https://min-api.cryptocompare.com/data/";
  private static final String TOKEN_PARAM_NAME = "api_key";
  private static final int MAX_ROWS = 2000;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  public CryptoCompareFeedConnector() {
    super(supportedFeed, "cryptocompare", "CryptoCompare", null, EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return DOMAIN_NAME + "price?fsym=" + currencypair.getFromCurrency() + "&tsyms=" + currencypair.getToCurrency() + "&"
        + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    TypeReference<Map<String, Double>> typeRef = new TypeReference<>() {
    };
    final URL url = new URI(getCurrencypairIntradayDownloadLink(currencypair)).toURL();

    final Map<String, Double> map = objectMapper.readValue(url, typeRef);
    currencypair.setSLast(map.get(currencypair.getToCurrency()));
    currencypair.setSTimestamp(new Date());
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 1;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return getCurrencypairHistoricalDownloadLink(currencypair, 20, new Date());
  }

  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, int limit, Date toDate) {
    return DOMAIN_NAME + "v2/histoday?fsym=" + currencypair.getFromCurrency() + "&tsym=" + currencypair.getToCurrency()
        + "&limit=" + limit + "&toTs=" + (toDate.getTime() / 1000) + "&" + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date fromDate, Date toDate)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    Date fromDateCalc = fromDate;
    Date toDateCalc = null;
    do {
      int limit = (int) DateHelper.getDateDiff(fromDateCalc, toDate, TimeUnit.DAYS);
      int rows = Math.min(limit, MAX_ROWS);
      toDateCalc = DateHelper.setTimeToZeroAndAddDay(fromDateCalc, rows);
      fromDateCalc = DateHelper.setTimeToZeroAndAddDay(toDateCalc, 1);

      final URL url = new URI(getCurrencypairHistoricalDownloadLink(currencyPair, rows, toDateCalc)).toURL();
      final CryptoCompareInput ci = objectMapper.readValue(url, CryptoCompareInput.class);
      List<Historyquote> readHistroyquotes = readCurrencyHistory(ci);
      if (!readHistroyquotes.isEmpty()) {
        log.debug("First append: {}", readHistroyquotes.get(0));
        log.debug("Last append: {}", readHistroyquotes.get(readHistroyquotes.size() - 1));
      }
      historyquotes.addAll(readHistroyquotes);
    } while (fromDateCalc.before(toDate));

    return historyquotes;
  }

  public List<Historyquote> readCurrencyHistory(final CryptoCompareInput ci) {
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (int i = 0; i < ci.Data.Data.size(); i++) {
      DataSingle ds = ci.Data.Data.get(i);
      if (ds.close > 0) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        histroyquote.setDate(new Date(ds.time * 1000));
        histroyquote.setClose(ds.close);
        histroyquote.setOpen(ds.open);
        histroyquote.setLow(ds.low);
        histroyquote.setHigh(ds.high);
        histroyquote.setVolume((long) ds.volumefrom);
      }
    }
    return historyquotes;
  }

  @Override
  protected boolean isConnectionOk(HttpURLConnection huc) {
    try {
      return getBodyAsString(huc).indexOf("\"Response\":\"Error\"") == -1;
    } catch (IOException e) {
      log.error("Could not open connection", e);
    }
    return true;
  }

  @Override
  protected String hideApiKeyForError(String url) {
    return url.replaceFirst("(.*" + TOKEN_PARAM_NAME + "=)([^&]*)(.*)", "$1" + ERROR_API_KEY_REPLACEMENT + "$3");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class CryptoCompareInput {
    public String Response;
    public String Message;
    public boolean HasWarning;
    public int Type;
    public TopData Data;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class TopData {
    public boolean Aggregated;
    public Date TimeFrom;
    public Date TimeTo;
    public List<DataSingle> Data;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class DataSingle {
    public long time;
    public double high;
    public double low;
    public double open;
    public double close;
    public double volumefrom;
  }

}
