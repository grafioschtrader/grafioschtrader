package grafioschtrader.connector.instrument.eodhistoricaldata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

@Component
public class EodHistoricalDataConnector extends BaseFeedApiKeyConnector {

  private static final String DOMAIN_NAME_API = "https://eodhistoricaldata.com/api/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String DIVDEND_EVENT = "div";
  private static final String SPLIT_EVENT = "splits";
  private static final String JSON_PARAM = "fmt=json";

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(new JavaTimeModule());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public EodHistoricalDataConnector() {
    super(supportedFeed, "eodhistoricaldata", "EOD Historical Data", null);
  }

  private String getApiKeyString() {
    return "&api_token=" + getApiKey();
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        dateFormat);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date toDate = new Date();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getCurrencypairHistoricalDownloadLink(currencypair, DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        dateFormat);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to,
      final SimpleDateFormat dateFormat) {
    return getSecurityCurrencyHistoricalDownloadLink(security.getUrlHistoryExtend().toUpperCase(), from, to,
        dateFormat);
  }

  private String getSecurityCurrencyHistoricalDownloadLink(String ticker, Date from, Date to,
      final SimpleDateFormat dateFormat) {
    return DOMAIN_NAME_API + "eod/" + ticker + "?from=" + dateFormat.format(from) + "&to=" + dateFormat.format(to)
        + getApiKeyString();
  }

  private String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, Date from, Date to,
      final SimpleDateFormat dateFormat) {
    return getSecurityCurrencyHistoricalDownloadLink(getCurrencyPairSymbol(currencypair), from, to, dateFormat);
  }

  private String getCurrencyPairSymbol(final Currencypair currencypair) {
    if (currencypair.getIsCryptocurrency()) {
      return currencypair.getFromCurrency() + "-" + currencypair.getToCurrency() + ".CC";
    } else {
      return currencypair.getFromCurrency() + currencypair.getToCurrency() + ".FOREX";
    }
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return getEodSecurityCurrencypairHistory(new URL(getSecurityHistoricalDownloadLink(security, from, to, dateFormat)),
        dateFormat, FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return getEodSecurityCurrencypairHistory(
        new URL(getCurrencypairHistoricalDownloadLink(currencyPair, from, to, dateFormat)), dateFormat, 1.0);
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(URL url, SimpleDateFormat dateFormat, double divider)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    URLConnection connection = url.openConnection();
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        if (inputLine.trim().length() == 0 || !Character.isDigit(inputLine.charAt(0))) {
          // First line throw away
          continue;
        }
        final String[] items = inputLine.split(",");
        if (items.length == 7) {
          historyquotes.add(parseResponseLineItems(items, dateFormat, divider));
        }
      }
    }
    return historyquotes;
  }

  /**
   * Date,Open,High,Low,Close,Adjusted_close,Volume
   * 2021-02-01,300.95,301.5,299,300.65,300.65,8813
   * 2021-02-02,302.15,306.55,302.15,305.85,305.85,4859
   * 
   * @param inputLine
   * @param dateFormat
   * @return
   * @throws ParseException
   */
  private Historyquote parseResponseLineItems(final String[] items, SimpleDateFormat dateFormat, double divider)
      throws ParseException {
    Historyquote historyquote = new Historyquote();
    historyquote.setDate(dateFormat.parse(items[0]));
    historyquote.setOpen(Double.parseDouble(items[1]) / divider);
    historyquote.setHigh(Double.parseDouble(items[2]) / divider);
    historyquote.setLow(Double.parseDouble(items[3]) / divider);
    historyquote.setClose(Double.parseDouble(items[5]) / divider);
    historyquote.setVolume(Long.parseLong(items[6]));
    return historyquote;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return getSecurityCurrencyIntradayDownloadLink(security.getUrlIntraExtend());
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    var quote = objectMapper.readValue(new URL(getSecurityIntradayDownloadLink(security)), Quote.class);
    quote.setValues(security, FeedConnectorHelper.getGBXLondonDivider(security), getIntradayDelayedSeconds());
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return getSecurityCurrencyIntradayDownloadLink(getCurrencyPairSymbol(currencypair));
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    var quote = objectMapper.readValue(new URL(getCurrencypairIntradayDownloadLink(currencypair)), Quote.class);
    quote.setValues(currencypair, 1.0, getIntradayDelayedSeconds());
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  private String getSecurityCurrencyIntradayDownloadLink(final String ticker) {
    return DOMAIN_NAME_API + "real-time/" + ticker.toUpperCase() + "?" + JSON_PARAM + getApiKeyString();
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return getDividendSplitHistoricalDownloadLink(security.getUrlDividendExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), DIVDEND_EVENT);
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    List<Dividend> dividends = new ArrayList<>();
    DividendRead[] dividendRead = objectMapper.readValue(
        new URL(getDividendSplitHistoricalDownloadLink(security.getUrlDividendExtend(), fromDate, DIVDEND_EVENT)),
        DividendRead[].class);
    for (int i = 0; i < dividendRead.length; i++) {
      Dividend dividend = new Dividend(security.getIdSecuritycurrency(), dividendRead[i].date,
          dividendRead[i].paymentDate, dividendRead[i].unadjustedValue, dividendRead[i].value, dividendRead[i].currency,
          CreateType.CONNECTOR_CREATED);
      dividends.add(dividend);
    }
    return dividends;
  }

  private String getDividendSplitHistoricalDownloadLink(String symbol, LocalDate fromDate, String event) {
    return DOMAIN_NAME_API + event + "/" + symbol + "?from=" + fromDate + "&" + JSON_PARAM + getApiKeyString();
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getDividendSplitHistoricalDownloadLink(security.getUrlSplitExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), SPLIT_EVENT);
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate) throws Exception {
    List<Securitysplit> securitySplits = new ArrayList<>();
    Split[] splits = objectMapper.readValue(
        new URL(getDividendSplitHistoricalDownloadLink(security.getUrlSplitExtend(), fromDate, SPLIT_EVENT)),
        Split[].class);
    FractionFormat fractionFormat = new FractionFormat(NumberFormat.getInstance(Locale.US));
    for (int i = 0; i < splits.length; i++) {
      Fraction fraction = fractionFormat.parse(splits[i].split);
      Securitysplit securitysplit = new Securitysplit(security.getIdSecuritycurrency(), splits[i].date,
          fraction.getDenominator(), fraction.getNumerator(), CreateType.CONNECTOR_CREATED);
      securitySplits.add(securitysplit);
    }
    return securitySplits;
  }

  private static class Quote {
   // public String code;
   // public long timestamp;
   // public int gmtoffset;
    public double open;
    public double high;
    public double low;
    public double close;
    public double previousClose;
   // public double change;
    public double change_p;

    public void setValues(Securitycurrency<?> securitycurrency, double divider, int delaySeconds) {
      securitycurrency.setSLast(close / divider);
      securitycurrency.setSOpen(open / divider);
      securitycurrency.setSLow(low / divider);
      securitycurrency.setSHigh(high / divider);
      securitycurrency.setSChangePercentage(change_p);
      securitycurrency.setSPrevClose(previousClose / divider);
      securitycurrency.setSTimestamp(new Date(new Date().getTime() - delaySeconds * 1000));
    }
  }

  private static class DividendRead {
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public LocalDate date;
    // public LocalDate declarationDate;
    // public LocalDate recordDate;
    public LocalDate paymentDate;
    // public String period;
    public double value;
    public double unadjustedValue;
    public String currency;
  }

  private static class Split {
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public Date date;
    public String split;
  }

}
