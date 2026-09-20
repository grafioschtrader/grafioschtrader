package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

/** Selects a bounded number of instruments for one class adjustment, without reading or writing market state. */
public final class AlgoClassRebalancingAllocator {
  public static final String VERSION = "class-bands-v1";
  public static final double EPSILON = 1e-8;

  private AlgoClassRebalancingAllocator() {
  }

  /** Exposure and weight refer to one distinct security, aggregated across its accounts. */
  public record Candidate(int idSecurity, double weight, double exposure, Long volume, boolean eligible) {
  }

  /** Positive amounts increase exposure; negative amounts reduce it, regardless of long/short transaction direction. */
  public record Selection(Map<Integer, Double> changes, double residual, String reason) {
  }

  /**
   * Uses target-class amounts for bands. Ranking is fixed before allocation; the first candidate may use its full band
   * capacity before another instrument is selected. The random tie key is independent of input ordering.
   */
  public static Selection allocate(double target, double actual, double deviation, int limit,
      List<Candidate> candidates, int idTop, int idClass, LocalDate date) {
    if (!Double.isFinite(deviation) || deviation < 0 || deviation > 100 || limit < 1)
      throw new IllegalArgumentException("Invalid security band or trade limit");
    double requested = target - actual;
    boolean increasing = requested > 0;
    Comparator<Candidate> order = Comparator.comparingDouble(c -> {
      double drift = target > 0 ? c.exposure() / target * 100 - c.weight() : c.exposure();
      return increasing ? drift : -drift;
    });
    order = order.thenComparing(Comparator.comparingLong(AlgoClassRebalancingAllocator::volume).reversed())
        .thenComparingLong(c -> tieKey(idTop, idClass, date, c.idSecurity())).thenComparingInt(Candidate::idSecurity);
    List<Candidate> ranked = candidates.stream().filter(Candidate::eligible).sorted(order).toList();
    Map<Integer, Double> changes = new LinkedHashMap<>();
    double remaining = Math.abs(requested);
    for (Candidate candidate : ranked) {
      if (remaining <= EPSILON || changes.size() >= limit)
        break;
      double lower = target * Math.max(0, candidate.weight() - deviation) / 100;
      double upper = candidate.weight() == 0 ? 0 : target * Math.min(100, candidate.weight() + deviation) / 100;
      double capacity = Math.max(0, increasing ? upper - candidate.exposure() : candidate.exposure() - lower);
      double amount = Math.min(remaining, capacity);
      if (amount > EPSILON) {
        changes.put(candidate.idSecurity(), increasing ? amount : -amount);
        remaining -= amount;
      }
    }
    String reason = remaining <= EPSILON ? null
        : changes.size() >= limit ? "REBALANCE_TRADE_LIMIT" : "REBALANCE_BAND_OR_ELIGIBILITY_LIMIT";
    return new Selection(Collections.unmodifiableMap(changes), Math.copySign(remaining, requested), reason);
  }

  private static long volume(Candidate candidate) {
    return candidate.volume() == null || candidate.volume() < 0 ? -1 : candidate.volume();
  }

  /** SplitMix64 of hierarchy IDs, valuation day and instrument ID: stable across JVMs and replay tenants. */
  private static long tieKey(int top, int bucket, LocalDate date, int security) {
    long value = ((long) top << 32) ^ bucket ^ date.toEpochDay() * 0x9e3779b97f4a7c15L ^ security;
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
