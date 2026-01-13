package grafioschtrader.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import grafioschtrader.entities.GTNetSupplierDetail;

/**
 * Filters instruments based on GTNetSupplierDetail configuration.
 *
 * This class provides instrument filtering for GTNet exchange services, implementing the following rules:
 * <ul>
 *   <li>AC_PUSH_OPEN suppliers: receive ALL instruments (no filtering needed)</li>
 *   <li>AC_OPEN suppliers: receive ONLY instruments with matching GTNetSupplierDetail entries</li>
 * </ul>
 *
 * The filter is built once from a list of GTNetSupplierDetail entries and then used for O(1) lookups
 * during the exchange process, avoiding repeated database queries.
 */
public class SupplierInstrumentFilter {

  /** Map: supplierId -> Set of supported instrumentIds */
  private final Map<Integer, Set<Integer>> supplierToInstruments;

  /**
   * Creates a new filter from a list of GTNetSupplierDetail entries.
   *
   * @param details the list of supplier details to build the filter from
   */
  public SupplierInstrumentFilter(List<GTNetSupplierDetail> details) {
    this.supplierToInstruments = new HashMap<>();

    if (details != null) {
      for (GTNetSupplierDetail detail : details) {
        Integer supplierId = detail.getIdGtNet();
        Integer instrumentId = detail.getSecuritycurrency() != null
            ? detail.getSecuritycurrency().getIdSecuritycurrency()
            : null;

        if (supplierId != null && instrumentId != null) {
          supplierToInstruments.computeIfAbsent(supplierId, k -> new HashSet<>()).add(instrumentId);
        }
      }
    }
  }

  /**
   * Returns the set of instrument IDs that should be sent to the given supplier.
   *
   * For AC_PUSH_OPEN suppliers (isPushOpen=true), returns all requested instruments.
   * For AC_OPEN suppliers (isPushOpen=false), returns only instruments with matching GTNetSupplierDetail entries.
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

    return requestedInstrumentIds.stream()
        .filter(supported::contains)
        .collect(Collectors.toSet());
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
}
