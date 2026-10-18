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
 * Probes Finnhub's basic financials, {@code GET /api/v1/stock/metric?metric=all}. Third-party descriptions disagree on
 * whether the free plan answers for non-US symbols; a 403 for such a symbol is the answer the probe is looking for.
 *
 * <p>
 * The symbol is the Finnhub connector's own extension when the security is priced by Finnhub, otherwise ticker plus
 * the Yahoo-style exchange suffix, which Finnhub uses for the European exchanges. The {@code metric} object carries no
 * share count, so {@link Metric#SHARES} is never present here; {@link Metric#NET_DEBT} stands for Finnhub's
 * debt-to-equity figure, the only leverage value of this endpoint.
 * </p>
 */
final class FinnhubProbe implements FundamentalSourceProbe {

  private static final String URL = "https://finnhub.io/api/v1/stock/metric?metric=all&symbol=%s&token=%s";

  private final String apiKey;

  FinnhubProbe(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public String name() {
    return "finnhub";
  }

  @Override
  public Duration pause() {
    // The free plan allows 60 requests per minute.
    return Duration.ofMillis(1100);
  }

  @Override
  public String prepare() {
    return FundamentalProbeSupport.notBlank(apiKey) ? null : "no API key";
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    String symbol = "gt.datafeed.finnhub".equals(s.idConnectorIntra())
        && FundamentalProbeSupport.notBlank(s.urlIntraExtend()) ? s.urlIntraExtend().trim()
            : FundamentalProbeSupport.derivedSymbol(s);
    if (symbol == null) {
      return ProbeResult.of(name(), s, "", Status.NO_SYMBOL, 0, "no exchange suffix for " + s.mic());
    }
    HttpAnswer answer = FundamentalProbeSupport.get(String.format(URL,
        URLEncoder.encode(symbol, StandardCharsets.UTF_8), URLEncoder.encode(apiKey, StandardCharsets.UTF_8)),
        Map.of());
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    if (answer.status() != 200 || root == null) {
      return ProbeResult.of(name(), s, symbol, FundamentalProbeSupport.statusOf(answer.status()), answer.status(),
          root == null ? "" : root.path("error").asString(""));
    }
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    metrics.put(Metric.EPS, FundamentalProbeSupport.hasNumber(root, "/metric/epsTTM",
        "/metric/epsBasicExclExtraItemsTTM", "/metric/epsInclExtraItemsTTM", "/metric/epsAnnual"));
    metrics.put(Metric.SHARES, false);
    metrics.put(Metric.BOOK_VALUE,
        FundamentalProbeSupport.hasNumber(root, "/metric/bookValuePerShareQuarterly", "/metric/bookValuePerShareAnnual"));
    metrics.put(Metric.REVENUE,
        FundamentalProbeSupport.hasNumber(root, "/metric/revenuePerShareTTM", "/metric/revenuePerShareAnnual"));
    metrics.put(Metric.NET_INCOME, FundamentalProbeSupport.hasNumber(root, "/metric/netProfitMarginTTM"));
    metrics.put(Metric.NET_DEBT, FundamentalProbeSupport.hasNumber(root, "/metric/totalDebt~1totalEquityQuarterly",
        "/metric/totalDebt~1totalEquityAnnual"));
    metrics.put(Metric.MARKET_CAP, FundamentalProbeSupport.hasNumber(root, "/metric/marketCapitalization"));
    metrics.put(Metric.PE, FundamentalProbeSupport.hasNumber(root, "/metric/peTTM", "/metric/peBasicExclExtraTTM",
        "/metric/peExclExtraTTM", "/metric/peAnnual"));
    return ProbeResult.answered(name(), s, symbol, answer.status(), metrics,
        FundamentalProbeSupport.countNumbers(root.path("metric")), "", answer.body().replace(apiKey, "***"))
        .withValues("eps=" + FundamentalProbeSupport.text(root, "/metric/epsTTM") + " annual="
            + FundamentalProbeSupport.text(root, "/metric/epsAnnual"));
  }
}
