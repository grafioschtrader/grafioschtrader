package grafioschtrader.instrument;

import java.util.Comparator;
import java.util.stream.IntStream;

import grafiosch.common.DataHelper;

/**
 * Distributes the cost a spin-off moves from the parent over the parent's lots. Each part is proportional to the
 * weight of its lot and the parts add up to the rounded total exactly, in minor units of the given precision. The
 * minor units that rounding down leaves open go to the parts with the largest remainders, with the lot position as
 * tie-break, so the result depends neither on which lot happens to be the last one nor on rounding errors that
 * accumulate over many small lots.
 *
 * <p>
 * A part never exceeds the cap of its lot, so a parent lot can not end up with a negative cost. A lot of weight 0 or
 * less receives nothing.
 * </p>
 */
public final class SpinOffCostDistribution {
  private static final double EPS = 1e-7;

  private SpinOffCostDistribution() {
  }

  /**
   * Splits a total in proportion to the weights.
   *
   * @param total     the amount to distribute, already rounded to {@code precision}
   * @param weights   the basis of each part, typically the cost of each lot
   * @param caps      the maximum of each part, aligned with {@code weights}; null for no maximum
   * @param precision the number of decimal places of the minor unit
   * @return the parts, aligned with {@code weights}. They add up to {@code total} unless the caps can not hold it,
   *         which happens only when the caps carry fractions of the minor unit and the total is a few minor units;
   *         the parts then add up to less.
   */
  public static double[] distribute(double total, double[] weights, double[] caps, int precision) {
    int n = weights.length;
    double scale = Math.pow(10, precision);
    long totalUnits = Math.round(total * scale);
    double weightSum = 0;
    for (double weight : weights) {
      weightSum += Math.max(weight, 0);
    }
    long[] parts = new long[n];
    if (totalUnits <= 0 || weightSum <= 0) {
      return toAmounts(parts, scale, precision);
    }
    long[] capUnits = new long[n];
    double[] rests = new double[n];
    long missing = totalUnits;
    for (int i = 0; i < n; i++) {
      capUnits[i] = weights[i] <= 0 ? 0
          : caps == null ? Long.MAX_VALUE : Math.max(0, (long) Math.floor(caps[i] * scale + EPS));
      if (weights[i] > 0) {
        double ideal = totalUnits * weights[i] / weightSum;
        long base = (long) Math.floor(ideal + EPS);
        parts[i] = Math.min(base, capUnits[i]);
        rests[i] = ideal - base;
        missing -= parts[i];
      }
    }
    Integer[] byRest = IntStream.range(0, n).boxed()
        .sorted(Comparator.<Integer> comparingDouble(i -> rests[i]).reversed().thenComparingInt(i -> i))
        .toArray(Integer[]::new);
    for (int k = 0; k < n && missing > 0; k++) {
      int i = byRest[k];
      if (weights[i] > 0 && parts[i] < capUnits[i]) {
        parts[i]++;
        missing--;
      }
    }
    for (int i = 0; i < n && missing > 0; i++) {
      long take = Math.min(capUnits[i] - parts[i], missing);
      parts[i] += take;
      missing -= take;
    }
    for (int k = n - 1; k >= 0 && missing < 0; k--) {
      int i = byRest[k];
      if (parts[i] > 0) {
        parts[i]--;
        missing++;
      }
    }
    return toAmounts(parts, scale, precision);
  }

  /** Sum of the parts, rounded to the precision so that floating point noise does not leak into a total. */
  public static double sum(double[] parts, int precision) {
    double sum = 0;
    for (double part : parts) {
      sum += part;
    }
    return DataHelper.round(sum, precision);
  }

  private static double[] toAmounts(long[] parts, double scale, int precision) {
    double[] amounts = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      amounts[i] = DataHelper.round(parts[i] / scale, precision);
    }
    return amounts;
  }
}
