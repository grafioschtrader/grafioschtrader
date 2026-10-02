package grafioschtrader.report.pdf;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;

/** One valuation shared by holdings and allocation. Missing prices propagate into totals rather than becoming zero. */
public record HoldingsData(List<SecurityPositionSummary> positions, Map<String, Double> exchangeRates) {
  public HoldingsData(SecurityPositionGrandSummary summary) {
    this(summary.securityPositionGroupSummaryList.stream().flatMap(g -> g.securityPositionSummaryList.stream())
        .sorted(Comparator.comparing((SecurityPositionSummary p) -> p.getSecurity().getAssetClass().getCategoryType())
            .thenComparing(p -> p.getSecurity().getName(), String.CASE_INSENSITIVE_ORDER))
        .toList(), summary.exchangeRates);
  }

  public HoldingsData {
    positions = List.copyOf(positions);
    exchangeRates = Map.copyOf(exchangeRates);
  }

  public static boolean cash(SecurityPositionSummary p) {
    return p.getSecurity().getIdSecuritycurrency() < 0;
  }

  public static double value(SecurityPositionSummary p) {
    return p.priceMissing ? Double.NaN : p.accountValueSecurityMC;
  }

  public double total() {
    return positions.stream().mapToDouble(HoldingsData::value).sum();
  }

  public Double weight(double value) {
    return total() > 0 && Double.isFinite(value) ? value / total() * 100 : null;
  }

  public Map<AssetclassType, Double> assetClasses() {
    return group(p -> p.getSecurity().getAssetClass().getCategoryType());
  }

  public Map<String, Double> currencies() {
    return group(p -> p.getSecurity().getCurrency());
  }

  private <T extends Comparable<T>> Map<T, Double> group(Function<SecurityPositionSummary, T> key) {
    Map<T, Double> result = new TreeMap<>();
    positions.forEach(p -> result.merge(key.apply(p), value(p), Double::sum));
    return result;
  }

  public double cell(String currency, AssetclassType type) {
    return positions.stream().filter(p -> p.getSecurity().getCurrency().equals(currency)
        && p.getSecurity().getAssetClass().getCategoryType() == type).mapToDouble(HoldingsData::value).sum();
  }

  static String money(double value, ReportData data, ReportFormatter f) {
    return Double.isFinite(value) ? f.money(value, data.currency()) : "n/a";
  }

  static String weight(double value, ReportData data, ReportFormatter f) {
    return Double.isFinite(data.holdings().total()) && Double.isFinite(value) ? f.percent(data.holdings().weight(value))
        : "n/a";
  }
}
