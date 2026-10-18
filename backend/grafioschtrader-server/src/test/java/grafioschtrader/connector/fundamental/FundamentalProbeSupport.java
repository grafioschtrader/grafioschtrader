package grafioschtrader.connector.fundamental;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared types and helpers of the fundamental data coverage probe ({@link FundamentalSourceCoverageProbeTest}). The
 * probe asks several external sources for the fundamentals of the stocks in the real database and records which of a
 * fixed set of base values each source delivers, so that the choice of sources rests on measured coverage rather than
 * on the providers' marketing.
 */
final class FundamentalProbeSupport {

  static final ObjectMapper MAPPER = new ObjectMapper();

  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NORMAL).build();

  /**
   * Exchange suffixes used by Yahoo and, for the exchanges listed, by Finnhub. US exchanges have no suffix. A MIC that
   * is missing here produces no derived symbol, and the source reports the security as "no symbol".
   */
  static final Map<String, String> YAHOO_SUFFIX = Map.ofEntries(Map.entry("XNAS", ""), Map.entry("XNYS", ""),
      Map.entry("XASE", ""), Map.entry("ARCX", ""), Map.entry("BATS", ""), Map.entry("XSWX", ".SW"),
      Map.entry("XETR", ".DE"), Map.entry("XFRA", ".F"), Map.entry("XMUN", ".MU"), Map.entry("XPAR", ".PA"),
      Map.entry("XLON", ".L"), Map.entry("XVIE", ".VI"), Map.entry("XMIL", ".MI"), Map.entry("XMAD", ".MC"),
      Map.entry("XTKS", ".T"), Map.entry("XTSE", ".TO"), Map.entry("XTSX", ".V"), Map.entry("XWAR", ".WA"),
      Map.entry("XASX", ".AX"), Map.entry("XSTO", ".ST"), Map.entry("XNZE", ".NZ"), Map.entry("XKRX", ".KS"),
      Map.entry("XOSL", ".OL"), Map.entry("XCSE", ".CO"), Map.entry("XHEL", ".HE"), Map.entry("XAMS", ".AS"),
      Map.entry("XBRU", ".BR"), Map.entry("XLIS", ".LS"), Map.entry("BIVA", ".MX"), Map.entry("XMEX", ".MX"));

  /** MICs whose tickers are US tickers, matched against the SEC ticker list. */
  static final List<String> US_MICS = List.of("XNAS", "XNYS", "XASE", "ARCX", "BATS", "OTXB");

  private FundamentalProbeSupport() {
  }

  /**
   * The base values a later implementation would store per security. Ratios such as P/E or market capitalisation are
   * meant to be computed in GT from these and the live price; {@link #MARKET_CAP} and {@link #PE} are recorded only to
   * see which source could serve as a cross-check. {@link #NET_INCOME} also counts a net margin, from which the income
   * follows with the revenue; {@link #NET_DEBT} also counts a debt-to-equity figure where a source has nothing closer.
   */
  enum Metric {
    EPS, SHARES, BOOK_VALUE, REVENUE, NET_INCOME, NET_DEBT, MARKET_CAP, PE
  }

  /** Outcome class of one request; {@code FOUND} means at least one metric was delivered. */
  enum Status {
    FOUND, EMPTY, NOT_FOUND, NO_ACCESS, RATE_LIMITED, NO_SYMBOL, NO_KEY, ERROR
  }

  /** One stock of the probe universe, read from the real database. */
  record ProbeSecurity(int id, String name, String isin, String ticker, String mic, String currency,
      String idConnectorIntra, String urlIntraExtend, String yahooSymbolUdf) {
  }

  /**
   * Result of one source for one security.
   *
   * @param symbol      the identifier sent to the source, so that a miss can be checked by hand
   * @param httpStatus  status of the main request, 0 when none was sent
   * @param metrics     which base values were present and numeric
   * @param fieldCount  number of numeric fields in the relevant part of the answer, as a measure of richness
   * @param latestPeriod newest reporting period end the source names, empty when it does not say
   * @param note        short reason for a miss or extra findings, never containing a key
   * @param values      selected raw values with their currency, for comparing sources against each other
   * @param body        the raw answer, kept for the sample files; null for a miss
   */
  record ProbeResult(String source, ProbeSecurity security, String symbol, Status status, int httpStatus,
      Map<Metric, Boolean> metrics, int fieldCount, String latestPeriod, String note, String values, String body) {

    /** A miss without metrics. */
    static ProbeResult of(String source, ProbeSecurity security, String symbol, Status status, int httpStatus,
        String note) {
      return new ProbeResult(source, security, symbol, status, httpStatus, new EnumMap<>(Metric.class), 0, "", note,
          "", null);
    }

    /** An answer that was read; {@code FOUND} when at least one metric is present, {@code EMPTY} otherwise. */
    static ProbeResult answered(String source, ProbeSecurity security, String symbol, int httpStatus,
        Map<Metric, Boolean> metrics, int fieldCount, String latestPeriod, String body) {
      Status status = metrics.containsValue(Boolean.TRUE) ? Status.FOUND : Status.EMPTY;
      return new ProbeResult(source, security, symbol, status, httpStatus, metrics, fieldCount, latestPeriod, "", "",
          body);
    }

    ProbeResult withNote(String newNote) {
      return new ProbeResult(source, security, symbol, status, httpStatus, metrics, fieldCount, latestPeriod, newNote,
          values, body);
    }

    ProbeResult withValues(String newValues) {
      return new ProbeResult(source, security, symbol, status, httpStatus, metrics, fieldCount, latestPeriod, note,
          newValues, body);
    }
  }

  /** The value at a JSON pointer as text, empty when it is missing or null. */
  static String text(JsonNode root, String pointer) {
    JsonNode node = root.at(pointer);
    return node.isMissingNode() || node.isNull() ? "" : node.asString("");
  }

  /**
   * Newest ISO date ({@code yyyy-MM-dd} prefix) found anywhere in a string value below the node, empty when there is
   * none. Used where a source states its reporting periods without a documented field name.
   */
  static String newestDate(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    if (node.isString()) {
      String text = node.asString();
      return text.length() >= 10 && text.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}") ? text.substring(0, 10)
          : "";
    }
    String newest = "";
    for (JsonNode child : node) {
      String candidate = newestDate(child);
      if (candidate.compareTo(newest) > 0) {
        newest = candidate;
      }
    }
    return newest;
  }

  /** Common contract of the per-source probes; one instance runs on its own thread. */
  interface FundamentalSourceProbe {

    /** Short source name used in the output files. */
    String name();

    /** Pause between two requests, chosen to stay inside the source's free rate limit. */
    Duration pause();

    /**
     * Runs once before the first security. Returns null when the source is ready, otherwise the reason why it is
     * skipped (no key, no contact address, list download failed); every security is then reported with that reason.
     */
    String prepare();

    ProbeResult probe(ProbeSecurity security);
  }

  /** Raw answer of a request; {@code body} is null when the request failed before an answer arrived. */
  record HttpAnswer(int status, String body) {
  }

  /**
   * Sends a GET request. A 429 is retried twice after 5 and 10 seconds, so that a short burst over a rate limit does
   * not count as a miss of the source.
   */
  static HttpAnswer get(String url, Map<String, String> headers) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
    headers.forEach(builder::header);
    HttpRequest request = builder.build();
    try {
      for (int attempt = 1;; attempt++) {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 429 || attempt == 3) {
          return new HttpAnswer(response.statusCode(), response.body());
        }
        Thread.sleep(Duration.ofSeconds(5L * attempt));
      }
    } catch (IOException _) {
      return new HttpAnswer(0, null);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return new HttpAnswer(0, null);
    }
  }

  static JsonNode parse(String body) {
    try {
      return body == null || body.isBlank() ? null : MAPPER.readTree(body);
    } catch (RuntimeException _) {
      return null;
    }
  }

  /** Maps the HTTP status of a failed request to a status class. */
  static Status statusOf(int httpStatus) {
    return switch (httpStatus) {
    case 401, 402, 403 -> Status.NO_ACCESS;
    case 404 -> Status.NOT_FOUND;
    case 429 -> Status.RATE_LIMITED;
    default -> Status.ERROR;
    };
  }

  /**
   * True when the node at one of the JSON pointers holds a number, or a string that parses as one and is not zero
   * length. Several pointers per metric absorb the naming variants of one source.
   */
  static boolean hasNumber(JsonNode root, String... pointers) {
    for (String pointer : pointers) {
      JsonNode node = root.at(pointer);
      if (node.isNumber()) {
        return true;
      }
      if (node.isString() && isNumeric(node.asString())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNumeric(String text) {
    try {
      Double.parseDouble(text.trim());
      return !text.isBlank();
    } catch (NumberFormatException _) {
      return false;
    }
  }

  /** Counts the numeric leaves below a node, numbers in strings included. */
  static int countNumbers(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return 0;
    }
    if (node.isNumber() || node.isString() && isNumeric(node.asString())) {
      return 1;
    }
    int count = 0;
    for (JsonNode child : node) {
      count += countNumbers(child);
    }
    return count;
  }

  /**
   * Symbol in the Yahoo convention: the Yahoo connector's own extension when the security is priced by Yahoo, then
   * the symbol stored in the system-wide Yahoo symbol UDF, then ticker plus exchange suffix.
   */
  static String yahooSymbol(ProbeSecurity s) {
    if ("gt.datafeed.yahoo".equals(s.idConnectorIntra()) && notBlank(s.urlIntraExtend())) {
      return s.urlIntraExtend().trim();
    }
    if (notBlank(s.yahooSymbolUdf())) {
      return s.yahooSymbolUdf().trim();
    }
    return derivedSymbol(s);
  }

  /** Ticker plus the Yahoo exchange suffix, null when the ticker or the suffix is unknown. */
  static String derivedSymbol(ProbeSecurity s) {
    String suffix = YAHOO_SUFFIX.get(s.mic());
    return notBlank(s.ticker()) && suffix != null ? s.ticker().trim().toUpperCase(Locale.ROOT) + suffix : null;
  }

  /**
   * Company name reduced for an exact comparison across sources: ASCII, lower case, legal forms and parenthesised text
   * removed, every non-alphanumeric run collapsed to one blank.
   */
  static String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    String noLegal = ascii.replaceAll("\\(.*?\\)", " ").replaceAll(
        "\\b(ag|se|sa|nv|plc|inc|corp|corporation|ltd|limited|co|company|group|holding|holdings|the|class [a-z])\\b",
        " ");
    return noLegal.replaceAll("[^a-z0-9]+", " ").trim();
  }

  static boolean notBlank(String text) {
    return text != null && !text.isBlank();
  }
}
