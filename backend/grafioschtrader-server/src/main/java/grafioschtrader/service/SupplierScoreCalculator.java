package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafiosch.entities.GTNet;
import grafioschtrader.gtnet.GTNetExchangeKindType;

/**
 * Calculates supplier scores based on coverage, success rate and OHL data richness for optimized AC_OPEN supplier
 * selection.
 *
 * The scoring formula is: score = coverageCount x successRate x ohlFactor - coverageCount: number of requested
 * instruments this supplier supports (from GTNetSupplierDetail) - successRate: entitiesUpdated / entitiesSent from
 * recent exchange logs (default 1.0 if no history) - ohlFactor: 1 + ohlWeight x avgOhl/100, where avgOhl is the average
 * OHL percentage the supplier reported for the requested instruments; exactly 1.0 when the weight is zero or nothing is
 * known
 *
 * Coverage and reliability stay dominant: the OHL factor is a bounded secondary multiplier, so a peer with wide
 * coverage is never displaced by a peer that merely holds richer data for a single instrument.
 *
 * Suppliers are sorted by score descending, then priority ascending, with random shuffle for ties within the same
 * score+priority bucket.
 *
 * Note: This calculator is only used for AC_OPEN servers. AC_PUSH_OPEN servers continue to use the priority+random
 * algorithm in BaseGTNetExchangeService.getSuppliersByPriorityWithRandomization().
 */
public class SupplierScoreCalculator {

  /** Default success rate when no historical data exists */
  private static final double DEFAULT_SUCCESS_RATE = 1.0;

  /** Score rounding used to build tie groups, so that a continuous OHL factor does not defeat the shuffle. */
  private static final double SCORE_TIE_PRECISION = 1000.0;

  /** Map: supplierId -> success rate (0.0 to 1.0) */
  private final Map<Integer, Double> successRates;

  /** Weight of the OHL preference; 0.0 disables it and reproduces the pure coverage x success rate ordering. */
  private final double ohlWeight;

  /**
   * Creates a new score calculator with preloaded success rates and no OHL preference.
   *
   * @param successRateData list of [idGtNet, successRate] from database query
   */
  public SupplierScoreCalculator(List<Object[]> successRateData) {
    this(successRateData, 0.0);
  }

  /**
   * Creates a new score calculator with preloaded success rates and an OHL preference weight.
   *
   * @param successRateData list of [idGtNet, successRate] from database query
   * @param ohlWeight       weight of the OHL richness preference; 0.0 disables it. A supplier reporting 100% OHL is
   *                        scored (1 + ohlWeight) times a supplier reporting 0%.
   */
  public SupplierScoreCalculator(List<Object[]> successRateData, double ohlWeight) {
    this.successRates = new HashMap<>();
    this.ohlWeight = Math.max(0.0, ohlWeight);

    if (successRateData != null) {
      for (Object[] row : successRateData) {
        Integer idGtNet = ((Number) row[0]).intValue();
        Double rate = row[1] != null ? ((Number) row[1]).doubleValue() : DEFAULT_SUCCESS_RATE;
        successRates.put(idGtNet, rate);
      }
    }
  }

  /**
   * Calculates the score for a supplier without an OHL contribution.
   *
   * @param idGtNet the supplier ID
   * @param coverageCount the number of requested instruments this supplier supports
   * @return the score (coverageCount x successRate)
   */
  public double calculateScore(Integer idGtNet, int coverageCount) {
    return calculateScore(idGtNet, coverageCount, null);
  }

  /**
   * Calculates the score for a supplier.
   *
   * @param idGtNet the supplier ID
   * @param coverageCount the number of requested instruments this supplier supports
   * @param avgOhl        the average OHL percentage (0..100) the supplier reported for the requested instruments, or
   *                      null when unknown. Unknown is neutral, never a penalty - a peer that has not been synchronised
   *                      yet, an intraday peer, and a currency pair all arrive here as null.
   * @return the score (coverageCount x successRate x ohlFactor)
   */
  public double calculateScore(Integer idGtNet, int coverageCount, Double avgOhl) {
    double successRate = successRates.getOrDefault(idGtNet, DEFAULT_SUCCESS_RATE);
    return coverageCount * successRate * getOhlFactor(avgOhl);
  }

  /**
   * Gets the success rate for a supplier.
   *
   * @param idGtNet the supplier ID
   * @return the success rate (0.0 to 1.0), or DEFAULT_SUCCESS_RATE if no data
   */
  public double getSuccessRate(Integer idGtNet) {
    return successRates.getOrDefault(idGtNet, DEFAULT_SUCCESS_RATE);
  }

