package grafioschtrader.connector.instrument.stockdata;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

@Component
public class StockDataFeedConnector extends BaseFeedApiKeyConnector {

  private static final String DOMAIN_NAME_WITH_VERSION = "https://api.stockdata.org/v1/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
  }

  public StockDataFeedConnector() {
    super(supportedFeed, "stockdata", "StockData", null);
  }

  private String getApiKeyString(boolean firstArgument) {
    return (firstArgument? "": "&") +  "api_token=" + getApiKey();
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to) {
    return getSecurityCurrencyHistoricalDownloadLink(security.getUrlHistoryExtend().toUpperCase(), from, to);
  }

  private String getSecurityCurrencyHistoricalDownloadLink(String ticker, Date from, Date to) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return DOMAIN_NAME_WITH_VERSION + "data/eod?symbols=" + ticker + "&date_from=" + dateFormat.format(from)
        + "&date_to=" + dateFormat.format(to) + getApiKeyString(false);
  }

  private String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, Date from, Date to) {
    return getSecurityCurrencyHistoricalDownloadLink(getCurrencyPairSymbol(currencypair), from, to);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(from, to, new URL(getSecurityHistoricalDownloadLink(security, from, to)));
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getCurrencypairHistoricalDownloadLink(currencypair, DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws IOException, ParseException, URISyntaxException {
    return getEodSecurityCurrencypairHistory(from, to,
        new URL(getCurrencypairHistoricalDownloadLink(currencyPair, from, to)));
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(final Date from, final Date to, URL url)
      throws StreamReadException, DatabindException, IOException {
    final List<Historyquote> historyquotes = new ArrayList<>();
    final EODMetaData eodData = objectMapper.readValue(url, EODMetaData.class);
    for (EODData data : eodData.data) {
      if (!(data.date.before(from) || data.date.after(to))) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        histroyquote.setDate(data.date);
        histroyquote.setClose(data.close);
        histroyquote.setOpen(data.open);
        histroyquote.setLow(data.low);
        histroyquote.setHigh(data.high);
        histroyquote.setVolume(data.volume);
      }
    }
    return historyquotes;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_VERSION + "data/quote?symbols=" + security.getUrlIntraExtend().toUpperCase()
        + getApiKeyString(false);
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final QuoteSecurity quote = objectMapper.readValue(new URL(getSecurityIntradayDownloadLink(security)),
        QuoteSecurity.class);
    for (QuoteDataSecurity data : quote.data) {
      data.setValues(security);
    }
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 0;
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return DOMAIN_NAME_WITH_VERSION + "data/currency/latest?symbols=" + getCurrencyPairSymbol(currencypair)
        + getApiKeyString(false);
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    final QuoteCurrencypair quote = objectMapper.readValue(new URL(getCurrencypairIntradayDownloadLink(currencypair)),
        QuoteCurrencypair.class);
    for (QuoteDataCurrencypair data : quote.data[0]) {
      data.setValues(currencypair);
    }
  }

  public StockexchangeAllStockdata getAllStockexchanges()
      throws StreamReadException, DatabindException, MalformedURLException, IOException {
    return objectMapper.readValue(new URL(DOMAIN_NAME_WITH_VERSION + "entity/exchange/list?" + getApiKeyString(true)),
        StockexchangeAllStockdata.class);
  }

  private String getCurrencyPairSymbol(final Currencypair currencypair) {
    return currencypair.getFromCurrency() + currencypair.getToCurrency();
  }

  private static class EODMetaData {
    // public String ticker;
    // public String name;
    // public String timezone_name;
    public EODData[] data;
  }

  private static class EODData {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Date date;
    public Double open;
    public Double high;
    public Double low;
    public Double close;
    public Long volume;

  }

  private static class QuoteSecurity {
    // public QuoteMeta meta;
    public QuoteDataSecurity[] data;
  }

  private static class QuoteCurrencypair {
    // public QuoteMeta meta;
    public QuoteDataCurrencypair[][] data;
  }

  private static class QuoteData {
    public double price;
    public double day_high;
    public double day_low;
    public double day_open;
    public double previous_close_price;

    public void setValues(Securitycurrency<?> securitycurrency) {
      securitycurrency.setSLast(price);
      securitycurrency.setSOpen(day_open);
      securitycurrency.setSLow(day_low);
      securitycurrency.setSHigh(day_high);
      securitycurrency.setSPrevClose(previous_close_price);
      securitycurrency.setSTimestamp(new Date());
    }

  }

  private static class QuoteDataSecurity extends QuoteData {
    // public String ticker;
    public double day_change;

    public void setValues(Security securitycurrency) {
      super.setValues(securitycurrency);
      securitycurrency.setSChangePercentage(day_change);
    }
  }

  private static class QuoteDataCurrencypair extends QuoteData {
    // public String symbol;
    public double change_percent;

    public void setValues(Currencypair currencypair) {
      super.setValues(currencypair);
      currencypair.setSChangePercentage(change_percent);
    }
  }

  public static class StockexchangeAllStockdata {
    public List<StockexchangeStockdata> data;
  }

  public static class StockexchangeStockdata {
    public String mic_code;
    public String exchange;
    public String stock_exchange_long;
    public String country;
    public String append;
    public String timezone;
    public String timezone_dst;
    public String timezone_name;
    public byte is_dst;
    @Override
    public String toString() {
      return "StockexchangeStockdata [mic_code=" + mic_code + ", exchange=" + exchange + ", stock_exchange_long="
          + stock_exchange_long + ", country=" + country + ", append=" + append + ", timezone=" + timezone
          + ", timezone_dst=" + timezone_dst + ", timezone_name=" + timezone_name + ", is_dst=" + is_dst + "]";
    }
    
    
    
  }
}
