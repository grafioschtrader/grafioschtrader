package grafioschtrader.connector.fundamental;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import grafioschtrader.connector.fundamental.FundamentalProbeSupport.FundamentalSourceProbe;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Metric;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeResult;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeSecurity;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Status;
import tools.jackson.databind.JsonNode;

/**
 * Probes Eulerpool's {@code GET /api/1/equity/metrics/{identifier}}, documented with the groups {@code valuation},
 * {@code profitability}, {@code perShare}, {@code leverage}, {@code growth} and {@code other}. The identifier is the
 * ISIN, as in {@code EulerpoolFeedConnector}; the key travels as the {@code token} query parameter.
 *
 * <p>
 * The per-share figures of {@code metrics} follow the newest annual period of its {@code historical} array, which is
 * recorded as the latest period. Every security is also asked for {@code financials-ltm}, and the newest date in that
 * answer goes into the note, to see whether trailing twelve months are more current. For the first
 * {@link #SIDE_PROBES} securities the note also carries the HTTP status and field count of {@code key-figures}, to see
 * whether the free plan serves it.
 * </p>
 */
final class EulerpoolProbe implements FundamentalSourceProbe {

  private static final String DOMAIN = "https://api.eulerpool.com/api/1/equity/";
  private static final int SIDE_PROBES = 10;

  private final String apiKey;
  private int probed;

  EulerpoolProbe(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public String name() {
    return "eulerpool";
  }

  @Override
  public Duration pause() {
    // No per-minute limit is documented; this keeps a full run far below the monthly free quota.
    return Duration.ofMillis(300);
  }

  @Override
  public String prepare() {
    return FundamentalProbeSupport.notBlank(apiKey) ? null : "no API key";
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    String identifier = FundamentalProbeSupport.notBlank(s.isin()) ? s.isin() : s.ticker();
    if (!FundamentalProbeSupport.notBlank(identifier)) {
      return ProbeResult.of(name(), s, "", Status.NO_SYMBOL, 0, "neither ISIN nor ticker");
    }
    HttpAnswer answer = request("metrics", identifier);
    HttpAnswer ltm = request("financials-ltm", identifier);
    JsonNode ltmRoot = FundamentalProbeSupport.parse(ltm.body());
    String note = "ltm=" + ltm.status() + " ltm_newest=" + FundamentalProbeSupport.newestDate(ltmRoot)
        + (probed++ < SIDE_PROBES ? " " + keyFiguresProbe(identifier) : "");
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    if (answer.status() != 200 || root == null) {
      return ProbeResult.of(name(), s, identifier, FundamentalProbeSupport.statusOf(answer.status()),
          answer.status(), note);
    }
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    metrics.put(Metric.EPS, FundamentalProbeSupport.hasNumber(root, "/perShare/eps"));
    metrics.put(Metric.SHARES, FundamentalProbeSupport.hasNumber(root, "/other/shares"));
    metrics.put(Metric.BOOK_VALUE, FundamentalProbeSupport.hasNumber(root, "/perShare/bps"));
    metrics.put(Metric.REVENUE, FundamentalProbeSupport.hasNumber(root, "/perShare/sps"));
    metrics.put(Metric.NET_INCOME, false);
    metrics.put(Metric.NET_DEBT, FundamentalProbeSupport.hasNumber(root, "/leverage/netDebt"));
    metrics.put(Metric.MARKET_CAP, FundamentalProbeSupport.hasNumber(root, "/valuation/marketCap"));
    metrics.put(Metric.PE, FundamentalProbeSupport.hasNumber(root, "/valuation/pe"));
    // The newest annual period of the history shows how current the per-share figures are.
    String latest = FundamentalProbeSupport.newestDate(root.path("historical"));
    String sample = "{\"metrics\":" + answer.body() + ",\"financialsLtm\":" + (ltm.body() == null ? "null" : ltm.body())
        + "}";
    return ProbeResult.answered(name(), s, identifier, answer.status(), metrics,
        FundamentalProbeSupport.countNumbers(root), latest, sanitize(sample)).withNote(note)
        .withValues(values(root));
  }

  /**
   * The raw values the fundamental data feature stores, the newest historical period and the field names of its entry,
   * so that the net income needed by the unit check (EPS · shares against net income) can be located.
   */
  private static String values(JsonNode root) {
    JsonNode newest = newestHistorical(root.path("historical"));
    StringBuilder keys = new StringBuilder();
    if (newest != null) {
      newest.propertyNames().forEach(name -> keys.append(keys.isEmpty() ? "" : ",").append(name));
    }
    return "eps=" + FundamentalProbeSupport.text(root, "/perShare/eps") + " cur="
        + FundamentalProbeSupport.text(root, "/currency") + " bvps=" + FundamentalProbeSupport.text(root, "/perShare/bps")
        + " sps=" + FundamentalProbeSupport.text(root, "/perShare/sps") + " sharesMio="
        + FundamentalProbeSupport.text(root, "/other/shares") + " netDebt="
        + FundamentalProbeSupport.text(root, "/leverage/netDebt") + " mcapMio="
        + FundamentalProbeSupport.text(root, "/valuation/marketCap") + " period="
        + (newest == null ? "" : FundamentalProbeSupport.text(newest, "/period")) + " historicalKeys=" + keys;
  }

  /** The entry of the historical array with the greatest period, null when there is none. */
  private static JsonNode newestHistorical(JsonNode historical) {
    JsonNode newest = null;
    for (JsonNode entry : historical) {
      String period = FundamentalProbeSupport.text(entry, "/period");
      if (newest == null || period.compareTo(FundamentalProbeSupport.text(newest, "/period")) > 0) {
        newest = entry;
      }
    }
    return newest;
  }

  /** HTTP status and numeric field count of {@code key-figures}, to see whether the free plan serves it. */
  private String keyFiguresProbe(String identifier) {
    HttpAnswer side = request("key-figures", identifier);
    return "key-figures=" + side.status() + "/"
        + FundamentalProbeSupport.countNumbers(FundamentalProbeSupport.parse(side.body()));
  }

  private HttpAnswer request(String endpoint, String identifier) {
    return FundamentalProbeSupport.get(DOMAIN + endpoint + "/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8)
        + "?token=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8), Map.of());
  }

  /** An answer must never carry the key into the sample files. */
  private String sanitize(String body) {
    return body == null ? null : body.replace(apiKey, "***");
  }
}