  /**
   * Returns the multiplier applied for OHL richness.
   *
   * @param avgOhl the average OHL percentage (0..100), or null when unknown
   * @return 1.0 when the preference is disabled or nothing is known, otherwise 1 + ohlWeight x avgOhl/100
   */
  public double getOhlFactor(Double avgOhl) {
    if (ohlWeight <= 0.0 || avgOhl == null) {
      return 1.0;
    }
    double bounded = Math.min(100.0, Math.max(0.0, avgOhl));
    return 1.0 + ohlWeight * bounded / 100.0;
  }

  /**
   * Sorts AC_OPEN suppliers by score (descending), priority (ascending), with random shuffle for ties.
   *
   * @param suppliers list of suppliers to sort
   * @param exchangeKind the exchange kind for priority lookup
   * @param filter                 the instrument filter for coverage and OHL calculation
   * @param requestedInstrumentIds the set of instruments being requested
   * @return sorted list of suppliers
   */
  public List<GTNet> sortSuppliersByScore(List<GTNet> suppliers, GTNetExchangeKindType exchangeKind,
      SupplierInstrumentFilter filter, Set<Integer> requestedInstrumentIds) {

    if (suppliers == null || suppliers.size() <= 1) {
      return suppliers != null ? suppliers : new ArrayList<>();
    }

    // Calculate scores for each supplier
    List<ScoredSupplier> scored = new ArrayList<>();
    for (GTNet supplier : suppliers) {
      Set<Integer> supported = getSupportedInstruments(supplier, filter, requestedInstrumentIds);
      int coverage = supported.size();
      Double avgOhl = filter == null ? null : filter.getAverageOhl(supplier.getIdGtNet(), supported);
      double score = calculateScore(supplier.getIdGtNet(), coverage, avgOhl);
      byte priority = getConsumerUsage(supplier, exchangeKind);
      scored.add(new ScoredSupplier(supplier, score, priority, coverage));
    }

    // Sort by score desc, priority asc
    scored.sort((a, b) -> {
      int scoreCompare = Double.compare(b.score, a.score);
      if (scoreCompare != 0) {
        return scoreCompare;
      }
      return Byte.compare(a.priority, b.priority);
    });

    // Group by (rounded score, priority) and shuffle within groups. Rounding matters: the OHL factor makes raw scores
    // almost always unique, which would silently disable the load-spreading shuffle.
    Map<String, List<ScoredSupplier>> groups = new LinkedHashMap<>();
    for (ScoredSupplier ss : scored) {
      String key = Math.round(ss.score * SCORE_TIE_PRECISION) + ":" + ss.priority;
      groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(ss);
    }

    // Shuffle within each group and flatten
    List<GTNet> result = new ArrayList<>();
    for (List<ScoredSupplier> group : groups.values()) {
      Collections.shuffle(group);
      for (ScoredSupplier ss : group) {
        result.add(ss.supplier);
      }
    }

    return result;
  }

  /**
   * Determines which of the requested instruments a supplier supports.
   */
  private Set<Integer> getSupportedInstruments(GTNet supplier, SupplierInstrumentFilter filter,
      Set<Integer> requestedInstrumentIds) {
    if (filter == null || requestedInstrumentIds == null || requestedInstrumentIds.isEmpty()) {
      return Set.of();
    }
    // AC_OPEN: filter to only supported instruments (isPushOpen = false)
    return filter.getInstrumentsForSupplier(supplier.getIdGtNet(), requestedInstrumentIds, false);
  }

  /**
   * Gets the consumerUsage priority value for a supplier and exchange kind.
   */
  private byte getConsumerUsage(GTNet supplier, GTNetExchangeKindType exchangeKind) {
    return supplier.getGtNetEntities().stream().filter(e -> e.getEntityKindValue() == exchangeKind.getValue())
        .findFirst().map(e -> e.getGtNetConfigEntity().getConsumerUsage()).orElse((byte) 0);
  }

  /**
   * Internal class for holding scored supplier data during sorting.
   */
  private static class ScoredSupplier {
    final GTNet supplier;
    final double score;
    final byte priority;
    final int coverage;

    ScoredSupplier(GTNet supplier, double score, byte priority, int coverage) {
      this.supplier = supplier;
      this.score = score;
      this.priority = priority;
      this.coverage = coverage;
    }
  }
}
