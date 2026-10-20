package grafioschtrader.connector.fundamental;

import static grafioschtrader.connector.fundamental.FundamentalFeedSupport.number;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import tools.jackson.databind.JsonNode;

/**
 * Turns the answers of the three fundamental sources into rows, following one field contract: every stored value has a
 * stated unit, currency, period and share basis. A missing or non-numeric field writes no row, and every value is
 * stored in full units — Eulerpool's share count in millions is multiplied out here, before any check sees it.
 *
 * <p>
 * The methods are pure, so that they can be tested on answers recorded by the fundamental source probe.
 * </p>
 */
public final class FundamentalAnswerMapper {

  private static final String DKS = "/defaultKeyStatistics/";
  private static final String FD = "/financialData/";
  private static final String SD = "/summaryDetail/";

  /**
   * Names under which an entry of Eulerpool's {@code historical} array states the net income for the unit check of the
   * share count; the first numeric one is used. Deliberately empty: in the production run of 2026-10-20 the check
   * failed for share counts that reproduce Yahoo's market capitalisation exactly (Continental, Rheinmetall, Deutsche
   * Telekom, Sanofi and others), so the net income read from {@code netIncome} has another unit or meaning. Until a
   * recorded answer settles it, the check stays not checked; the market capitalisation check already catches a slip of
   * the millions factor.
   */
  static final List<String> EULERPOOL_NET_INCOME_FIELDS = List.of();

  private FundamentalAnswerMapper() {
  }

  /**
   * Maps Yahoo's {@code quoteSummary} with the modules {@code defaultKeyStatistics}, {@code financialData} and
   * {@code summaryDetail}. EPS, book value per listed share and market capitalisation use the major quote currency.
   * Revenue, debt and cash use {@code financialData.financialCurrency}, including for foreign listings and ADRs.
   * Revenue per share divides the company revenue by the same listing-equivalent share count used for market
   * capitalisation. The recorded Nestle, Novo Nordisk, Alibaba ADR and AstraZeneca answers document this contract in
   * {@code src/test/resources/fundamental/samples/README.md}.
   *
   * @param root               the whole answer
   * @param idSecuritycurrency the security the rows belong to
   * @param fetchedTime        when the answer arrived
   * @return the usable answer, or a failed one when the expected root object is missing
   */
  public static FundamentalAnswer yahoo(JsonNode root, Integer idSecuritycurrency, LocalDateTime fetchedTime) {
    JsonNode result = root == null ? null : root.at("/quoteSummary/result/0");
    if (result == null || result.isMissingNode() || !result.isObject()) {
      return FundamentalAnswer.failed("no quoteSummary result", 200);
    }
    Rows rows = new Rows(idSecuritycurrency, FundamentalSource.YAHOO, fetchedTime);
    String currency = FundamentalFeedSupport.majorCurrency(FundamentalFeedSupport.text(result, SD + "currency"));
    String financialCurrency = FundamentalFeedSupport
        .majorCurrency(FundamentalFeedSupport.text(result, FD + "financialCurrency"));
    LocalDate quarter = FundamentalFeedSupport.epochDate(result, DKS + "mostRecentQuarter/raw");

    rows.add(FundamentalMetric.EPS_TTM, number(result, DKS + "trailingEps/raw"), currency, quarter);
    rows.add(FundamentalMetric.BVPS, number(result, DKS + "bookValue/raw"), currency, quarter);
    Double shares = yahooShares(result);
    Double revenue = number(result, FD + "totalRevenue/raw");
    rows.add(FundamentalMetric.SPS_TTM, revenue == null || shares == null ? null : revenue / shares, financialCurrency,
        quarter);
    rows.add(FundamentalMetric.SHARES, shares, null, null);
    Double debt = number(result, FD + "totalDebt/raw");
    Double cash = number(result, FD + "totalCash/raw");
    rows.add(FundamentalMetric.NET_DEBT, debt == null || cash == null ? null : debt - cash, financialCurrency, quarter);
    rows.add(FundamentalMetric.PE_CHECK, number(result, SD + "trailingPE/raw"), null, null);
    rows.add(FundamentalMetric.MCAP_CHECK, positive(number(result, SD + "marketCap/raw")), currency, null);
    return FundamentalAnswer.usable(rows.list, Map.of());
  }

  /**
   * Company-wide shares expressed in units of the listing (ADRs for an ADR). For multiple classes, Yahoo's
   * {@code sharesOutstanding} can cover only the listed class: Novo Nordisk has 3.338 billion B shares but its market
   * capitalisation includes 4.413 billion shares. The implied count covers both. The load-time market capitalisation
   * check must pass before this count or the revenue divided by it can be selected.
   */
  private static Double yahooShares(JsonNode result) {
    Double implied = positive(number(result, DKS + "impliedSharesOutstanding/raw"));
    return implied != null ? implied : positive(number(result, DKS + "sharesOutstanding/raw"));
  }

