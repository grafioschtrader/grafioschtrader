package grafioschtrader.connector.fundamental;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
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
        .withValues(values(result));
  }

  /**
   * The raw values with both currency fields and the ratios that settle the field contract of the fundamental data
   * feature. {@code trailingEps} is stated in the quote currency of the symbol ({@code summaryDetail.currency}), not in
   * {@code financialData.financialCurrency}; for the other fields the currency is not established. Each ratio is about 1
   * when the field is in the quote currency and on the share basis of the listing, and about an exchange rate or a
   * depositary ratio otherwise:
   * <ul>
   * <li>{@code bvRatio} = previousClose / (priceToBook · bookValue)</li>
   * <li>{@code psRatio} = marketCap / (priceToSalesTrailing12Months · totalRevenue)</li>
   * <li>{@code shRatio} = marketCap / (previousClose · sharesOutstanding)</li>
   * <li>{@code evRatio} = (marketCap + totalDebt − totalCash) / enterpriseValue</li>
   * </ul>
   */
  private static String values(JsonNode r) {
    Double close = number(r, SD + "previousClose/raw");
    Double marketCap = number(r, SD + "marketCap/raw");
    Double bookValue = number(r, DKS + "bookValue/raw");
    Double revenue = number(r, FD + "totalRevenue/raw");
    Double shares = number(r, DKS + "sharesOutstanding/raw");
    Double debt = number(r, FD + "totalDebt/raw");
    Double cash = number(r, FD + "totalCash/raw");
    return "eps=" + text(r, DKS + "trailingEps/raw") + " cur=" + text(r, SD + "currency") + " finCur="
        + text(r, FD + "financialCurrency") + " pe=" + text(r, SD + "trailingPE/raw") + " close="
        + text(r, SD + "previousClose/raw") + " bvps=" + text(r, DKS + "bookValue/raw") + " ptb="
        + text(r, DKS + "priceToBook/raw") + " revenue=" + text(r, FD + "totalRevenue/raw") + " pts="
        + text(r, SD + "priceToSalesTrailing12Months/raw") + " shares=" + text(r, DKS + "sharesOutstanding/raw")
        + " debt=" + text(r, FD + "totalDebt/raw") + " cash=" + text(r, FD + "totalCash/raw") + " mcap="
        + text(r, SD + "marketCap/raw") + " ev=" + text(r, DKS + "enterpriseValue/raw") + " mrq="
        + text(r, DKS + "mostRecentQuarter/fmt") + " bvRatio="
        + ratio(close, product(number(r, DKS + "priceToBook/raw"), bookValue)) + " psRatio="
        + ratio(marketCap, product(number(r, SD + "priceToSalesTrailing12Months/raw"), revenue)) + " shRatio="
        + ratio(marketCap, product(close, shares)) + " evRatio="
        + ratio(marketCap == null || debt == null || cash == null ? null : marketCap + debt - cash,
            number(r, DKS + "enterpriseValue/raw"));
  }

  private static String text(JsonNode root, String pointer) {
    return FundamentalProbeSupport.text(root, pointer);
  }

  private static Double number(JsonNode root, String pointer) {
    JsonNode node = root.at(pointer);
    return node.isNumber() ? node.asDouble() : null;
  }

  private static Double product(Double a, Double b) {
    return a == null || b == null ? null : a * b;
  }

  private static String ratio(Double numerator, Double denominator) {
    return numerator == null || denominator == null || denominator == 0 ? ""
        : String.format(Locale.ROOT, "%.3f", numerator / denominator);
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
