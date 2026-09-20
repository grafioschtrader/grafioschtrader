package grafioschtrader.connector.instrument.eulerpool;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eulerpool REST connector. Identifier in the URL extension is an ISIN, ticker, WKN, CUSIP or SEDOL; ISIN is preferred
 * because ticker and ISIN of one company can be two series. Equities only: {@code /charting/ohlcv} answers 404 for ETFs
 * and indices. History is split-adjusted. Last price comes from the multi-exchange quote, never from the ticker-only
 * last-quote endpoint (short tickers collide).
 */
@Component
public class EulerpoolFeedConnector extends BaseFeedApiKeyConnector {

  private static final String DOMAIN = "https://api.eulerpool.com/api/1/";
  private static final String TOKEN_PARAM_NAME = "token";
  private static final String URL_EXTEND_PATTERN = "^[A-Za-z0-9.-]{1,24}$";
  private static final int INTRADAY_DELAY_SECONDS = 900;

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  private final Bucket bucket;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT_URL });
    supportedFeed.put(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND_URL });
  }

  public EulerpoolFeedConnector() {
    super(supportedFeed, "eulerpool", "Eulerpool", URL_EXTEND_PATTERN, EnumSet.of(UrlCheck.HISTORY, UrlCheck.INTRADAY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES);
    Bandwidth limit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofSeconds(1)).build();
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  @Override
  public boolean needHistoricalGapFiller(final Security security) {
    return true;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return INTRADAY_DELAY_SECONDS;
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    return EnumSet.of(DownloadLink.DL_HISTORY_FORCE_BACKEND, DownloadLink.DL_INTRA_FORCE_BACKEND);
  }

  @Override
  public boolean isDividendSplitAdjusted() {
    return true;
  }

  @Override
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, TOKEN_PARAM_NAME);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate to = LocalDate.now();
    return historicalUrl(security.getUrlHistoryExtend(), to.minusDays(7), to);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return exchangesUrl(security.getUrlIntraExtend());
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return splitsUrl(security.getUrlSplitExtend());
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return dividendsUrl(security.getUrlDividendExtend());
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(Security security, LocalDate from, LocalDate to) throws Exception {
    Ohlcv ohlcv = OBJECT_MAPPER.readValue(httpGet(historicalUrl(security.getUrlHistoryExtend(), from, to)),
        Ohlcv.class);
    return toHistoryquotes(ohlcv, from, to);
  }

  @Override
  public void updateSecurityLastPrice(Security security) throws Exception {
    ExchangeQuotes quotes = OBJECT_MAPPER.readValue(httpGet(exchangesUrl(security.getUrlIntraExtend())),
        ExchangeQuotes.class);
    ExchangeQuote chosen = chooseQuote(quotes, security.getCurrency());
    if (chosen == null || chosen.effectivePrice() == null) {
      throw new IOException("No last price for " + hideApiKeyForError(exchangesUrl(security.getUrlIntraExtend())));
    }
    security.setSLast(chosen.effectivePrice());
    security.setSTimestamp(LocalDateTime.ofInstant(epochToInstant(chosen.epoch()), ZoneId.systemDefault()));
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception {
    SplitRead[] reads = OBJECT_MAPPER.readValue(httpGet(splitsUrl(security.getUrlSplitExtend())), SplitRead[].class);
    List<Securitysplit> splits = new ArrayList<>();
    if (reads == null) {
      return splits;
    }
    for (SplitRead read : reads) {
      LocalDate date = parseIsoDate(read.date);
      if (date == null || date.isBefore(fromDate) || date.isAfter(toDate)) {
        continue;
      }
      splits.add(new Securitysplit(security.getIdSecuritycurrency(), date, read.fromFactor, read.toFactor,
          CreateType.CONNECTOR_CREATED));
    }
    return splits;
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    DividendRead[] reads = OBJECT_MAPPER.readValue(httpGet(dividendsUrl(security.getUrlDividendExtend())),
        DividendRead[].class);
    List<Dividend> dividends = new ArrayList<>();
    if (reads == null) {
      return dividends;
    }
    for (DividendRead read : reads) {
      LocalDate exDate = parseIsoDate(read.period);
      if (exDate == null || exDate.isBefore(fromDate)) {
        continue;
      }
      dividends.add(new Dividend(security.getIdSecuritycurrency(), exDate, parseIsoDate(read.payDate), null,
          read.dividend, security.getCurrency(), CreateType.CONNECTOR_CREATED));
    }
    return dividends;
  }

  private List<Historyquote> toHistoryquotes(Ohlcv ohlcv, LocalDate from, LocalDate to) {
    List<Historyquote> quotes = new ArrayList<>();
    if (ohlcv == null || ohlcv.t == null || ohlcv.c == null) {
      return quotes;
    }
    LocalDate today = LocalDate.now();
    Map<LocalDate, Historyquote> byDate = new LinkedHashMap<>();
    int n = ohlcv.t.length;
    for (int i = 0; i < n; i++) {
      LocalDate date = Instant.ofEpochSecond(ohlcv.t[i]).atZone(ZoneId.systemDefault()).toLocalDate();
      if (date.isBefore(from) || date.isAfter(to) || date.equals(today)) {
        continue;
      }
      Historyquote hq = new Historyquote();
      hq.setDate(date);
      hq.setClose(ohlcv.c[i]);
      if (ohlcv.o != null && i < ohlcv.o.length) {
        hq.setOpen(ohlcv.o[i]);
      }
      if (ohlcv.h != null && i < ohlcv.h.length) {
        hq.setHigh(ohlcv.h[i]);
      }
      if (ohlcv.l != null && i < ohlcv.l.length) {
        hq.setLow(ohlcv.l[i]);
      }
      if (ohlcv.v != null && i < ohlcv.v.length && ohlcv.v[i] > 0) {
        hq.setVolume((long) ohlcv.v[i]);
      }
      byDate.put(date, hq);
    }
    quotes.addAll(byDate.values());
    return quotes;
  }

  private ExchangeQuote chooseQuote(ExchangeQuotes quotes, String currency) {
    if (quotes == null || quotes.exchanges == null || quotes.exchanges.isEmpty()) {
      return null;
    }
    ExchangeQuote primary = null;
    ExchangeQuote currencyMatch = null;
    for (ExchangeQuote q : quotes.exchanges) {
      if (quotes.primary != null && quotes.primary.equalsIgnoreCase(q.exchange)) {
        primary = q;
      }
      if (currency != null && currency.equalsIgnoreCase(q.currency)) {
        currencyMatch = q;
        if (primary == q) {
          return q;
        }
      }
    }
    if (currencyMatch != null) {
      return currencyMatch;
    }
    return primary != null ? primary : quotes.exchanges.get(0);
  }

  private String historicalUrl(String identifier, LocalDate from, LocalDate to) {
    long fromUnix = from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
    long toUnix = to.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond();
    return DOMAIN + "charting/ohlcv/" + encode(identifier) + "?resolution=D&from=" + fromUnix + "&to=" + toUnix
        + tokenQuery(true);
  }

  private String exchangesUrl(String identifier) {
    return DOMAIN + "market/quotes/exchanges/" + encode(identifier) + tokenQuery(false);
  }

  private String splitsUrl(String identifier) {
    return DOMAIN + "equity/splits/" + encode(identifier) + tokenQuery(false);
  }

  private String dividendsUrl(String identifier) {
    return DOMAIN + "equity/dividends/" + encode(identifier) + tokenQuery(false);
  }

  private String tokenQuery(boolean hasQuery) {
    return (hasQuery ? "&" : "?") + TOKEN_PARAM_NAME + "=" + getApiKey();
  }

  private String encode(String identifier) {
    String value = identifier == null ? "" : identifier.toUpperCase();
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }

  private String httpGet(String url) throws IOException, InterruptedException {
    waitForTokenOrGo(bucket);
    String safe = hideApiKeyForError(url);
    try {
      HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url, 30);
      if (response.statusCode() == 500) {
        waitForTokenOrGo(bucket);
        response = FeedConnectorHelper.getByHttpClient(url, 30);
      }
      if (response.statusCode() != 200) {
        throw new IOException("HTTP " + response.statusCode() + " for " + safe);
      }
      return response.body();
    } catch (IllegalArgumentException e) {
      throw new IOException("HTTP request failed for " + safe, e);
    } catch (IOException e) {
      String message = e.getMessage();
      if (message != null && getApiKey() != null && message.contains(getApiKey())) {
        throw new IOException(hideApiKeyForError(message), e);
      }
      throw e;
    }
  }

  private static LocalDate parseIsoDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
  }

  private static Instant epochToInstant(long timestamp) {
    return timestamp > 10_000_000_000L ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
  }

  static class Ohlcv {
    public long[] t;
    public double[] o;
    public double[] h;
    public double[] l;
    public double[] c;
    public double[] v;
  }

  static class ExchangeQuotes {
    public String primary;
    public List<ExchangeQuote> exchanges;
  }

  static class ExchangeQuote {
    public String exchange;
    public String currency;
    public Double bid;
    public Double ask;
    public Object timestamp;

    Double effectivePrice() {
      if (bid != null && bid > 0) {
        return bid;
      }
      return ask;
    }

    long epoch() {
      if (timestamp instanceof Number number) {
        return number.longValue();
      }
      if (timestamp instanceof String text && !text.isBlank()) {
        return Long.parseLong(text);
      }
      return 0L;
    }
  }

  static class SplitRead {
    public String date;
    public int fromFactor;
    public int toFactor;
  }

  static class DividendRead {
    public String payDate;
    public String period;
    public Double dividend;
  }
}
