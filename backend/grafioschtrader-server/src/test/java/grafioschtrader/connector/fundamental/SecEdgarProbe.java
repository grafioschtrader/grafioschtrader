package grafioschtrader.connector.fundamental;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import grafioschtrader.connector.fundamental.FundamentalProbeSupport.FundamentalSourceProbe;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.HttpAnswer;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Metric;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeResult;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.ProbeSecurity;
import grafioschtrader.connector.fundamental.FundamentalProbeSupport.Status;
import tools.jackson.databind.JsonNode;

/**
 * Probes the SEC EDGAR XBRL API ({@code data.sec.gov/api/xbrl/companyfacts}), which is free and needs no key but
 * covers only companies filing with the SEC: US issuers and foreign issuers with a US listing (20-F/40-F filers).
 *
 * <p>
 * The security is mapped to a CIK through {@code company_tickers_exchange.json}: by ticker on a US exchange, otherwise
 * by an exact match of the normalised company name, which finds foreign issuers such as Novartis under their ADR. The
 * SEC delivers reported facts only, so {@link Metric#MARKET_CAP} and {@link Metric#PE} are never present; the latest
 * period end of the earnings per share shows how current the facts are.
 * </p>
 *
 * <p>
 * The SEC requires a User-Agent with a contact address, passed as {@code -Dgt.probe.sec.contact=<email>}.
 * </p>
 */
final class SecEdgarProbe implements FundamentalSourceProbe {

  private static final String TICKER_LIST = "https://www.sec.gov/files/company_tickers_exchange.json";
  private static final String FACTS = "https://data.sec.gov/api/xbrl/companyfacts/CIK%010d.json";

  /** XBRL concepts per metric as {@code taxonomy/tag}; the first present one counts. */
  private static final Map<Metric, List<String>> TAGS = new EnumMap<>(Map.of(
      Metric.EPS, List.of("us-gaap/EarningsPerShareDiluted", "us-gaap/EarningsPerShareBasic",
          "ifrs-full/DilutedEarningsPerShare", "ifrs-full/BasicEarningsPerShare"),
      Metric.SHARES, List.of("dei/EntityCommonStockSharesOutstanding",
          "us-gaap/WeightedAverageNumberOfDilutedSharesOutstanding", "ifrs-full/WeightedAverageShares"),
      Metric.BOOK_VALUE, List.of("us-gaap/StockholdersEquity", "ifrs-full/EquityAttributableToOwnersOfParent",
          "ifrs-full/Equity"),
      Metric.REVENUE, List.of("us-gaap/Revenues", "us-gaap/RevenueFromContractWithCustomerExcludingAssessedTax",
          "us-gaap/SalesRevenueNet", "ifrs-full/Revenue"),
      Metric.NET_INCOME, List.of("us-gaap/NetIncomeLoss", "ifrs-full/ProfitLossAttributableToOwnersOfParent",
          "ifrs-full/ProfitLoss")));

  private static final List<String> DEBT_TAGS = List.of("us-gaap/LongTermDebt", "us-gaap/LongTermDebtNoncurrent",
      "ifrs-full/Borrowings", "ifrs-full/NoncurrentPortionOfNoncurrentBorrowings");
  private static final List<String> CASH_TAGS = List.of("us-gaap/CashAndCashEquivalentsAtCarryingValue",
      "ifrs-full/CashAndCashEquivalents");

  private final String contact;
  private final Map<String, Long> cikByTicker = new HashMap<>();
  private final Map<String, Long> cikByName = new HashMap<>();

  SecEdgarProbe(String contact) {
    this.contact = contact;
  }

  @Override
  public String name() {
    return "sec";
  }

  @Override
  public Duration pause() {
    // The SEC allows 10 requests per second.
    return Duration.ofMillis(150);
  }

