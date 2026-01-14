package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.GTNetExchangeKindType;

/**
 * Calculates supplier scores based on coverage and success rate for optimized AC_OPEN supplier selection.
 *
 * The scoring formula is: score = coverageCount x successRate
 * - coverageCount: number of requested instruments this supplier supports (from GTNetSupplierDetail)
 * - successRate: entitiesUpdated / entitiesSent from recent exchange logs (default 1.0 if no history)
 *
 * Suppliers are sorted by score descending, then priority ascending, with random shuffle for ties
 * within the same score+priority bucket.
 *
 * Note: This calculator is only used for AC_OPEN servers. AC_PUSH_OPEN servers continue to use
 * the priority+random algorithm in BaseGTNetExchangeService.getSuppliersByPriorityWithRandomization().
 */
public class SupplierScoreCalculator {

  /** Default success rate when no historical data exists */
  private static final double DEFAULT_SUCCESS_RATE = 1.0;

  /** Map: supplierId -> success rate (0.0 to 1.0) */
  private final Map<Integer, Double> successRates;

  /**
   * Creates a new score calculator with preloaded success rates.
   *
   * @param successRateData list of [idGtNet, successRate] from database query
   */
  public SupplierScoreCalculator(List<Object[]> successRateData) {
    this.successRates = new HashMap<>();

    if (successRateData != null) {
      for (Object[] row : successRateData) {
        Integer idGtNet = ((Number) row[0]).intValue();
        Double rate = row[1] != null ? ((Number) row[1]).doubleValue() : DEFAULT_SUCCESS_RATE;
        successRates.put(idGtNet, rate);
      }
    }
  }

  /**
   * Calculates the score for a supplier.
   *
   * @param idGtNet the supplier ID
   * @param coverageCount the number of requested instruments this supplier supports
   * @return the score (coverageCount x successRate)
   */
  public double calculateScore(Integer idGtNet, int coverageCount) {
    double successRate = successRates.getOrDefault(idGtNet, DEFAULT_SUCCESS_RATE);
    return coverageCount * successRate;
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
   * Sorts AC_OPEN suppliers by score (descending), priority (ascending), with random shuffle for ties.
   *
   * @param suppliers list of suppliers to sort
   * @param exchangeKind the exchange kind for priority lookup
   * @param filter the instrument filter for coverage calculation
   * @param requestedInstrumentIds the set of instruments being requested
   * @return sorted list of suppliers
   */
  public List<GTNet> sortSuppliersByScore(
      List<GTNet> suppliers,
      GTNetExchangeKindType exchangeKind,
      SupplierInstrumentFilter filter,
      Set<Integer> requestedInstrumentIds) {

    if (suppliers == null || suppliers.size() <= 1) {
      return suppliers != null ? suppliers : new ArrayList<>();
    }

    // Calculate scores for each supplier
    List<ScoredSupplier> scored = new ArrayList<>();
    for (GTNet supplier : suppliers) {
      int coverage = calculateCoverage(supplier, filter, requestedInstrumentIds);
      double score = calculateScore(supplier.getIdGtNet(), coverage);
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

    // Group by (score, priority) and shuffle within groups
    Map<String, List<ScoredSupplier>> groups = new LinkedHashMap<>();
    for (ScoredSupplier ss : scored) {
      String key = ss.score + ":" + ss.priority;
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ss);
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
   * Calculates coverage count for a supplier based on how many requested instruments it supports.
   */
  private int calculateCoverage(GTNet supplier, SupplierInstrumentFilter filter, Set<Integer> requestedInstrumentIds) {
    if (filter == null || requestedInstrumentIds == null || requestedInstrumentIds.isEmpty()) {
      return 0;
    }
    // AC_OPEN: filter to only supported instruments (isPushOpen = false)
    Set<Integer> supported = filter.getInstrumentsForSupplier(supplier.getIdGtNet(), requestedInstrumentIds, false);
    return supported.size();
  }

  /**
   * Gets the consumerUsage priority value for a supplier and exchange kind.
   */
  private byte getConsumerUsage(GTNet supplier, GTNetExchangeKindType exchangeKind) {
    return supplier.getGtNetEntities().stream()
        .filter(e -> e.getEntityKind() == exchangeKind)
        .findFirst()
        .map(e -> e.getGtNetConfigEntity().getConsumerUsage())
        .orElse((byte) 0);
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
