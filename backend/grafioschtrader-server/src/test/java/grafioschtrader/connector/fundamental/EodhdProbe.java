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
 * Probes EODHD's {@code /api/fundamentals/{symbol}} with the {@code Highlights} and {@code SharesStats} sections. The
 * plan behind the stored key is not known to include fundamentals, so {@link #prepare()} first asks for one well-known
 * symbol; a refusal there skips the source for all securities instead of spending a request on each.
 *
 * <p>
 * The symbol is the EODHD connector's own extension when the security is priced by EODHD, otherwise ticker plus the
 * EODHD exchange code.
 * </p>
 */
final class EodhdProbe implements FundamentalSourceProbe {

  private static final String URL = "https://eodhd.com/api/fundamentals/%s?api_token=%s&fmt=json"
      + "&filter=Highlights,SharesStats";

  private static final Map<String, String> EXCHANGE = Map.ofEntries(Map.entry("XNAS", "US"), Map.entry("XNYS", "US"),
      Map.entry("XASE", "US"), Map.entry("ARCX", "US"), Map.entry("BATS", "US"), Map.entry("XSWX", "SW"),
      Map.entry("XETR", "XETRA"), Map.entry("XFRA", "F"), Map.entry("XMUN", "MU"), Map.entry("XPAR", "PA"),
      Map.entry("XLON", "LSE"), Map.entry("XVIE", "VI"), Map.entry("XMIL", "MI"), Map.entry("XMAD", "MC"),
      Map.entry("XTSE", "TO"), Map.entry("XTSX", "V"), Map.entry("XWAR", "WAR"), Map.entry("XASX", "AU"),
      Map.entry("XSTO", "ST"), Map.entry("XOSL", "OL"), Map.entry("XCSE", "CO"), Map.entry("XHEL", "HE"),
      Map.entry("XAMS", "AS"), Map.entry("XBRU", "BR"), Map.entry("XLIS", "LS"), Map.entry("XKRX", "KO"),
      Map.entry("XTKS", "TSE"));

  private final String apiKey;

  EodhdProbe(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public String name() {
    return "eodhd";
  }

  @Override
  public Duration pause() {
    return Duration.ofMillis(200);
  }

  @Override
  public String prepare() {
    if (!FundamentalProbeSupport.notBlank(apiKey)) {
      return "no API key";
    }
    HttpAnswer answer = request("AAPL.US");
    return answer.status() == 200 ? null : "plan without fundamentals, AAPL.US answered HTTP " + answer.status();
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    String symbol = symbol(s);
    if (symbol == null) {
      return ProbeResult.of(name(), s, "", Status.NO_SYMBOL, 0, "no EODHD exchange code for " + s.mic());
    }
    HttpAnswer answer = request(symbol);
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    if (answer.status() != 200 || root == null) {
      return ProbeResult.of(name(), s, symbol, FundamentalProbeSupport.statusOf(answer.status()), answer.status(),
          "");
    }
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    metrics.put(Metric.EPS, FundamentalProbeSupport.hasNumber(root, "/Highlights/EarningsShare"));
    metrics.put(Metric.SHARES, FundamentalProbeSupport.hasNumber(root, "/SharesStats/SharesOutstanding"));
    metrics.put(Metric.BOOK_VALUE, FundamentalProbeSupport.hasNumber(root, "/Highlights/BookValue"));
    metrics.put(Metric.REVENUE, FundamentalProbeSupport.hasNumber(root, "/Highlights/RevenueTTM"));
    metrics.put(Metric.NET_INCOME, FundamentalProbeSupport.hasNumber(root, "/Highlights/ProfitMargin"));
    metrics.put(Metric.NET_DEBT, false);
    metrics.put(Metric.MARKET_CAP, FundamentalProbeSupport.hasNumber(root, "/Highlights/MarketCapitalization"));
    metrics.put(Metric.PE, FundamentalProbeSupport.hasNumber(root, "/Highlights/PERatio"));
    String latest = root.at("/Highlights/MostRecentQuarter").asString("");
    return ProbeResult.answered(name(), s, symbol, answer.status(), metrics,
        FundamentalProbeSupport.countNumbers(root), latest, answer.body().replace(apiKey, "***"));
  }

  private String symbol(ProbeSecurity s) {
    if ("gt.datafeed.eodhistoricaldata".equals(s.idConnectorIntra())
        && FundamentalProbeSupport.notBlank(s.urlIntraExtend())) {
      return s.urlIntraExtend().trim();
    }
    String exchange = EXCHANGE.get(s.mic());
    return FundamentalProbeSupport.notBlank(s.ticker()) && exchange != null ? s.ticker().trim() + "." + exchange
        : null;
  }

  private HttpAnswer request(String symbol) {
    return FundamentalProbeSupport.get(String.format(URL, URLEncoder.encode(symbol, StandardCharsets.UTF_8),
        URLEncoder.encode(apiKey, StandardCharsets.UTF_8)), Map.of());
  }
}
