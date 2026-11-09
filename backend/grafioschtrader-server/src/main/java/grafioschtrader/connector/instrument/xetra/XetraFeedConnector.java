package grafioschtrader.connector.instrument.xetra;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.common.DateHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * No regex check of the URL extension is performed. This is usually the ISIN, but a URL such as "ARIVA:US631101102" is
 * also possible. The connector has not yet been checked for functioning URL extensions. The check of the connector with
 * the URL extension provides the expected response and is therefore used.
 *
 * Daily prices of instruments listed on Xetra or Frankfurt come from the date based price history endpoint, which
 * reports how many rows belong to the period. The TradingView endpoint remains for series of other suppliers such as
 * ARIVA, which the price history does not know. It delivers at most 10 years per request, so a longer period is divided
 * into several chunks. It can also answer "ok" with only a part of the period, depending on the exact time stamps.
 */
@Component
public class XetraFeedConnector extends BaseFeedConnector {

  public static final String STOCK_EX_MIC_XETRA = "XETR";
  public static final String STOCK_EX_MIC_FRANKFURT = "XFRA";

  private static final String DOMAIN_VERSION = "https://api.boerse-frankfurt.de/v1/";
  private static final String DOMAIN_INTRADAY = "https://api.live.deutsche-boerse.com/v1/data/price_information/single";
  private static final String DOMAIN_PRICE_HISTORY = "https://mobile-api.live.deutsche-boerse.com/v1/data/"
      + "price_history";
  private static final int PRICE_HISTORY_PAGE_SIZE = 2000;
  private static final String DAY_RESOLUTION = "1D";
  /** Status of a history answer without bars. */
  private static final String NO_DATA = "no_data";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public XetraFeedConnector() {
    super(supportedFeed, "xetra", "Xetra", null, EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.FIXED_INCOME,
        AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND, AssetclassCategory.REAL_ESTATE_FUND,
        AssetclassCategory.ISSUER_RISK_PRODUCT, AssetclassCategory.CFD_DERIVATIVE);
    parseGeoRestrictions("XETR XFRA");
  }

