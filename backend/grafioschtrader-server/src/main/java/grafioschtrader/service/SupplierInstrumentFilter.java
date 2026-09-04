package grafioschtrader.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.entities.GTNetSupplierDetail;

/**
 * Filters instruments based on GTNetSupplierDetail configuration.
 *
 * This class provides instrument filtering for GTNet exchange services, implementing the following rules:
 * <ul>
 *   <li>AC_PUSH_OPEN suppliers: receive ALL instruments (no filtering needed)</li>
 *   <li>AC_OPEN suppliers: receive ONLY instruments with matching GTNetSupplierDetail entries</li>
 * </ul>
 *
 * The filter is built once from a list of GTNetSupplierDetail entries and then used for O(1) lookups during the
 * exchange process, avoiding repeated database queries.
 *
 * <p>
 * For the historical price exchange the filter optionally carries the OHL quality percentage each supplier reported per
 * instrument, so that {@link SupplierScoreCalculator} can prefer peers delivering complete open/high/low data over
 * close-only peers. The percentages come from a separate query because they live in the application-side child table
 * gt_net_supplier_detail_hist; when they are not supplied the filter behaves exactly as before and every average is
 * unknown.
 * </p>
 */
public class SupplierInstrumentFilter {

  /** Map: supplierId -> Set of supported instrumentIds */
  private final Map<Integer, Set<Integer>> supplierToInstruments;

  /** Map: supplierId -> (instrumentId -> OHL percentage 0..100). Empty when no OHL data was loaded. */
  private final Map<Integer, Map<Integer, Double>> supplierToOhl;

  /**
   * Creates a new filter from a list of GTNetSupplierDetail entries, without OHL quality data.
   *
   * @param details the list of supplier details to build the filter from
   */
  public SupplierInstrumentFilter(List<GTNetSupplierDetail> details) {
    this(details, null);
  }

  /**
   * Creates a new filter from a list of GTNetSupplierDetail entries including the reported OHL quality per instrument.
   *
   * @param details the list of supplier details to build the filter from
   * @param ohlRows rows of [idGtNet, idEntity, ohlPercentage] as returned by
   *                GTNetSupplierDetailHistJpaRepository.findOhlPercentagesByEntityKindAndInstrumentIds, or null when
   *                OHL preference is not wanted
   */
  public SupplierInstrumentFilter(List<GTNetSupplierDetail> details, List<Object[]> ohlRows) {
    this.supplierToInstruments = new HashMap<>();
    this.supplierToOhl = new HashMap<>();

    if (details != null) {
      for (GTNetSupplierDetail detail : details) {
        Integer supplierId = detail.getIdGtNet();
        Integer instrumentId = detail.getIdEntity();

        if (supplierId != null && instrumentId != null) {
          supplierToInstruments.computeIfAbsent(supplierId, _ -> new HashSet<>()).add(instrumentId);
        }
      }
    }

    if (ohlRows != null) {
      for (Object[] row : ohlRows) {
        if (row[0] == null || row[1] == null || row[2] == null) {
          continue;
        }
        Integer supplierId = ((Number) row[0]).intValue();
        Integer instrumentId = ((Number) row[1]).intValue();
        Double ohl = ((Number) row[2]).doubleValue();
        supplierToOhl.computeIfAbsent(supplierId, _ -> new HashMap<>()).put(instrumentId, ohl);
      }
    }
  }

  /**
   * Returns the set of instrument IDs that should be sent to the given supplier.
   *
   * For AC_PUSH_OPEN suppliers (isPushOpen=true), returns all requested instruments. For AC_OPEN suppliers
   * (isPushOpen=false), returns only instruments with matching GTNetSupplierDetail entries.
   *
   * @param supplierId the ID of the supplier
   * @param requestedInstrumentIds the set of instrument IDs being requested
   * @param isPushOpen true if the supplier is AC_PUSH_OPEN, false for AC_OPEN
   * @return the filtered set of instrument IDs to send to this supplier
   */
  public Set<Integer> getInstrumentsForSupplier(Integer supplierId, Set<Integer> requestedInstrumentIds,
      boolean isPushOpen) {
    if (isPushOpen) {
      // AC_PUSH_OPEN: return all requested instruments - they maintain active connections
      return requestedInstrumentIds;
    }

    // AC_OPEN: filter to only supported instruments
    Set<Integer> supported = supplierToInstruments.get(supplierId);
    if (supported == null || supported.isEmpty()) {
      // No GTNetSupplierDetail entries for this supplier - skip it entirely
      return Collections.emptySet();
    }

    return requestedInstrumentIds.stream().filter(supported::contains).collect(Collectors.toSet());
  }

  /**
   * Checks if a supplier has any known instrument support.
   *
   * @param supplierId the ID of the supplier
   * @return true if the supplier has at least one GTNetSupplierDetail entry
   */
  public boolean hasSupplierDetails(Integer supplierId) {
    Set<Integer> supported = supplierToInstruments.get(supplierId);
    return supported != null && !supported.isEmpty();
  }

  /**
   * Returns the average OHL percentage this supplier reported for the given instruments.
   *
   * Only instruments the supplier actually reported a percentage for contribute to the average, so a peer is never
   * penalised for instruments whose quality is simply unknown. Returns null when nothing is known at all - the caller
   * must treat that as neutral rather than as zero.
   *
   * @param supplierId    the ID of the supplier
   * @param instrumentIds the instruments to average over
   * @return the average percentage in the range 0..100, or null when no percentage is known
   */
  public Double getAverageOhl(Integer supplierId, Set<Integer> instrumentIds) {
    Map<Integer, Double> byInstrument = supplierToOhl.get(supplierId);
    if (byInstrument == null || byInstrument.isEmpty() || instrumentIds == null || instrumentIds.isEmpty()) {
      return null;
    }

    double sum = 0.0;
    int count = 0;
    for (Integer instrumentId : instrumentIds) {
      Double ohl = byInstrument.get(instrumentId);
      if (ohl != null) {
        sum += ohl;
        count++;
      }
    }

    return count == 0 ? null : sum / count;
  }
}
