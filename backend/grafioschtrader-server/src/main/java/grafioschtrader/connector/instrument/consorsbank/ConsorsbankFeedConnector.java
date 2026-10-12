package grafioschtrader.connector.instrument.consorsbank;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.MinorCurrencyUnit;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.securitysearch.ConsorsbankSecuritySearch;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public Consorsbank quotes identified by a venue-specific notation ID and exchange code. HistoryV1 supplies daily
 * OHLCV, including weekend observations for currencies and cryptocurrencies. Missing sessions and provider adjustments
 * are preserved: this connector neither fills gaps nor repairs historical prices. API errors can arrive with HTTP 200.
 *
 * <p>
 * The URL extension {@code _notation,exchange code} is not derivable from an ISIN without a request; the security
 * lookup takes it from the listing {@link ConsorsbankSecuritySearch} found on the queried exchange.
 * </p>
 */
@Component
public class ConsorsbankFeedConnector extends BaseFeedConnector {
  private static final String ENDPOINT = "https://www.consorsbank.de/web-financialinfo-service/api/marketdata/stocks";
  private static final String EXTENSION_PATTERN = "^_[0-9]+,[A-Z0-9@$_]+$";
  private static final int PAGE_SIZE = 1000;
  private static final ObjectMapper MAPPER = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String endpoint;

  public ConsorsbankFeedConnector() {
    this(ENDPOINT);
  }

