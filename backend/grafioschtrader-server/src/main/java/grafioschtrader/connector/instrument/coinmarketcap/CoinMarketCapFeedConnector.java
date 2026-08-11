package grafioschtrader.connector.instrument.coinmarketcap;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafiosch.common.DataHelper;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.ObjectMapper;

/**
 * Supplies historical and intraday cryptocurrency rates from CoinMarketCap's chart endpoint. CoinMarketCap identifies
 * both assets with numeric IDs, so the URL extension stores symbol-to-ID mappings such as
 * {@code BTC=1,CHF=2785}. Keeping the symbols in the extension makes the mapping verifiable and allows the same value
 * to serve both BTC/CHF and CHF/BTC.
 */
@Component
public class CoinMarketCapFeedConnector extends BaseFeedConnector {

  @Autowired
  private GlobalparametersService globalparametersService;

  private static final String SHORT_ID = "coinmarketcap";
  private static final String BASE_URL =
      "https://api.coinmarketcap.com/data-api/v3.3/cryptocurrency/detail/chart";
  private static final String URL_EXTENSION_REGEX =
      "^[A-Za-z0-9]{1,15}=[1-9][0-9]*,[A-Za-z0-9]{1,15}=[1-9][0-9]*$";
  private static final Pattern URL_EXTENSION_PATTERN = Pattern.compile(
      "^([A-Za-z0-9]{1,15})=([1-9][0-9]*),([A-Za-z0-9]{1,15})=([1-9][0-9]*)$");
  private static final int REQUEST_TIMEOUT_SECONDS = 30;
  private static final int HISTORICAL_WINDOW_YEARS = 3;
  private static final int INTRADAY_DELAY_SECONDS = 300;
  private static final Map<String, String> JSON_HEADERS = Map.of("Accept", "application/json");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Map<FeedSupport, FeedIdentifier[]> SUPPORTED_FEED = new HashMap<>();

