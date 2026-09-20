package grafioschtrader.service;

/** Shared parent-relative gross-exposure arithmetic for allocation reports and trading decisions. */
public final class AlgoExposureBudget {
  private AlgoExposureBudget() {
  }

  public static double amount(double equity, double percentage) {
    return equity * percentage / 100.0;
  }

  public static double childPercentage(double parent, double child) {
    return parent * child / 100.0;
  }

  public static void requireWeights(java.util.List<Float> weights) {
    if (weights.isEmpty() || weights.stream().anyMatch(w -> w == null || !Float.isFinite(w) || w < 0 || w > 100)
        || Math.abs(weights.stream().mapToDouble(Float::doubleValue).sum() - 100) > .011)
      throw new IllegalArgumentException("MEAN_REVERSION_ALLOCATION_REQUIRED");
  }
}