  /** History and intraday of Deutsche Börse are keyed by the ISIN; the MIC comes from the stock exchange. */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    return baseData.isin() == null ? Optional.empty() : Optional.of(baseData.isin());
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return buildIntradayApiUrl(security);
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final HttpResponse<String> response = FeedConnectorHelper
        .getByHttpClient(getSecurityIntradayDownloadLink(security));
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Failed to fetch intraday data from Xetra connector. HTTP status: " + response.statusCode());
    }
    final PriceInformation priceInfo = objectMapper.readValue(response.body(), PriceInformation.class);
    if (priceInfo.lastPrice != null) {
      security.setSLast(priceInfo.lastPrice);
    }
    if (priceInfo.closingPricePrevTradingDay != null) {
      security.setSPrevClose(priceInfo.closingPricePrevTradingDay);
    }
    if (priceInfo.dayHigh != null) {
      security.setSHigh(priceInfo.dayHigh);
    }
    if (priceInfo.dayLow != null) {
      security.setSLow(priceInfo.dayLow);
    }
    if (priceInfo.turnoverInPieces != null) {
      security.setSVolume(priceInfo.turnoverInPieces);
    }
    if (priceInfo.timestampLastPrice != null) {
      security.setSTimestamp(parseIso8601Timestamp(priceInfo.timestampLastPrice));
    }
    if (priceInfo.changeToPrevDayInPercent != null) {
      security.setSChangePercentage(DataBusinessHelper.roundPercentage(priceInfo.changeToPrevDayInPercent));
    }
  }

  /**
   * Builds the intraday API URL for the Deutsche Boerse live price endpoint.
   *
   * @param security the security to get intraday prices for
   * @return the fully constructed URL with ISIN and MIC parameters
   */
  private String buildIntradayApiUrl(final Security security) {
    String isin = extractIsin(security.getUrlIntraExtend());
    String mic = security.getStockexchange().getMic();
    return DOMAIN_INTRADAY + "?isin=" + URLEncoder.encode(isin, StandardCharsets.UTF_8) + "&mic="
        + URLEncoder.encode(mic, StandardCharsets.UTF_8);
  }

  /**
   * Extracts the ISIN from the URL extension. Handles both plain ISIN format and "PREFIX:ISIN" format.
   *
   * @param urlExtend the URL extension (e.g., "DE0007164600" or "ARIVA:DE0007164600")
   * @return the extracted ISIN
   */
  private String extractIsin(String urlExtend) {
    if (urlExtend.contains(":")) {
      return urlExtend.substring(urlExtend.indexOf(':') + 1);
    }
    return urlExtend;
  }

  /**
   * Parses an ISO 8601 timestamp string to a Date object.
   *
   * @param timestamp the ISO 8601 formatted timestamp (e.g., "2024-01-15T14:30:00+01:00")
   * @return the parsed Date object
   */
  private LocalDateTime parseIso8601Timestamp(String timestamp) {
    OffsetDateTime odt = OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    return odt.toLocalDateTime();
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate toDate = LocalDate.now();
    LocalDate fromDate = toDate.minusDays(7);
    return hasPriceHistory(security.getUrlHistoryExtend()) ? getPriceHistoryLink(security, fromDate, toDate, 0)
        : getSecurityDownloadLink(security, fromDate, toDate, security.getUrlHistoryExtend(), DAY_RESOLUTION, null);
  }

  /**
   * Tells whether the URL extension denotes a listing of Deutsche Börse, which the price history endpoint serves.
   *
   * @param urlExtend the URL extension: an ISIN, or "SOURCE:ISIN" such as "XFRA:DE0008492356" or "ARIVA:US6311011026"
   * @return true for a plain ISIN and for the sources XETR and XFRA
   */
  private boolean hasPriceHistory(String urlExtend) {
    String source = getUrlExtendSource(urlExtend);
    return source == null || STOCK_EX_MIC_XETRA.equals(source) || STOCK_EX_MIC_FRANKFURT.equals(source);
  }

  /** Returns the part before the colon of the URL extension, or null for a plain ISIN. */
  private String getUrlExtendSource(String urlExtend) {
    int colon = urlExtend.indexOf(':');
    return colon < 0 ? null : urlExtend.substring(0, colon);
  }

  /**
   * Builds the link of one page of the price history. The dates are inclusive. Prices are adjusted for splits, as those
   * of the TradingView endpoint are, but not for payouts or subscription rights.
   */
  private String getPriceHistoryLink(final Security security, LocalDate from, LocalDate to, int offset) {
    String urlExtend = security.getUrlHistoryExtend();
    String source = getUrlExtendSource(urlExtend);
    String mic = source == null ? security.getStockexchange().getMic() : source;
    return DOMAIN_PRICE_HISTORY + "?isin=" + URLEncoder.encode(extractIsin(urlExtend), StandardCharsets.UTF_8) + "&mic="
        + URLEncoder.encode(mic, StandardCharsets.UTF_8) + "&minDate=" + from + "&maxDate=" + to + "&limit="
        + PRICE_HISTORY_PAGE_SIZE + "&offset=" + offset + "&cleanSplit=true&cleanPayout=false&cleanSubscription=false";
  }

  /**
   * Builds the link of the TradingView endpoint. Its daily bars lie at 00:00 or 01:00 UTC. {@code to} is the start of
   * the following day in the time zone of the system, so that the last day is included. Depending on that time zone the
   * answer also contains the bar of the following day; callers therefore filter the bars to the requested days.
   */
  private String getSecurityDownloadLink(final Security security, LocalDate from, LocalDate to, String urlExtend,
      String resolution, Integer countback) {
    return getSecurityDownloadLink(security, from, to, urlExtend, resolution, countback, 0);
  }

  /**
   * @param toShiftSeconds seconds added to the {@code to} time stamp; a retry after a spurious {@code no_data} answer
   *                       shifts it by one second
   */
  private String getSecurityDownloadLink(final Security security, LocalDate from, LocalDate to, String urlExtend,
      String resolution, Integer countback, int toShiftSeconds) {
    String prefix = DOMAIN_VERSION + "tradingview/history?symbol="
        + (urlExtend.contains(":") ? urlExtend : security.getStockexchange().getMic() + ":" + urlExtend);
    return prefix + "&resolution=" + resolution + "&from=" + DateHelper.LocalDateToEpocheSeconds(from) + "&to="
        + (DateHelper.LocalDateToEpocheSeconds(to.plusDays(1)) + toShiftSeconds)
        + (countback == null ? "" : "&countback=" + countback);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return hasPriceHistory(security.getUrlHistoryExtend()) ? loadPriceHistory(security, from, to)
        : loadTradingViewHistory(security, from, to);
  }

  /**
   * Loads the daily history page by page, ordered by date.
   *
   * @return the quotes of the period, empty when the period has none
   * @throws IllegalStateException if the pages do not add up to the number of rows the API reports, so that an
   *                               incomplete history is never stored as if it were complete
   */
  private List<Historyquote> loadPriceHistory(final Security security, LocalDate from, LocalDate to) throws Exception {
    Map<LocalDate, Historyquote> quotesByDate = new TreeMap<>();
    int offset = 0;
    PriceHistory page;
    do {
      page = fetchPriceHistoryPage(getPriceHistoryLink(security, from, to, offset));
      for (PriceBar bar : page.data) {
        quotesByDate.put(bar.date, bar.toHistoryquote());
      }
      offset += page.data.length;
    } while (page.data.length > 0 && offset < page.totalCount);
    if (quotesByDate.size() != page.totalCount) {
      throw new IllegalStateException("Xetra price history of " + security.getUrlHistoryExtend() + " from " + from
          + " to " + to + " is incomplete: " + quotesByDate.size() + " of " + page.totalCount + " rows");
    }
    return new ArrayList<>(quotesByDate.values());
  }

  private PriceHistory fetchPriceHistoryPage(String url) throws Exception {
    log.debug("Fetching price history using URL: {}", url);
    final HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url);
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Failed to fetch price history from Xetra connector. HTTP status: " + response.statusCode());
    }
    return objectMapper.readValue(response.body(), PriceHistory.class);
  }

  /** Loads the daily history in chunks of up to ten years and keeps the days from {@code from} to {@code to}. */
  private List<Historyquote> loadTradingViewHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate currentFrom = from;
    while (!currentFrom.isAfter(to)) {
      LocalDate chunkEndDate = currentFrom.plusYears(10).isAfter(to) ? to : currentFrom.plusYears(10);
      try {
        Quotes quotes = fetchChunk(security, currentFrom, chunkEndDate);
        if (quotes != null) {
          addQuotes(historyquotes, quotes, currentFrom, chunkEndDate);
        }
      } catch (Exception e) {
        log.error("Error fetching data for period: {} to {}", currentFrom, chunkEndDate, e);
        throw e;
      }
      currentFrom = chunkEndDate.plusDays(1);
    }
    // Fetching in chunks does not guarantee the order
    historyquotes.sort((hq1, hq2) -> hq1.getDate().compareTo(hq2.getDate()));
    return historyquotes;
  }

  /**
   * Requests one chunk. In the TradingView UDF protocol {@code no_data} means that no bars exist in the period, which
   * is no error: it happens when the active from date precedes the first trading day on this exchange (e.g. a US
   * stock listed on Xetra years later). The API also answers {@code no_data} now and then for a period with data,
   * depending on the exact time stamps - such an answer is often not repeated for a {@code to} one second later. A
   * chunk without data is therefore requested once more with that shift before it is skipped.
   *
   * @return the bars of the chunk, or null when there are none
   */
  private Quotes fetchChunk(Security security, LocalDate chunkFrom, LocalDate chunkTo) throws Exception {
    Quotes quotes = null;
    for (int toShiftSeconds = 0; toShiftSeconds <= 1; toShiftSeconds++) {
      String url = getSecurityDownloadLink(security, chunkFrom, chunkTo, security.getUrlHistoryExtend(), DAY_RESOLUTION,
          null, toShiftSeconds);
      log.debug("Fetching data for period: {} to {} using URL: {}", chunkFrom, chunkTo, url);
      final HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url);
      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Failed to fetch historical data from Xetra connector. HTTP status: " + response.statusCode());
      }
      quotes = objectMapper.readValue(response.body(), Quotes.class);
      if (!NO_DATA.equals(quotes.s)) {
        break;
      }
    }
    if ("ok".equals(quotes.s)) {
      return quotes;
    } else if (NO_DATA.equals(quotes.s)) {
      log.debug("No data for period: {} to {} (symbol not traded in this range)", chunkFrom, chunkTo);
      return null;
    }
    log.error("Failed to fetch data for period: {} to {}. Status: {}", chunkFrom, chunkTo, quotes.s);
    throw new RuntimeException("Failed to fetch historical data from Xetra connector. Status: " + quotes.s);
  }

  /** Adds the bars that fall on the days of this chunk, so that a day cannot be added by two neighbouring chunks. */
  private void addQuotes(List<Historyquote> historyquotes, Quotes quotes, LocalDate from, LocalDate to) {
    for (int i = 0; i < quotes.t.length; i++) {
      LocalDate quoteDate = Instant.ofEpochSecond(quotes.t[i]).atZone(ZoneId.systemDefault()).toLocalDate();
      if (!quoteDate.isBefore(from) && !quoteDate.isAfter(to)) {
        final Historyquote historyquote = new Historyquote();
        historyquote.setDate(quoteDate);
        historyquote.setClose(quotes.c[i]);
        historyquote.setOpen(quotes.o[i]);
        historyquote.setHigh(quotes.h[i]);
        historyquote.setLow(quotes.l[i]);
        historyquote.setVolume(quotes.v[i]);
        historyquotes.add(historyquote);
      } else {
        log.debug("Skipping quote outside requested range: {} (requested: {} to {})", quoteDate, from, to);
      }
    }
  }

  /**
   * DTO for deserializing historical quote data from the TradingView API.
   */
  private static class Quotes {
    public String s;
    public long[] t;
    public double[] c;
    public double[] o;
    public double[] h;
    public double[] l;
    // Volume is a nullable boxed array: the API returns null entries (and fractional turnover values)
    // for instruments/days without traded pieces, which a primitive long[] cannot represent.
    public Long[] v;
  }

  /** One page of the price history API. */
  private static class PriceHistory {
    public PriceBar[] data;
    /** Number of rows of the whole period, not of this page. */
    public int totalCount;
  }

  /** One day of the price history API. Open, high, low and the traded pieces can be null, for an index always. */
  private static class PriceBar {
    public LocalDate date;
    public double close;
    public Double open;
    public Double high;
    public Double low;
    public Long turnoverPieces;

    Historyquote toHistoryquote() {
      final Historyquote historyquote = new Historyquote();
      historyquote.setDate(date);
      historyquote.setClose(close);
      historyquote.setOpen(open);
      historyquote.setHigh(high);
      historyquote.setLow(low);
      historyquote.setVolume(turnoverPieces);
      return historyquote;
    }
  }

  /**
   * DTO for deserializing intraday price data from the Deutsche Boerse live price API.
   */
  private static class PriceInformation {
    public Double changeToPrevDayInPercent;
    public Double closingPricePrevTradingDay;
    public Double dayHigh;
    public Double dayLow;
    @SuppressWarnings("unused")
    public String isin;
    public Double lastPrice;
    @SuppressWarnings("unused")
    public String mic;
    public String timestampLastPrice;
    public Long turnoverInPieces;
  }

}
