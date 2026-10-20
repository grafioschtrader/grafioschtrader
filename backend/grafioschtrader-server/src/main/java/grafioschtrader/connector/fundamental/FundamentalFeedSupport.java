package grafioschtrader.connector.fundamental;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import grafioschtrader.entities.Security;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP access, symbol rules and JSON reading shared by the fundamental data sources.
 */
public final class FundamentalFeedSupport {

  public static final ObjectMapper MAPPER = JsonMapper.builder().build();

  /** Request timeout of every fundamental request. */
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  /** Longest wait a 429 answer may ask for before the one repetition. */
  static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL).build();

  /**
   * Exchange suffix of a Yahoo symbol per MIC, the table measured by the fundamental source probe. US exchanges have no
   * suffix; a MIC missing here yields no derived symbol.
   */
  static final Map<String, String> YAHOO_SUFFIX = Map.ofEntries(Map.entry("XNAS", ""), Map.entry("XNYS", ""),
      Map.entry("XASE", ""), Map.entry("ARCX", ""), Map.entry("BATS", ""), Map.entry("XSWX", ".SW"),
      Map.entry("XETR", ".DE"), Map.entry("XFRA", ".F"), Map.entry("XMUN", ".MU"), Map.entry("XPAR", ".PA"),
      Map.entry("XLON", ".L"), Map.entry("XVIE", ".VI"), Map.entry("XMIL", ".MI"), Map.entry("XMAD", ".MC"),
      Map.entry("XTKS", ".T"), Map.entry("XTSE", ".TO"), Map.entry("XTSX", ".V"), Map.entry("XWAR", ".WA"),
      Map.entry("XASX", ".AX"), Map.entry("XSTO", ".ST"), Map.entry("XNZE", ".NZ"), Map.entry("XKRX", ".KS"),
      Map.entry("XOSL", ".OL"), Map.entry("XCSE", ".CO"), Map.entry("XHEL", ".HE"), Map.entry("XAMS", ".AS"),
      Map.entry("XBRU", ".BR"), Map.entry("XLIS", ".LS"), Map.entry("BIVA", ".MX"), Map.entry("XMEX", ".MX"));

  /** MICs whose tickers are US tickers; the free Finnhub plan answers for these only. */
  static final List<String> US_MICS = List.of("XNAS", "XNYS", "XASE", "ARCX", "BATS", "OTXB");

  /**
   * Quote currency codes Yahoo uses for prices in a hundredth of a currency, with the currency of the whole unit.
   * Yahoo's fundamental fields of such a listing are nevertheless in the whole unit: the production run of 2026-10-20
   * showed AstraZeneca with a trailing EPS of 4.83 GBP next to a price of 123.50 GBP and a P/E of 25.7, and the market
   * capitalisations of all London listings in pounds. Only the code is therefore replaced, the value never scaled.
   */
  private static final Map<String, String> MINOR_UNIT_CURRENCY = Map.of("GBp", "GBP", "GBX", "GBP", "ZAc", "ZAR", "ILA",
      "ILS");

  private FundamentalFeedSupport() {
  }

  /** Raw answer of a request. */
  public record HttpAnswer(int status, String body) {
  }

  /**
   * Sends a GET request with the request timeout. A 429 answer waits for its {@code Retry-After}, at most 60 seconds,
   * and is repeated once; a second 429 is returned as it is.
   *
   * @param url     the URL, which may carry a key
   * @param headers additional request headers
   * @return the status and body of the answer
   * @throws IOException          when no answer arrives
   * @throws InterruptedException when the waiting thread is interrupted
   */
  public static HttpAnswer get(String url, Map<String, String> headers) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET();
    headers.forEach(builder::header);
    HttpRequest request = builder.build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 429) {
      Thread.sleep(retryAfter(response));
      response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
    return new HttpAnswer(response.statusCode(), response.body());
  }

  private static Duration retryAfter(HttpResponse<String> response) {
    String header = response.headers().firstValue("Retry-After").orElse(null);
    long seconds = 5;
    if (header != null) {
      try {
        seconds = Long.parseLong(header.trim());
      } catch (NumberFormatException _) {
        // An HTTP date instead of seconds; the short default is enough for the one repetition.
      }
    }
    Duration wait = Duration.ofSeconds(Math.max(1, seconds));
    return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
  }

  /** Parses a body, null when it is empty or not JSON. */
  public static JsonNode parse(String body) {
    try {
      return body == null || body.isBlank() ? null : MAPPER.readTree(body);
    } catch (RuntimeException _) {
      return null;
    }
  }

  /** A finite number at the JSON pointer, null when the field is missing, null, non-numeric or not finite. */
  public static Double number(JsonNode root, String pointer) {
    JsonNode node = root.at(pointer);
    double value;
    if (node.isNumber()) {
      value = node.asDouble();
    } else if (node.isString()) {
      try {
        value = Double.parseDouble(node.asString().trim());
      } catch (NumberFormatException _) {
        return null;
      }
    } else {
      return null;
    }
    return Double.isFinite(value) ? value : null;
  }

  /** The text at the JSON pointer, null when it is missing, null or blank. */
  public static String text(JsonNode root, String pointer) {
    JsonNode node = root.at(pointer);
    String text = node.isMissingNode() || node.isNull() ? null : node.asString(null);
    return text == null || text.isBlank() ? null : text.trim();
  }

  /** An ISO date ({@code yyyy-MM-dd} prefix) at the JSON pointer, null when there is none. */
  public static LocalDate date(JsonNode root, String pointer) {
    String text = text(root, pointer);
    if (text == null || text.length() < 10 || !text.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
      return null;
    }
    return LocalDate.parse(text.substring(0, 10));
  }

  /** A date given as epoch seconds at the JSON pointer, read in UTC; null when there is none. */
  public static LocalDate epochDate(JsonNode root, String pointer) {
    Double seconds = number(root, pointer);
    return seconds == null ? null : LocalDate.ofInstant(Instant.ofEpochSecond(seconds.longValue()), ZoneOffset.UTC);
  }

  /** The currency of the whole unit for a minor unit code such as {@code GBp}, otherwise the code itself. */
  static String majorCurrency(String currency) {
    return currency == null ? null : MINOR_UNIT_CURRENCY.getOrDefault(currency, currency);
  }

  public static boolean isUsListing(Security security) {
    // An exchange without MIC exists (private papers); List.of(...).contains(null) would throw.
    String mic = security.getStockexchange() == null ? null : security.getStockexchange().getMic();
    return mic != null && US_MICS.contains(mic);
  }

  /** Ticker plus the Yahoo exchange suffix of the MIC, null when the ticker or the suffix is unknown. */
  public static String derivedYahooSymbol(Security security) {
    String mic = security.getStockexchange() == null ? null : security.getStockexchange().getMic();
    String suffix = mic == null ? null : YAHOO_SUFFIX.get(mic);
    String ticker = security.getTickerSymbol();
    return ticker != null && !ticker.isBlank() && suffix != null ? ticker.trim().toUpperCase(Locale.ROOT) + suffix
        : null;
  }

  static boolean notBlank(String text) {
    return text != null && !text.isBlank();
  }
}
