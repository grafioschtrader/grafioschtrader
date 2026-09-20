package grafioschtrader.service;

import java.util.*;

import grafioschtrader.common.DataBusinessHelper;

/** Deterministic parent-relative percentages, with residual hundredths assigned by largest remainder. */
public final class AlgoAllocationWeights {
  private AlgoAllocationWeights() {
  }

  /**
   * The ceiling of the generated AlgoTop, as a percentage of net equity.
   *
   * <p>
   * A leveraged reconstruction, where gross exposure exceeds equity, has no representation: the hierarchy divides one
   * investment budget that is itself a share of equity, and the percentage of every node is bounded by 100. Such a
   * portfolio is rejected rather than silently clamped, and the message names the two figures so the user can see which
   * holdings caused it.
   * </p>
   *
   * @param equity   signed net equity in tenant currency, cash and liabilities included
   * @param exposure gross exposure in tenant currency, the sum of absolute position exposures
   * @return the ceiling in percentage points, rounded to hundredths
   */
  public static double topPercentage(double equity, double exposure) {
    if (!Double.isFinite(equity) || !Double.isFinite(exposure) || equity <= 0 || exposure <= 0
        || exposure > equity + 1e-8)
      throw AlgoHistoricalValuationService.invalid("algo.allocation.invalid",
          String.format(Locale.ROOT, "equity %.2f, gross exposure %.2f", equity, exposure));
    return DataBusinessHelper.roundPercentage(exposure / equity * 100.0);
  }

  public static Map<Integer, Float> normalize(Map<Integer, Double> values) {
    double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
    if (total <= 0 || !Double.isFinite(total) || values.values().stream().anyMatch(v -> v <= 0 || !Double.isFinite(v)))
      throw AlgoHistoricalValuationService.invalid("algo.allocation.invalid",
          String.format(Locale.ROOT, "sibling exposures %s", values.values()));
    Map<Integer, Integer> hundredths = new TreeMap<>();
    values.forEach((id, value) -> hundredths.put(id, (int) Math.floor(value / total * 10000)));
    int remainder = 10000 - hundredths.values().stream().mapToInt(Integer::intValue).sum();
    var order = values.keySet().stream()
        .sorted(Comparator.<Integer>comparingDouble(id -> -(values.get(id) / total * 10000 - hundredths.get(id)))
            .thenComparingInt(id -> id))
        .toList();
    for (int i = 0; i < remainder; i++)
      hundredths.merge(order.get(i), 1, Integer::sum);
    Map<Integer, Float> result = new LinkedHashMap<>();
    hundredths.forEach((id, value) -> result.put(id, value / 100f));
    return result;
  }
}
