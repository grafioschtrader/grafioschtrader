package grafioschtrader.connector.instrument.onvista;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.BaseConstants;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.securitysearch.OnvistaSecuritySearch;
import grafioschtrader.dto.CurrencypairLookupData;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/*-
 * Supports history quotes for securities. Unfortunately, quotes are only
 * exported with an accuracy of two decimal places. Currency is disabled because
 * for currency it is not useful.
 *
 * onvista limits bursts of requests (HTTP 429 or the body {"ERROR": "TOO_MANY_REQUESTS"});
 * a limited request is repeated after a wait.
 *
 * Regex pattern recognition for checking the URL extension is not implemented.
 * However, the URL extension is checked with a connection to the connector.
 */
@Component
public class OnvistaFeedConnector extends BaseFeedConnector {

  private static final Logger log = LoggerFactory.getLogger(OnvistaFeedConnector.class);

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final int MAX_YEAR_DOWNLOAD = 5;
  /** Parameters the connector sets itself and therefore removes from a stored URL extension, in lower case. */
  private static final Set<String> PERIOD_PARAMETERS = Set.of("range", "startdate");
  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  private static final String ERROR_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
  /** Repetitions of a request onvista answered with its rate limit; the waits add up to 5 + 10 + 20 + 40 seconds. */
  private static final int MAX_RATE_LIMIT_RETRIES = 4;
  private static final Duration FIRST_RATE_LIMIT_WAIT = Duration.ofSeconds(5);
  private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(60);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL).build();
  private static MonthToString[] monthToStrings;
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  static {
    supportedFeed = new HashMap<>();
    // supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] {
    // FeedIdentifier.SECURITY_URL,
    // FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    monthToStrings = new MonthToString[] { new MonthToString(1, "M1"), new MonthToString(3, "M3"),
        new MonthToString(6, "M6"), new MonthToString(12, "Y1"), new MonthToString(36, "Y3"),
        new MonthToString(60, "Y5") };
  }

  public OnvistaFeedConnector() {
    super(supportedFeed, "onvista", "onvista", null, EnumSet.of(UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR,
        AssetclassCategory.NON_INVESTABLE_INDICES, AssetclassCategory.EQUITIES, AssetclassCategory.FIXED_INCOME,
        AssetclassCategory.ETF, AssetclassCategory.ISSUER_RISK_PRODUCT);
  }

  /**
   * The URL extension {@code {TYPE}/{id}/eod_history?idNotation={notation}} is the instrument id of onvista master data
   * ({@code OnvistaSecuritySearch}); it cannot be derived from ISIN or ticker, since the notation identifies the venue.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    return OnvistaSecuritySearch.PROVIDER_ID.equals(baseData.providerId()) && baseData.providerInstrumentId() != null
        ? Optional.of(baseData.providerInstrumentId())
        : Optional.empty();
  }

  /**
   * onvista quotes a currency pair as {@code CURRENCY/{from}{to}} with the notation of the FactSet synthetic rate. Both
   * come from the onvista hit of the currency pair search; the notation cannot be derived from the currencies.
   */
  @Override
  public Optional<String> deriveCurrencyUrlExtension(CurrencypairLookupData data, FeedSupport feedSupport) {
    return data.hitOf(OnvistaSecuritySearch.PROVIDER_ID).filter(h -> h.instrumentId() != null && h.notation() != null)
        .map(h -> OnvistaSecuritySearch.TYPE_CURRENCY + "/" + h.instrumentId() + "/eod_history?idNotation="
            + h.notation());
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws Exception {
    return this.getHistoricalData(currencyPair.getUrlHistoryExtend(), from, to);
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return getDownloadLink(currencypair);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getDownloadLink(security);
  }

  private <S extends Securitycurrency<S>> String getDownloadLink(S securitycurrency) {
    LocalDate fromDate = LocalDate.now().minusYears(MAX_YEAR_DOWNLOAD);
    return this.getSecurityHistoricalDownloadLink(securitycurrency.getUrlHistoryExtend(), fromDate,
        monthToStrings[monthToStrings.length - 1].traslate);
  }

  private String getSecurityHistoricalDownloadLink(final String productUrlPart, final LocalDate startRange,
      String timeSpan) {
    return "https://api.onvista.de/api/v1/instruments/" + withoutPeriodParameters(productUrlPart) + "&startDate="
        + DateTimeFormatter.ofPattern(BaseConstants.STANDARD_DATE_FORMAT).format(startRange) + "&range=" + timeSpan;
  }

  /**
   * Removes the parameters {@code range} and {@code startDate} from a stored URL extension, because the connector sets
   * both itself. onvista honours the first occurrence of a repeated parameter: an extension ending in
   * {@code &range=M1} made every request return a single month instead of five years, so a full load of a long history
   * became some two hundred requests and ended in HTTP 429.
   *
   * @param productUrlPart the URL extension, e.g. {@code STOCK/82840/eod_history?idNotation=161766&range=M1}
   * @return the extension without these parameters, e.g. {@code STOCK/82840/eod_history?idNotation=161766}
   */
  static String withoutPeriodParameters(String productUrlPart) {
    int queryStart = productUrlPart.indexOf('?');
    if (queryStart < 0) {
      return productUrlPart;
    }
    String query = Arrays.stream(productUrlPart.substring(queryStart + 1).split("&"))
        .filter(p -> !p.isEmpty() && !PERIOD_PARAMETERS.contains(p.split("=", 2)[0].toLowerCase(Locale.ROOT)))
        .collect(Collectors.joining("&"));
    return productUrlPart.substring(0, queryStart + 1) + query;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return this.getHistoricalData(security.getUrlHistoryExtend(), from, to);
  }

  /**
   * Reads the history in pieces of at most five years, each piece starting the day after the last quote of the previous
   * one. A single failing piece fails the whole load, so each request repeats a rate-limit answer, see
   * {@link #readQuotes(String)}.
   */
  private List<Historyquote> getHistoricalData(String productUrlPart, final LocalDate from, final LocalDate to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate lFrom = from;
    LocalDate lTo = to;
    LocalDate calcLFrom;
    do {
      long durationMonths = ChronoUnit.MONTHS.between(lFrom, lTo);
      Optional<MonthToString> optionalMts = Arrays.stream(monthToStrings)
          .filter(months -> durationMonths < months.month).findFirst();
      MonthToString mts = optionalMts.isPresent() ? optionalMts.get() : monthToStrings[monthToStrings.length - 1];

      readHistoricalData(productUrlPart, lFrom, mts.traslate, to, historyquotes);
      calcLFrom = lFrom.plusMonths(mts.month);
      if (!historyquotes.isEmpty()) {
        lFrom = historyquotes.get(historyquotes.size() - 1).getDate().plusDays(1);
      } else {
        lFrom = calcLFrom;
      }
    } while (!calcLFrom.isAfter(lTo));

    return historyquotes;
  }

  private void readHistoricalData(final String productUrlPart, final LocalDate startRange, final String timeSpan,
      final LocalDate to, List<Historyquote> historyquotes) throws Exception {

    String url = getSecurityHistoricalDownloadLink(productUrlPart, startRange, timeSpan);
    final Quotes quotes = readQuotes(url);
    for (int i = 0; i < quotes.datetimeLast.length; i++) {
      LocalDate date = Instant.ofEpochSecond(quotes.datetimeLast[i]).atZone(ZoneId.systemDefault()).toLocalDate();
      if (!to.isBefore(date)) {
        final Historyquote historyquote = new Historyquote();
        historyquotes.add(historyquote);
        historyquote.setDate(date);
        historyquote.setClose(quotes.last[i]);
        historyquote.setOpen(quotes.first[i]);
        historyquote.setHigh(quotes.high[i]);
        historyquote.setLow(quotes.low[i]);
        historyquote.setVolume(quotes.volume[i]);
      }
    }
  }

  /**
   * Requests one piece of the history. onvista signals its rate limit either with HTTP 429 or with HTTP 200 and the
   * body {@code {"ERROR": "TOO_MANY_REQUESTS"}}; both are repeated after a wait that doubles each time, or after the
   * {@code Retry-After} of the answer, at most {@link #MAX_RATE_LIMIT_RETRIES} times.
   *
   * @param url the complete request URL
   * @return the quotes of the piece, never with a missing date array
   * @throws IOException          when onvista still limits after the last repetition, answers with another error or
   *                              without quotes
   * @throws InterruptedException when the waiting thread is interrupted
   */
  private Quotes readQuotes(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT).GET().build();
    Duration wait = FIRST_RATE_LIMIT_WAIT;
    for (int attempt = 0;; attempt++) {
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      Quotes quotes = response.statusCode() == HttpURLConnection.HTTP_OK
          ? objectMapper.readValue(response.body(), Quotes.class)
          : null;
      boolean rateLimited = response.statusCode() == HTTP_TOO_MANY_REQUESTS
          || quotes != null && ERROR_TOO_MANY_REQUESTS.equals(quotes.error);
      if (!rateLimited) {
        return checkedQuotes(url, response.statusCode(), quotes);
      }
      if (attempt >= MAX_RATE_LIMIT_RETRIES) {
        throw new IOException("onvista still answers with too many requests after " + MAX_RATE_LIMIT_RETRIES
            + " repetitions for URL: " + url);
      }
      Duration retryAfter = retryAfter(response).orElse(wait);
      log.info("onvista rate limit, request {} repeated in {} s: {}", attempt + 1, retryAfter.toSeconds(), url);
      Thread.sleep(retryAfter);
      wait = wait.multipliedBy(2);
    }
  }

  private static Quotes checkedQuotes(String url, int statusCode, Quotes quotes) throws IOException {
    if (statusCode != HttpURLConnection.HTTP_OK) {
      throw new IOException("onvista answered with HTTP " + statusCode + " for URL: " + url);
    }
    if (quotes.error != null) {
      throw new IOException("onvista answered with error " + quotes.error + " for URL: " + url);
    }
    if (quotes.datetimeLast == null) {
      throw new IOException("onvista answered without quotes for URL: " + url);
    }
    return quotes;
  }

  /** The {@code Retry-After} seconds of an answer, capped at {@link #MAX_RATE_LIMIT_WAIT}. */
  private static Optional<Duration> retryAfter(HttpResponse<String> response) {
    try {
      return response.headers().firstValue("Retry-After").map(v -> Duration.ofSeconds(Long.parseLong(v.trim())))
          .filter(d -> !d.isNegative()).map(d -> d.compareTo(MAX_RATE_LIMIT_WAIT) > 0 ? MAX_RATE_LIMIT_WAIT : d);
    } catch (NumberFormatException _) {
      // An HTTP date instead of seconds; the doubling wait applies.
      return Optional.empty();
    }
  }

  private static class Quotes {
    @JsonProperty("ERROR")
    public String error;
    public long[] datetimeLast;
    public double[] first;
    public double[] last;
    public double[] high;
    public double[] low;
    public long[] volume;
  }

}

class MonthToString {
  public long month;
  public String traslate;

  public MonthToString(long month, String traslate) {
    this.month = month;
    this.traslate = traslate;
  }

}