  /**
   * Maps Finnhub's basic financials ({@code stock/metric?metric=all}). The free plan answers for US listings only,
   * whose values are in the currency of the security. The {@code metric} object names no period; the fiscal year
   * earnings take the period of the first annual entry of the {@code series} block when that entry carries the same
   * value.
   *
   * @param root               the whole answer
   * @param idSecuritycurrency the security the rows belong to
   * @param currency           the currency of the security
   * @param fetchedTime        when the answer arrived
   * @return the usable answer, or a failed one when the {@code metric} object is missing
   */
  public static FundamentalAnswer finnhub(JsonNode root, Integer idSecuritycurrency, String currency,
      LocalDateTime fetchedTime) {
    JsonNode metric = root == null ? null : root.path("metric");
    if (metric == null || !metric.isObject()) {
      return FundamentalAnswer.failed("no metric object", 200);
    }
    Rows rows = new Rows(idSecuritycurrency, FundamentalSource.FINNHUB, fetchedTime);
    rows.add(FundamentalMetric.EPS_TTM, number(metric, "/epsTTM"), currency, null);
    Double epsAnnual = number(metric, "/epsAnnual");
    Double seriesEps = number(root, "/series/annual/eps/0/v");
    LocalDate annualPeriod = epsAnnual != null && seriesEps != null && Math.abs(epsAnnual - seriesEps) < 0.005
        ? FundamentalFeedSupport.date(root, "/series/annual/eps/0/period")
        : null;
    rows.add(FundamentalMetric.EPS_FY, epsAnnual, currency, annualPeriod);
    rows.add(FundamentalMetric.BVPS, number(metric, "/bookValuePerShareQuarterly"), currency, null);
    rows.add(FundamentalMetric.SPS_TTM, number(metric, "/revenuePerShareTTM"), currency, null);
    return FundamentalAnswer.usable(rows.list, Map.of());
  }

  /**
   * Maps Eulerpool's {@code equity/metrics/{ISIN}}. The per-share values describe the last fiscal year, whose end is
   * the newest {@code period} of the {@code historical} array; {@code other.shares} is in millions, {@code leverage
   * .netDebt} in full units. The net income of the same period becomes evidence of the unit check.
   *
   * @param root               the whole answer
   * @param idSecuritycurrency the security the rows belong to
   * @param fetchedTime        when the answer arrived
   * @return the usable answer, or a failed one when the answer is not an object with per-share values
   */
  public static FundamentalAnswer eulerpool(JsonNode root, Integer idSecuritycurrency, LocalDateTime fetchedTime) {
    if (root == null || !root.isObject() || !root.path("perShare").isObject()) {
      return FundamentalAnswer.failed("no perShare object", 200);
    }
    Rows rows = new Rows(idSecuritycurrency, FundamentalSource.EULERPOOL, fetchedTime);
    String currency = FundamentalFeedSupport.text(root, "/currency");
    JsonNode newest = newestHistorical(root.path("historical"));
    LocalDate period = newest == null ? null : FundamentalFeedSupport.date(newest, "/period");
    rows.add(FundamentalMetric.EPS_FY, number(root, "/perShare/eps"), currency, period);
    rows.add(FundamentalMetric.BVPS, number(root, "/perShare/bps"), currency, period);
    rows.add(FundamentalMetric.SPS_FY, number(root, "/perShare/sps"), currency, period);
    Double sharesMillions = positive(number(root, "/other/shares"));
    rows.add(FundamentalMetric.SHARES, sharesMillions == null ? null : sharesMillions * 1_000_000, null, period);
    rows.add(FundamentalMetric.NET_DEBT, number(root, "/leverage/netDebt"), currency, period);
    Map<String, Double> evidence = new HashMap<>();
    if (newest != null) {
      EULERPOOL_NET_INCOME_FIELDS.stream().map(name -> number(newest, "/" + name)).filter(v -> v != null).findFirst()
          .ifPresent(v -> evidence.put(FundamentalAnswer.EVIDENCE_NET_INCOME, v));
    }
    return FundamentalAnswer.usable(rows.list, evidence);
  }

  /** The entry of the historical array with the latest period, null when no entry carries a period. */
  private static JsonNode newestHistorical(JsonNode historical) {
    JsonNode newest = null;
    LocalDate newestPeriod = null;
    for (JsonNode entry : historical) {
      LocalDate period = FundamentalFeedSupport.date(entry, "/period");
      if (period != null && (newestPeriod == null || period.isAfter(newestPeriod))) {
        newest = entry;
        newestPeriod = period;
      }
    }
    return newest;
  }

  private static Double positive(Double value) {
    return value == null || value <= 0 ? null : value;
  }

  /** Collects the rows of one answer, skipping absent values. */
  private static final class Rows {
    private final Integer idSecuritycurrency;
    private final FundamentalSource source;
    private final LocalDateTime fetchedTime;
    private final List<SecurityFundamental> list = new ArrayList<>();

    Rows(Integer idSecuritycurrency, FundamentalSource source, LocalDateTime fetchedTime) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.source = source;
      this.fetchedTime = fetchedTime;
    }

    void add(FundamentalMetric metric, Double value, String currency, LocalDate periodEnd) {
      if (value != null && Double.isFinite(value)) {
        list.add(new SecurityFundamental(idSecuritycurrency, metric, source, value, currency, periodEnd, fetchedTime));
      }
    }
  }
}
