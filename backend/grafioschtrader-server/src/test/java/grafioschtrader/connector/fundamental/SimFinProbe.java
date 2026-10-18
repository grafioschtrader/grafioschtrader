package grafioschtrader.connector.fundamental;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import grafioschtrader.connector.fundamental.FundamentalProbeSupport.FundamentalSourceProbe;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Metric;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeResult;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeSecurity;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Status;
import tools.jackson.databind.JsonNode;

/**
 * Probes the SimFin web API v3, {@code GET /api/v3/companies/statements/compact} with the {@code DERIVED} statement as
 * trailing-twelve-month rows ({@code ttm=true}). The key travels in the header {@code Authorization: api-key …}.
 *
 * <p>
 * SimFin's free plan names US stocks only; its bulk market {@code de} holds 25 DAX companies last updated for 2021. The
 * probe therefore asks by ticker for US exchanges and by ticker plus {@code .DE} for Xetra, the form of SimFin's German
 * tickers, and reports every other exchange as "no symbol". The newest row's report date is the latest period; the
 * {@code DERIVED} statement carries no share count, so {@link Metric#SHARES} is never present here.
 * </p>
 */
final class SimFinProbe implements FundamentalSourceProbe {

  private static final String URL = "https://backend.simfin.com/api/v3/companies/statements/compact?ticker=%s"
      + "&statements=DERIVED&period=q1,q2,q3,q4&ttm=true&start=%s";

  private final String apiKey;

  SimFinProbe(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public String name() {
    return "simfin";
  }

  @Override
  public Duration pause() {
    // The free plan allows two requests per second; a burst above it is refused as an exhausted quota.
    return Duration.ofMillis(700);
  }

  @Override
  public String prepare() {
    return FundamentalProbeSupport.notBlank(apiKey) ? null : "no API key, set GT_PROBE_KEY_SIMFIN";
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    String symbol = symbol(s);
    if (symbol == null) {
      return ProbeResult.of(name(), s, "", Status.NO_SYMBOL, 0, "SimFin covers US exchanges and Xetra only");
    }
    String url = String.format(URL, URLEncoder.encode(symbol, StandardCharsets.UTF_8),
        LocalDate.now().minusYears(2));
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "api-key " + apiKey);
    headers.put("accept", "application/json");
    HttpAnswer answer = FundamentalProbeSupport.get(url, headers);
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    JsonNode statement = root == null || !root.isArray() || root.isEmpty() ? null
        : root.get(0).path("statements").path(0);
    if (answer.status() != 200 || statement == null || statement.path("data").isEmpty()) {
      return ProbeResult.of(name(), s, symbol, answer.status() == 200 ? Status.NOT_FOUND
          : FundamentalProbeSupport.statusOf(answer.status()), answer.status(),
          root == null ? "" : root.path("message").asString(""));
    }
    Map<String, JsonNode> newest = newestRow(statement);
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    metrics.put(Metric.EPS, isNumber(newest, "Earnings Per Share, Diluted"));
    metrics.put(Metric.SHARES, false);
    metrics.put(Metric.BOOK_VALUE, isNumber(newest, "Equity Per Share"));
    metrics.put(Metric.REVENUE, isNumber(newest, "Sales Per Share"));
    metrics.put(Metric.NET_INCOME, isNumber(newest, "Net Income (Adjusted)"));
    metrics.put(Metric.NET_DEBT, isNumber(newest, "Total Debt"));
    metrics.put(Metric.MARKET_CAP, false);
    metrics.put(Metric.PE, false);
    String latest = newest.get("Report Date").asString("");
    return ProbeResult.answered(name(), s, symbol, answer.status(), metrics, statement.path("columns").size(), latest,
        answer.body().replace(apiKey, "***"))
        .withValues("eps=" + newest.get("Earnings Per Share, Diluted").asString("") + " "
            + root.get(0).path("currency").asString("") + " ttm=" + newest.get("TTM").asString(""));
  }

  private static String symbol(ProbeSecurity s) {
    if (!FundamentalProbeSupport.notBlank(s.ticker())) {
      return null;
    }
    if (FundamentalProbeSupport.US_MICS.contains(s.mic())) {
      return s.ticker().trim();
    }
    return "XETR".equals(s.mic()) ? s.ticker().trim() + ".DE" : null;
  }

  /** The row with the newest report date, as column name to value. */
  private static Map<String, JsonNode> newestRow(JsonNode statement) {
    JsonNode columns = statement.path("columns");
    int reportDate = indexOf(columns, "Report Date");
    JsonNode newest = null;
    for (JsonNode row : statement.path("data")) {
      if (newest == null || row.get(reportDate).asString("").compareTo(newest.get(reportDate).asString("")) > 0) {
        newest = row;
      }
    }
    Map<String, JsonNode> byName = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      byName.put(columns.get(i).asString(), newest.get(i));
    }
    return byName;
  }

  private static int indexOf(JsonNode columns, String name) {
    for (int i = 0; i < columns.size(); i++) {
      if (name.equals(columns.get(i).asString())) {
        return i;
      }
    }
    throw new IllegalStateException("SimFin answer without column " + name);
  }

  private static boolean isNumber(Map<String, JsonNode> row, String column) {
    JsonNode node = row.get(column);
    return node != null && node.isNumber();
  }
}
