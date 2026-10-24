package grafioschtrader.connector.instrument.eodhistoricaldata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.securitysearch.EodhdSecuritySearch;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.CreateType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * There is no regex pattern check for the URL. The URL check with connection establishment can only be used for
 * historical course data. For intraday, response body is equal to an existing security with the value "NA".
 *
 */
@Component
public class EodHistoricalDataConnector extends BaseFeedApiKeyConnector {

  private static final String DOMAIN_NAME_API = "https://eodhistoricaldata.com/api/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String DIVDEND_EVENT = "div";
  private static final String SPLIT_EVENT = "splits";
  private static final String JSON_PARAM = "fmt=json";
  private static final String TOKEN_PARAM_NAME = "api_token";

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private static final int INTRADAY_BATCH_SIZE = 15;

  private final Semaphore semaphoreLastPrice = new Semaphore(5);

  @Autowired(required = false)
  private MicProviderMapRepository micProviderMapRepository;

  /** EODHD exchange code per MIC, e.g. XETRA for XETR; read once from {@code mic_provider_map}. */
  private volatile Map<String, String> exchangeCodeByMic;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY });
    supportedFeed.put(FeedSupport.FS_SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public EodHistoricalDataConnector() {
    super(supportedFeed, "eodhistoricaldata", "EOD Historical Data", null, EnumSet.of(UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR, AssetclassCategory.CRYPTOCURRENCY,
        AssetclassCategory.EQUITIES, AssetclassCategory.ETF, AssetclassCategory.ISSUER_RISK_PRODUCT,
        AssetclassCategory.CFD_DERIVATIVE, AssetclassCategory.NON_INVESTABLE_INDICES);
  }

  /**
   * The EODHD symbol is ticker plus exchange code, e.g. {@code SAP.XETRA}. Master data of the EODHD search carries it;
   * for a listing another provider found it is built from the exchange ticker and the {@code eodhd} code of the MIC in
   * {@code mic_provider_map}, with {@code -} for a dot or blank in the ticker ({@code BRK-B.US}). All four feeds use
   * the same symbol.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    if (EodhdSecuritySearch.PROVIDER_ID.equals(baseData.providerId()) && baseData.providerInstrumentId() != null) {
      return Optional.of(baseData.providerInstrumentId());
    }
    if (baseData.tickerSymbol() == null || baseData.tickerSymbol().isBlank() || baseData.mic() == null) {
      return Optional.empty();
    }
    String code = getExchangeCodeByMic().get(baseData.mic());
    return code == null ? Optional.empty()
        : Optional.of(baseData.tickerSymbol().trim().replace(' ', '-').replace('.', '-') + "." + code);
  }

  private Map<String, String> getExchangeCodeByMic() {
    Map<String, String> codeByMic = exchangeCodeByMic;
    if (codeByMic == null) {
      if (micProviderMapRepository == null) {
        return Map.of();
      }
      codeByMic = new HashMap<>();
      for (MicProviderMap row : micProviderMapRepository
          .findByIdProviderMic_IdProvider(EodhdSecuritySearch.PROVIDER_ID)) {
        codeByMic.put(row.getIdProviderMic().getMic(), row.getCodeProvider());
      }
      exchangeCodeByMic = codeByMic;
    }
    return codeByMic;
  }

  private String getApiKeyString() {
    return "&" + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  @Override
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, TOKEN_PARAM_NAME);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate toDate = LocalDate.now();
    LocalDate fromDate = toDate.minusDays(7);
    return getSecurityHistoricalDownloadLink(security, fromDate, toDate);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    LocalDate toDate = LocalDate.now();
    LocalDate fromDate = toDate.minusDays(7);
    return getCurrencypairHistoricalDownloadLink(currencypair, fromDate, toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, LocalDate from, LocalDate to) {
    return getSecurityCurrencyHistoricalDownloadLink(security.getUrlHistoryExtend().toUpperCase(), from, to);
  }

  private String getSecurityCurrencyHistoricalDownloadLink(String ticker, LocalDate from, LocalDate to) {
    return DOMAIN_NAME_API + "eod/" + ticker + "?from=" + from.format(DATE_FORMAT) + "&to=" + to.format(DATE_FORMAT)
        + getApiKeyString();
  }

  private String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair, LocalDate from, LocalDate to) {
    return getSecurityCurrencyHistoricalDownloadLink(getCurrencyPairSymbol(currencypair), from, to);
  }

  private String getCurrencyPairSymbol(final Currencypair currencypair) {
    if (currencypair.getIsCryptocurrency()) {
      return currencypair.getFromCurrency() + "-" + currencypair.getToCurrency() + ".CC";
    } else {
      return currencypair.getFromCurrency() + currencypair.getToCurrency() + ".FOREX";
    }
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(new URI(getSecurityHistoricalDownloadLink(security, from, to)).toURL(),
        FeedConnectorHelper.getMinorUnitDivider(security));
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws Exception {
    return getEodSecurityCurrencypairHistory(
        new URI(getCurrencypairHistoricalDownloadLink(currencyPair, from, to)).toURL(), 1.0);
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(URL url, double divider) throws Exception {
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
          historyquotes.add(parseResponseLineItems(items, divider));
        }
      }
    }
    return historyquotes;
  }

  /**
   * Date,Open,High,Low,Close,Adjusted_close,Volume 2021-02-01,300.95,301.5,299,300.65,300.65,8813
   * 2021-02-02,302.15,306.55,302.15,305.85,305.85,4859
   */
  private Historyquote parseResponseLineItems(final String[] items, double divider) {
    Historyquote historyquote = new Historyquote();
    historyquote.setDate(LocalDate.parse(items[0], DATE_FORMAT));
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
    updateLastPrice(security, getSecurityIntradayDownloadLink(security),
        FeedConnectorHelper.getMinorUnitDivider(security));
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return getSecurityCurrencyIntradayDownloadLink(getCurrencyPairSymbol(currencypair));
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateLastPrice(currencypair, getCurrencypairIntradayDownloadLink(currencypair), 1.0);
  }

  /**
   * There are many timeout exceptions, so this is an attempt to avoid this with a semaphore.
   */
  private <T extends Securitycurrency<T>> void updateLastPrice(T securitycurrency, String url, double divider)
      throws Exception {
    semaphoreLastPrice.acquire();
    try {
      var quote = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(url, 10).body(), Quote.class);
      quote.setValues(securitycurrency, divider, getIntradayDelayedSeconds());
    } finally {
      // Release the permit after the work is done
      semaphoreLastPrice.release();
    }
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  /** EODHD recommends no more than 15 to 20 tickers per real-time request. */
  @Override
  public int getIntradayBatchSize() {
    return INTRADAY_BATCH_SIZE;
  }

  @Override
  public List<Security> updateSecuritiesLastPrice(final List<Security> securities) throws Exception {
    return updateLastPrices(securities, Security::getUrlIntraExtend, FeedConnectorHelper::getMinorUnitDivider);
  }

  @Override
  public List<Currencypair> updateCurrencyPairsLastPrice(final List<Currencypair> currencypairs) throws Exception {
    return updateLastPrices(currencypairs, this::getCurrencyPairSymbol, _ -> 1.0);
  }

  /**
   * Requests the real-time quotes of all instruments with one call: the first symbol is the path, the others go into
   * the {@code s} parameter. One permit of the semaphore covers the whole request.
   *
   * @return the instruments without symbol or without usable quote in the answer
   */
  private <T extends Securitycurrency<T>> List<T> updateLastPrices(final List<T> instruments,
      final Function<T, String> symbolOf, final ToDoubleFunction<T> divider) throws Exception {
    final List<T> missing = new ArrayList<>();
    final Map<String, List<T>> bySymbol = new LinkedHashMap<>();
    for (final T instrument : instruments) {
      final String symbol = symbolOf.apply(instrument);
      if (symbol == null || symbol.isBlank()) {
        missing.add(instrument);
      } else {
        bySymbol.computeIfAbsent(symbol.toUpperCase(Locale.ROOT), _ -> new ArrayList<>()).add(instrument);
      }
    }
    if (!bySymbol.isEmpty()) {
      final String body;
      semaphoreLastPrice.acquire();
      try {
        body = FeedConnectorHelper.getByHttpClient(getBatchIntradayDownloadLink(bySymbol.keySet()), 10).body();
      } finally {
        semaphoreLastPrice.release();
      }
      missing.addAll(applyQuotes(body, bySymbol, divider, getIntradayDelayedSeconds()));
    }
    return missing;
  }

  private String getBatchIntradayDownloadLink(final Collection<String> symbols) {
    final List<String> encoded = symbols.stream().map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).toList();
    final String others = encoded.size() > 1 ? "s=" + String.join(",", encoded.subList(1, encoded.size())) + "&" : "";
    return DOMAIN_NAME_API + "real-time/" + encoded.getFirst() + "?" + others + JSON_PARAM + getApiKeyString();
  }

  /**
   * Applies a real-time answer to the instruments. The answer is an array with one element per ticker, or a single
   * object when only one ticker was requested. Elements are matched by their {@code code}, not by position, and are
   * converted one by one, so that an element with {@code "NA"} values of an unknown ticker leaves only its own
   * instruments without price.
   *
   * @param body         the response body of the real-time request
   * @param bySymbol     the instruments by upper-case EODHD symbol; several instruments may share one symbol
   * @param divider      the divider of each instrument, e.g. 100 for a price quoted in pence
   * @param delaySeconds the delay subtracted from the current time for the quote timestamp
   * @return the instruments the answer held no usable quote for
   */
  static <T extends Securitycurrency<T>> List<T> applyQuotes(final String body, final Map<String, List<T>> bySymbol,
      final ToDoubleFunction<T> divider, final int delaySeconds) {
    final JsonNode root = objectMapper.readTree(body);
    final Iterable<JsonNode> nodes = root.isArray() ? root : List.of(root);
    final Map<String, List<T>> open = new LinkedHashMap<>(bySymbol);
    for (final JsonNode node : nodes) {
      final Quote quote;
      try {
        quote = objectMapper.treeToValue(node, Quote.class);
      } catch (final JacksonException e) {
        continue;
      }
      final List<T> matched = quote.code == null ? null : open.remove(quote.code.toUpperCase(Locale.ROOT));
      if (matched != null) {
        matched.forEach(sc -> quote.setValues(sc, divider.applyAsDouble(sc), delaySeconds));
      }
    }
    return open.values().stream().flatMap(List::stream).toList();
  }

  private String getSecurityCurrencyIntradayDownloadLink(final String ticker) {
    return DOMAIN_NAME_API + "real-time/" + ticker.toUpperCase() + "?" + JSON_PARAM + getApiKeyString();
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return getDividendSplitHistoricalDownloadLink(security.getUrlDividendExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), LocalDate.now(), DIVDEND_EVENT);
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    List<Dividend> dividends = new ArrayList<>();
    DividendRead[] dividendRead = objectMapper
        .readValue(new URI(getDividendSplitHistoricalDownloadLink(security.getUrlDividendExtend(), fromDate,
            LocalDate.now(), DIVDEND_EVENT)).toURL().openStream(), DividendRead[].class);
    for (DividendRead element : dividendRead) {
      Dividend dividend = new Dividend(security.getIdSecuritycurrency(), element.date, element.paymentDate,
          element.unadjustedValue, element.value, element.currency, CreateType.CONNECTOR_CREATED);
      dividends.add(dividend);
    }
    return dividends;
  }

  private String getDividendSplitHistoricalDownloadLink(String symbol, LocalDate fromDate, LocalDate toDate,
      String event) {
    return DOMAIN_NAME_API + event + "/" + symbol + "?from=" + fromDate + "&to=" + toDate + "&" + JSON_PARAM
        + getApiKeyString();
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getDividendSplitHistoricalDownloadLink(security.getUrlSplitExtend(),
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), LocalDate.now(), SPLIT_EVENT);
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception {
    List<Securitysplit> securitySplits = new ArrayList<>();
    Split[] splits = objectMapper.readValue(
        new URI(getDividendSplitHistoricalDownloadLink(security.getUrlSplitExtend(), fromDate, toDate, SPLIT_EVENT))
            .toURL().openStream(),
        Split[].class);
    FractionFormat fractionFormat = new FractionFormat(NumberFormat.getInstance(Locale.US));
    for (Split split : splits) {
      Fraction fraction = fractionFormat.parse(split.split);
      Securitysplit securitysplit = new Securitysplit(security.getIdSecuritycurrency(), split.date,
          fraction.getDenominator(), fraction.getNumerator(), CreateType.CONNECTOR_CREATED);
      securitySplits.add(securitysplit);
    }
    return securitySplits;
  }

  private static class Quote {
    public String code;
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
      securitycurrency.setSTimestamp(LocalDateTime.now().minusSeconds(delaySeconds));
    }
  }

  private static class DividendRead {
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
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
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
    public LocalDate date;
    public String split;
  }

}