  /** Alternate endpoint for deterministic HTTP tests; production uses the public Consorsbank endpoint. */
  ConsorsbankFeedConnector(String endpoint) {
    super(
        Map.of(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL },
            FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL }),
        "consorsbank", "Consorsbank", EXTENSION_PATTERN, EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
    this.endpoint = endpoint;
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.FIXED_INCOME,
        AssetclassCategory.ETF, AssetclassCategory.CURRENCY_PAIR, AssetclassCategory.CRYPTOCURRENCY);
  }

  /** History and intraday use the notation the Consorsbank search found, see the class documentation. */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    return ConsorsbankSecuritySearch.PROVIDER_ID.equals(baseData.providerId())
        && baseData.providerInstrumentId() != null ? Optional.of(baseData.providerInstrumentId()) : Optional.empty();
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public String getSecurityIntradayDownloadLink(Security security) {
    return priceUrl(security.getUrlIntraExtend());
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(Currencypair currencyPair) {
    return priceUrl(currencyPair.getUrlIntraExtend());
  }

  @Override
  public String getSecurityHistoricalDownloadLink(Security security) {
    return historyUrl(security.getUrlHistoryExtend(), 1, 0);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(Currencypair currencyPair) {
    return historyUrl(currencyPair.getUrlHistoryExtend(), 1, 0);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(Security security, LocalDate from, LocalDate to) throws Exception {
    return history(security.getUrlHistoryExtend(), from, to);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, LocalDate from, LocalDate to)
      throws Exception {
    return history(currencyPair.getUrlHistoryExtend(), from, to);
  }

  private String priceUrl(String extension) {
    String[] parts = splitExtension(extension);
    return endpoint + "?id=" + encode(parts[0]) + "&field=PriceV2&rtExchangeCode=" + encode(parts[1]);
  }

  private String historyUrl(String extension, long months, int page) {
    return endpoint + "?id=" + encode(splitExtension(extension)[0]) + "&field=HistoryV1&range=-" + months
        + "&resolution=1D&page=" + page + "&pagesize=" + PAGE_SIZE;
  }

  private static String[] splitExtension(String extension) {
    if (extension == null || !extension.matches(EXTENSION_PATTERN)) {
      throw new IllegalArgumentException("Expected Consorsbank notation ID and exchange code, e.g. _39471840,GER");
    }
    return extension.split(",");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /** The endpoint accepts a lookback in months, not an absolute interval. Filter each returned day locally. */
  private List<Historyquote> history(String extension, LocalDate from, LocalDate to) throws Exception {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("Historical start date must not follow end date");
    }
    long months = Math.max(1,
        ChronoUnit.MONTHS.between(from.withDayOfMonth(1), LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)) + 1);
    Map<LocalDate, DailyQuote> quotes = new TreeMap<>();
    Integer total = null;
    Integer pageSize = null;
    LocalDate previous = null;
    for (int page = 0;; page++) {
      HistoryPage data = request(historyUrl(extension, months, page)).HistoryV1;
      validatePage(data, page, total);
      if (pageSize != null && !pageSize.equals(data.PAGE_SIZE)) {
        throw new IOException("Inconsistent Consorsbank pagination page size");
      }
      total = data.TOTAL_AMOUNT;
      pageSize = data.PAGE_SIZE;
      if (data.ITEMS != null) {
        for (DailyQuote quote : data.ITEMS) {
          LocalDate date = timestamp(quote.DATETIME_LAST).toLocalDate();
          if (previous != null && date.isAfter(previous)) {
            throw new IOException("Consorsbank history is not in descending date order");
          }
          previous = date;
          if (!date.isBefore(from) && !date.isAfter(to)) {
            requirePrice(quote.LAST);
            DailyQuote existing = quotes.putIfAbsent(date, quote);
            if (existing != null && !existing.equals(quote)) {
              throw new IOException("Conflicting Consorsbank quotes for " + date);
            }
          }
        }
      }
      if (page + 1 >= data.TOTAL_PAGES || previous != null && previous.isBefore(from)) {
        break;
      }
    }
    List<Historyquote> result = new ArrayList<>();
    quotes.forEach((date, quote) -> result.add(toHistoryquote(date, quote)));
    return result;
  }

  /** Reject truncated, repeated or inconsistent pages instead of returning an apparently successful partial history. */
  private static void validatePage(HistoryPage data, int page, Integer total) throws IOException {
    if (data == null || data.PAGE_INDEX == null || data.PAGE_SIZE == null || data.TOTAL_PAGES == null
        || data.TOTAL_AMOUNT == null || data.AMOUNT == null || data.PAGE_INDEX != page || data.PAGE_SIZE <= 0
        || data.TOTAL_AMOUNT < 0 || data.TOTAL_PAGES < 1 || page >= data.TOTAL_PAGES
        || total != null && !total.equals(data.TOTAL_AMOUNT)) {
      throw new IOException("Missing or inconsistent Consorsbank pagination metadata");
    }
    long pages = Math.max(1, (data.TOTAL_AMOUNT.longValue() + data.PAGE_SIZE - 1) / data.PAGE_SIZE);
    long expected = Math.min(data.PAGE_SIZE, data.TOTAL_AMOUNT.longValue() - (long) page * data.PAGE_SIZE);
    int actual = data.ITEMS == null ? 0 : data.ITEMS.size();
    if (pages != data.TOTAL_PAGES || expected != actual || data.AMOUNT != actual) {
      throw new IOException("Incomplete Consorsbank history page " + page);
    }
  }

  private static Historyquote toHistoryquote(LocalDate date, DailyQuote quote) {
    Historyquote result = new Historyquote();
    result.setDate(date);
    result.setClose(price(quote.LAST, quote.ISO_CURRENCY));
    result.setOpen(price(quote.FIRST, quote.ISO_CURRENCY));
    result.setHigh(price(quote.HIGH, quote.ISO_CURRENCY));
    result.setLow(price(quote.LOW, quote.ISO_CURRENCY));
    result.setVolume(quote.TOTAL_VOLUME == null ? null : quote.TOTAL_VOLUME.longValue());
    return result;
  }

  @Override
  public void updateSecurityLastPrice(Security security) throws Exception {
    Price data = request(getSecurityIntradayDownloadLink(security)).PriceV2;
    updatePrice(security, data);
    security.setSVolume(data.TOTAL_VOLUME == null ? null : data.TOTAL_VOLUME.longValue());
  }

  @Override
  public void updateCurrencyPairLastPrice(Currencypair currencyPair) throws Exception {
    updatePrice(currencyPair, request(getCurrencypairIntradayDownloadLink(currencyPair)).PriceV2);
  }

  private static <S extends Securitycurrency<S>> void updatePrice(Securitycurrency<S> instrument, Price data)
      throws IOException {
    if (data == null) {
      throw new IOException("Missing Consorsbank PriceV2");
    }
    requirePrice(data.PRICE);
    var time = timestamp(data.DATETIME_PRICE).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    instrument.setSLast(price(data.PRICE, data.ISO_CURRENCY));
    instrument.setSChangePercentage(
        data.PERFORMANCE_PCT == null ? null : DataBusinessHelper.roundPercentage(data.PERFORMANCE_PCT));
    instrument.setSPrevClose(price(data.PREVIOUS_LAST, data.ISO_CURRENCY));
    instrument.setSOpen(price(data.FIRST, data.ISO_CURRENCY));
    instrument.setSLow(price(data.LOW, data.ISO_CURRENCY));
    instrument.setSHigh(price(data.HIGH, data.ISO_CURRENCY));
    instrument.setSTimestamp(time);
  }

  private static Double price(Double value, String currency) {
    return value == null ? null
        : value / (MinorCurrencyUnit.isMinorUnit(currency) ? MinorCurrencyUnit.MINOR_TO_MAJOR_DIVIDER : 1.0);
  }

  private static void requirePrice(Double value) throws IOException {
    if (value == null || !Double.isFinite(value) || value <= 0) {
      throw new IOException("Missing or invalid Consorsbank closing price");
    }
  }

  private static OffsetDateTime timestamp(String value) throws IOException {
    try {
      return OffsetDateTime.parse(value.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2"));
    } catch (RuntimeException e) {
      throw new IOException("Missing or invalid Consorsbank quote timestamp: " + value, e);
    }
  }

  private RootData request(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
    var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != HttpURLConnection.HTTP_OK) {
      throw new IOException("Consorsbank HTTP " + response.statusCode());
    }
    return parseResponse(response.body());
  }

  private static RootData parseResponse(String body) throws IOException {
    RootData[] roots = MAPPER.readValue(body, RootData[].class);
    if (roots == null || roots.length != 1 || roots[0] == null) {
      throw new IOException("Missing Consorsbank response");
    }
    RootData root = roots[0];
    if (root.Info != null && root.Info.Errors != null && !root.Info.Errors.isEmpty()) {
      throw new IOException("Consorsbank API error: " + root.Info.Errors);
    }
    return root;
  }

  @Override
  protected boolean isConnectionOk(HttpURLConnection connection) {
    try {
      RootData root = parseResponse(getBodyAsString(connection));
      if (root.HistoryV1 != null) {
        validatePage(root.HistoryV1, 0, null);
      } else {
        requirePrice(root.PriceV2 == null ? null : root.PriceV2.PRICE);
      }
      return true;
    } catch (IOException | RuntimeException _) {
      return false;
    }
  }

  record DailyQuote(String DATETIME_LAST, Double FIRST, Double HIGH, Double LOW, Double LAST, Double TOTAL_VOLUME,
      String ISO_CURRENCY) {
  }

  static class RootData {
    public Price PriceV2;
    public HistoryPage HistoryV1;
    public Info Info;
  }

  static class Info {
    public List<ApiError> Errors;
  }

  record ApiError(String ERROR_CODE, String ERROR_MESSAGE) {
  }

  static class HistoryPage {
    public Integer AMOUNT;
    public Integer PAGE_INDEX;
    public Integer PAGE_SIZE;
    public Integer TOTAL_AMOUNT;
    public Integer TOTAL_PAGES;
    public List<DailyQuote> ITEMS;
  }

  static class Price {
    public Double PRICE;
    public Double PERFORMANCE_PCT;
    public Double PREVIOUS_LAST;
    public Double FIRST;
    public Double LOW;
    public Double HIGH;
    public Double TOTAL_VOLUME;
    public String ISO_CURRENCY;
    public String DATETIME_PRICE;
  }
}