  @Override
  public String prepare() {
    if (!FundamentalProbeSupport.notBlank(contact)) {
      return "no contact address, run with -Dgt.probe.sec.contact=<email>";
    }
    HttpAnswer answer = FundamentalProbeSupport.get(TICKER_LIST, headers());
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    if (answer.status() != 200 || root == null) {
      return "ticker list not loaded, HTTP " + answer.status();
    }
    Set<String> ambiguousNames = new HashSet<>();
    for (JsonNode row : root.at("/data")) {
      long cik = row.get(0).asLong();
      cikByTicker.putIfAbsent(row.get(2).asString().toUpperCase(Locale.ROOT), cik);
      String name = FundamentalProbeSupport.normalizeName(row.get(1).asString());
      Long previous = cikByName.putIfAbsent(name, cik);
      if (previous != null && previous != cik) {
        ambiguousNames.add(name);
      }
    }
    ambiguousNames.forEach(cikByName::remove);
    return null;
  }

  @Override
  public ProbeResult probe(ProbeSecurity s) {
    Long cik = null;
    String matchedBy = "ticker";
    if (FundamentalProbeSupport.US_MICS.contains(s.mic()) && FundamentalProbeSupport.notBlank(s.ticker())) {
      cik = cikByTicker.get(s.ticker().trim().toUpperCase(Locale.ROOT));
    }
    if (cik == null) {
      cik = cikByName.get(FundamentalProbeSupport.normalizeName(s.name()));
      matchedBy = "name";
    }
    if (cik == null) {
      return ProbeResult.of(name(), s, "", Status.NOT_FOUND, 0, "not in the SEC ticker list");
    }
    String symbol = "CIK" + cik + " (" + matchedBy + ")";
    HttpAnswer answer = FundamentalProbeSupport.get(String.format(FACTS, cik), headers());
    JsonNode root = FundamentalProbeSupport.parse(answer.body());
    if (answer.status() != 200 || root == null) {
      return ProbeResult.of(name(), s, symbol, FundamentalProbeSupport.statusOf(answer.status()), answer.status(),
          "companyfacts not loaded");
    }
    JsonNode facts = root.at("/facts");
    Map<Metric, Boolean> metrics = new EnumMap<>(Metric.class);
    TAGS.forEach((metric, tags) -> metrics.put(metric, firstPresent(facts, tags) != null));
    metrics.put(Metric.NET_DEBT, firstPresent(facts, DEBT_TAGS) != null && firstPresent(facts, CASH_TAGS) != null);
    metrics.put(Metric.MARKET_CAP, false);
    metrics.put(Metric.PE, false);
    String epsTag = firstPresent(facts, TAGS.get(Metric.EPS));
    String latest = epsTag == null ? "" : latestEnd(facts.at("/" + epsTag + "/units"));
    // The full companyfacts document is several megabytes; the sample keeps only the EPS concept.
    String sample = epsTag == null ? null : facts.at("/" + epsTag).toString();
    return ProbeResult.answered(name(), s, symbol, answer.status(), metrics, countConcepts(facts), latest, sample);
  }

  private Map<String, String> headers() {
    return Map.of("User-Agent", "Grafioschtrader fundamental coverage probe " + contact);
  }

  /** First concept of the list that has at least one reported value, as {@code taxonomy/tag}, or null. */
  private static String firstPresent(JsonNode facts, List<String> tags) {
    for (String tag : tags) {
      for (JsonNode values : facts.at("/" + tag + "/units")) {
        if (!values.isEmpty()) {
          return tag;
        }
      }
    }
    return null;
  }

  /** Newest {@code end} date over all units of one concept; ISO dates compare correctly as strings. */
  private static String latestEnd(JsonNode units) {
    String latest = "";
    for (JsonNode values : units) {
      for (JsonNode value : values) {
        String end = value.path("end").asString("");
        if (end.compareTo(latest) > 0) {
          latest = end;
        }
      }
    }
    return latest;
  }

  /** Number of reported concepts over all taxonomies, as the richness measure of this source. */
  private static int countConcepts(JsonNode facts) {
    int count = 0;
    for (JsonNode taxonomy : facts) {
      count += taxonomy.size();
    }
    return count;
  }
}