  static {
    SUPPORTED_FEED.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY_URL });
    SUPPORTED_FEED.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.CURRENCY_URL });
  }

  public CoinMarketCapFeedConnector() {
    super(SUPPORTED_FEED, SHORT_ID, "CoinMarketCap", URL_EXTENSION_REGEX,
        EnumSet.of(UrlCheck.HISTORY, UrlCheck.INTRADAY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CRYPTOCURRENCY);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(Currencypair currencypair) {
    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    return createChartUrl(parseMapping(currencypair, currencypair.getUrlHistoryExtend()), "1d", to.minusDays(7), to);
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(Currencypair currencypair) {
    long timeEnd = Instant.now().getEpochSecond();
    return createChartUrl(parseMapping(currencypair, currencypair.getUrlIntraExtend()), "5m", timeEnd - 86_400,
        timeEnd);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencypair, LocalDate from, LocalDate to)
      throws Exception {
    CoinMarketCapMapping mapping = parseMapping(currencypair, currencypair.getUrlHistoryExtend());
    int quoteCurrencyPrecision = globalparametersService.getPrecisionForCurrency(currencypair.getToCurrency());
    Map<LocalDate, Historyquote> quotesByDate = new TreeMap<>();

    LocalDate windowStart = from;
    while (!windowStart.isAfter(to)) {
      LocalDate windowEnd = windowStart.plusYears(HISTORICAL_WINDOW_YEARS).minusDays(1);
      if (windowEnd.isAfter(to)) {
        windowEnd = to;
      }
      String url = createChartUrl(mapping, "1d", windowStart, windowEnd);
      for (TimedPrice timedPrice : extractPrices(readPoints(url), mapping)) {
        LocalDate quoteDate = timedPrice.instant().atZone(ZoneOffset.UTC).toLocalDate();
        if (!quoteDate.isBefore(from) && !quoteDate.isAfter(to)) {
          Historyquote historyquote = new Historyquote();
          historyquote.setDate(quoteDate);
          historyquote.setClose(DataHelper.round(timedPrice.price(), quoteCurrencyPrecision));
          quotesByDate.put(quoteDate, historyquote);
        }
      }
      windowStart = windowEnd.plusDays(1);
    }
    return new ArrayList<>(quotesByDate.values());
  }

  @Override
  public void updateCurrencyPairLastPrice(Currencypair currencypair) throws Exception {
    CoinMarketCapMapping mapping = parseMapping(currencypair, currencypair.getUrlIntraExtend());
    int quoteCurrencyPrecision = globalparametersService.getPrecisionForCurrency(currencypair.getToCurrency());
    List<TimedPrice> prices = extractPrices(readPoints(getCurrencypairIntradayDownloadLink(currencypair)), mapping);
    if (prices.isEmpty()) {
      throw new IOException("CoinMarketCap returned no intraday prices for " + currencypair.getName());
    }

    prices.sort(Comparator.comparing(TimedPrice::instant));
    TimedPrice lastPrice = prices.getLast();
    LocalDate currentUtcDate = lastPrice.instant().atZone(ZoneOffset.UTC).toLocalDate();
    List<TimedPrice> currentDayPrices = prices.stream()
        .filter(price -> price.instant().atZone(ZoneOffset.UTC).toLocalDate().equals(currentUtcDate)).toList();
    if (currentDayPrices.isEmpty()) {
      currentDayPrices = List.of(lastPrice);
    }

    double open = currentDayPrices.getFirst().price();
    double previousClose = prices.stream()
        .filter(price -> price.instant().atZone(ZoneOffset.UTC).toLocalDate().isBefore(currentUtcDate))
        .map(TimedPrice::price).reduce((_, current) -> current).orElse(open);
    double high = currentDayPrices.stream().mapToDouble(TimedPrice::price).max().orElse(lastPrice.price());
    double low = currentDayPrices.stream().mapToDouble(TimedPrice::price).min().orElse(lastPrice.price());

    currencypair.setSLast(DataHelper.round(lastPrice.price(), quoteCurrencyPrecision));
    currencypair.setSOpen(DataHelper.round(open, quoteCurrencyPrecision));
    currencypair.setSHigh(DataHelper.round(high, quoteCurrencyPrecision));
    currencypair.setSLow(DataHelper.round(low, quoteCurrencyPrecision));
    currencypair.setSPrevClose(DataHelper.round(previousClose, quoteCurrencyPrecision));
    currencypair
        .setSChangePercentage(previousClose == 0 ? 0 : (lastPrice.price() - previousClose) / previousClose * 100);
    currencypair.setSTimestamp(LocalDateTime.now().minusSeconds(getIntradayDelayedSeconds()));
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return INTRADAY_DELAY_SECONDS;
  }

  @Override
  protected <S extends Securitycurrency<S>> void extendedCheck(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport, String urlExtend, String errorMsgKey, FeedIdentifier feedIdentifier,
      SpecialInvestmentInstruments specialInvestmentInstruments, AssetclassType assetclassType) {
    if (!(securitycurrency instanceof Currencypair currencypair)) {
      throw new GeneralNotTranslatedWithArgumentsException(errorMsgKey, null);
    }
    try {
      parseMapping(currencypair, urlExtend);
    } catch (IllegalArgumentException _) {
      throw new GeneralNotTranslatedWithArgumentsException(errorMsgKey, null);
    }
  }

  @Override
  protected void checkUrl(String url, String failureMsgKey, FeedSupport feedSupport) {
    try {
      List<CoinMarketCapPoint> points = readPoints(url);
      boolean nativeUsd = url.contains("convertId=2781&");
      boolean containsPrices = points.stream().anyMatch(point -> point.c != null && !point.c.isEmpty()
          || nativeUsd && point.v != null && !point.v.isEmpty());
      if (!containsPrices) {
        throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { url });
      }
    } catch (GeneralNotTranslatedWithArgumentsException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { url });
    } catch (Exception _) {
      throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { url });
    }
  }

  private List<CoinMarketCapPoint> readPoints(String url) throws IOException, InterruptedException {
    HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url, REQUEST_TIMEOUT_SECONDS, JSON_HEADERS);
    if (response.statusCode() != 200) {
      throw new IOException("CoinMarketCap request failed with HTTP status " + response.statusCode());
    }
    CoinMarketCapResponse responseBody = OBJECT_MAPPER.readValue(response.body(), CoinMarketCapResponse.class);
    if (responseBody.status == null || !"0".equals(responseBody.status.error_code)) {
      String message = responseBody.status == null ? "missing response status" : responseBody.status.error_message;
      throw new IOException("CoinMarketCap request failed: " + message);
    }
    return responseBody.data == null || responseBody.data.points == null ? List.of() : responseBody.data.points;
  }

  private List<TimedPrice> extractPrices(List<CoinMarketCapPoint> points, CoinMarketCapMapping mapping) {
    List<TimedPrice> prices = new ArrayList<>();
    for (CoinMarketCapPoint point : points) {
      BigDecimal convertedValue = getConvertedValue(point, mapping.providerQuoteId());
      if (convertedValue != null) {
        double directPrice = convertedValue.doubleValue();
        double price = mapping.inverted() ? 1.0 / directPrice : directPrice;
        if (directPrice > 0 && Double.isFinite(price)) {
          prices.add(new TimedPrice(Instant.ofEpochSecond(Long.parseLong(point.s)), price));
        }
      }
    }
    return prices;
  }

  private BigDecimal getConvertedValue(CoinMarketCapPoint point, String providerQuoteId) {
    List<BigDecimal> convertedValues = point.c == null ? null : point.c.get(providerQuoteId);
    if (convertedValues != null && !convertedValues.isEmpty() && convertedValues.getFirst() != null) {
      return convertedValues.getFirst();
    }
    // USD is CoinMarketCap's native quote currency and is returned in v instead of c[2781].
    return "2781".equals(providerQuoteId) && point.v != null && !point.v.isEmpty() ? point.v.getFirst() : null;
  }

  private CoinMarketCapMapping parseMapping(Currencypair currencypair, String urlExtension) {
    Matcher matcher = URL_EXTENSION_PATTERN.matcher(urlExtension == null ? "" : urlExtension);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid CoinMarketCap URL extension");
    }
    String providerBaseSymbol = matcher.group(1).toUpperCase(Locale.ROOT);
    String providerQuoteSymbol = matcher.group(3).toUpperCase(Locale.ROOT);
    if (providerBaseSymbol.equals(providerQuoteSymbol)) {
      throw new IllegalArgumentException("CoinMarketCap symbols must be different");
    }

    boolean direct = currencypair.getFromCurrency().equalsIgnoreCase(providerBaseSymbol)
        && currencypair.getToCurrency().equalsIgnoreCase(providerQuoteSymbol);
    boolean inverted = currencypair.getFromCurrency().equalsIgnoreCase(providerQuoteSymbol)
        && currencypair.getToCurrency().equalsIgnoreCase(providerBaseSymbol);
    if (!direct && !inverted) {
      throw new IllegalArgumentException("CoinMarketCap symbols do not match the currency pair");
    }
    return new CoinMarketCapMapping(providerBaseSymbol, matcher.group(2), providerQuoteSymbol, matcher.group(4),
        inverted);
  }

  private String createChartUrl(CoinMarketCapMapping mapping, String interval, LocalDate from, LocalDate to) {
    return createChartUrl(mapping, interval, from.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
        to.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
  }

  private String createChartUrl(CoinMarketCapMapping mapping, String interval, long timeStart, long timeEnd) {
    return BASE_URL + "?id=" + mapping.providerBaseId() + "&interval=" + interval + "&convertId="
        + mapping.providerQuoteId() + "&timeStart=" + timeStart + "&timeEnd=" + timeEnd;
  }

  private record CoinMarketCapMapping(String providerBaseSymbol, String providerBaseId, String providerQuoteSymbol,
      String providerQuoteId, boolean inverted) {
  }

  private record TimedPrice(Instant instant, double price) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CoinMarketCapResponse {
    public CoinMarketCapData data;
    public CoinMarketCapStatus status;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CoinMarketCapData {
    public List<CoinMarketCapPoint> points;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CoinMarketCapPoint {
    public String s;
    public List<BigDecimal> v;
    public Map<String, List<BigDecimal>> c;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class CoinMarketCapStatus {
    public String error_code;
    public String error_message;
  }
}
