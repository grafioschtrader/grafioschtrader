package grafioschtrader.connector.fundamental;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.FundamentalSourceProbe;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Metric;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeResult;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeSecurity;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Status;
import grafioschtrader.connector.yahoo.CrumbManager;
import tools.jackson.databind.JsonNode;

/**
 * Probes Yahoo's unofficial {@code v10/finance/quoteSummary} with the modules {@code defaultKeyStatistics},
 * {@code financialData} and {@code summaryDetail}. It needs no key, but a cookie and crumb, taken from the
 * application's own {@link CrumbManager}; a 401 resets both once and repeats the request.
 *
 * <p>
 * The symbol follows {@link FundamentalProbeSupport#yahooSymbol}: the Yahoo connector's extension, then the
 * system-wide Yahoo symbol UDF, then ticker plus exchange suffix.
 * </p>
 */
final class YahooQuoteSummaryProbe implements FundamentalSourceProbe {

  private static final String URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s"
      + "?modules=defaultKeyStatistics,financialData,summaryDetail&crumb=%s";
  private static final String DKS = "/defaultKeyStatistics/";
  private static final String FD = "/financialData/";
  private static final String SD = "/summaryDetail/";

  @Override
  public String name() {
    return "yahoo";
  }

  @Override
  public Duration pause() {
    return Duration.ofMillis(600);
  }

  @Override
  public String prepare() {
    return FundamentalProbeSupport.notBlank(CrumbManager.getCrumb()) ? null : "no Yahoo crumb obtained";
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    String symbol = FundamentalProbeSupport.yahooSymbol(s);
    if (symbol == null) {
      return ProbeResult.of(name(), s, "", Status.NO_SYMBOL, 0, "no Yahoo symbol and no suffix for " + s.mic());
    }
    HttpAnswer answer = request(symbol);
    if (answer.status() == 401) {
      CrumbManager.resetCookieCrumb();
      answer = request(symbol);
    }
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    JsonNode result = root == null ? null : root.at("/quoteSummary/result/0");
    if (answer.status() != 200 || result == null || result.isMissingNode()) {
      String note = root == null ? "" : root.at("/quoteSummary/error/description").asString("");
      return ProbeResult.of(name(), s, symbol, FundamentalProbeSupport.statusOf(answer.status()), answer.status(),
          note);
    }
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    metrics.put(Metric.EPS, FundamentalProbeSupport.hasNumber(result, DKS + "trailingEps/raw"));
    metrics.put(Metric.SHARES, FundamentalProbeSupport.hasNumber(result, DKS + "sharesOutstanding/raw"));
    metrics.put(Metric.BOOK_VALUE, FundamentalProbeSupport.hasNumber(result, DKS + "bookValue/raw"));
    metrics.put(Metric.REVENUE, FundamentalProbeSupport.hasNumber(result, FD + "totalRevenue/raw"));
    metrics.put(Metric.NET_INCOME, FundamentalProbeSupport.hasNumber(result, DKS + "netIncomeToCommon/raw"));
    metrics.put(Metric.NET_DEBT, FundamentalProbeSupport.hasNumber(result, FD + "totalDebt/raw")
        && FundamentalProbeSupport.hasNumber(result, FD + "totalCash/raw"));
    metrics.put(Metric.MARKET_CAP, FundamentalProbeSupport.hasNumber(result, SD + "marketCap/raw"));
    metrics.put(Metric.PE, FundamentalProbeSupport.hasNumber(result, SD + "trailingPE/raw"));
    String latest = result.at(DKS + "mostRecentQuarter/fmt").asString("");
    return ProbeResult.answered(name(), s, symbol, answer.status(), metrics,
        FundamentalProbeSupport.countNumbers(result), latest, answer.body())
        // trailingEps is stated in the quote currency of the symbol, not in financialData.financialCurrency.
        .withValues("eps=" + FundamentalProbeSupport.text(result, DKS + "trailingEps/raw") + " "
            + FundamentalProbeSupport.text(result, SD + "currency") + " pe="
            + FundamentalProbeSupport.text(result, SD + "trailingPE/raw"));
  }

  private static HttpAnswer request(String symbol) {
    String url = String.format(URL, URLEncoder.encode(symbol, StandardCharsets.UTF_8),
        URLEncoder.encode(CrumbManager.getCrumb(), StandardCharsets.UTF_8));
    Map<String, String> headers = new HashMap<>(Map.of("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT));
    String cookie = CrumbManager.getCookie();
    if (FundamentalProbeSupport.notBlank(cookie)) {
      headers.put("Cookie", cookie);
    }
    return FundamentalProbeSupport.get(url, headers);
  }
}
