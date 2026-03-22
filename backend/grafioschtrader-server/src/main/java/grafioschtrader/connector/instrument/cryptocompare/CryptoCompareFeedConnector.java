package grafioschtrader.connector.instrument.cryptocompare;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafiosch.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * It has a max row limit
 *
 * A regex check of the URL extension is not necessary. The connector for checking the instrument apparently always
 * returns an HTTP OK. However, the body of the return contains a text that indicates an unsupported currency pair. This
 * is evaluated here.
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
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CRYPTOCURRENCY);
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

    final Map<String, Double> map = objectMapper.readValue(url.openStream(), typeRef);
    currencypair.setSLast(map.get(currencypair.getToCurrency()));
    currencypair.setSTimestamp(LocalDateTime.now());
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 1;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return getCurrencypairHistoricalDownloadLink(currencypair, 20, LocalDate.now());
  }

  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, int limit, LocalDate toDate) {
    return DOMAIN_NAME + "v2/histoday?fsym=" + currencypair.getFromCurrency() + "&tsym=" + currencypair.getToCurrency()
        + "&limit=" + limit + "&toTs=" + DateHelper.LocalDateToEpocheSeconds(toDate) + "&" + TOKEN_PARAM_NAME + "="
        + getApiKey();
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, LocalDate fromDate, LocalDate toDate)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate fromDateCalc = fromDate;
    LocalDate toDateCalc;
    do {
      int limit = (int) ChronoUnit.DAYS.between(fromDateCalc, toDate);
      int rows = Math.min(limit, MAX_ROWS);
      toDateCalc = fromDateCalc.plusDays(rows);
      fromDateCalc = toDateCalc.plusDays(1);

      final URL url = new URI(getCurrencypairHistoricalDownloadLink(currencyPair, rows, toDateCalc)).toURL();
      final CryptoCompareInput ci = objectMapper.readValue(url.openStream(), CryptoCompareInput.class);
      List<Historyquote> readHistroyquotes = readCurrencyHistory(ci);
      if (!readHistroyquotes.isEmpty()) {
        log.debug("First append: {}", readHistroyquotes.get(0));
        log.debug("Last append: {}", readHistroyquotes.get(readHistroyquotes.size() - 1));
      }
      historyquotes.addAll(readHistroyquotes);
    } while (fromDateCalc.isBefore(toDate));

    return historyquotes;
  }

  public List<Historyquote> readCurrencyHistory(final CryptoCompareInput ci) {
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (int i = 0; i < ci.Data.Data.size(); i++) {
      DataSingle ds = ci.Data.Data.get(i);
      if (ds.close > 0) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        histroyquote.setDate(
            Instant.ofEpochSecond(ds.time).atZone(ZoneId.systemDefault()).toLocalDate());
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
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, TOKEN_PARAM_NAME);
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
    public long TimeFrom;
    public long TimeTo;
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
