package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.entities.GTNet;
import grafioschtrader.gtnet.GTNetExchangeKindType;

/**
 * Abstract base class for GTNet exchange services (lastprice and historyquote).
 *
 * Provides shared functionality for:
 * <ul>
 *   <li>Supplier priority ordering with randomization</li>
 *   <li>Common exchange patterns</li>
 * </ul>
 *
 * @see GTNetLastpriceService for intraday price exchange
 * @see GTNetHistoryquoteService for historical price exchange
 */
public abstract class BaseGTNetExchangeService {

  /**
   * Randomizes suppliers within the same priority level.
   * Suppliers are grouped by their consumerUsage priority value, sorted by priority ascending,
   * and randomized within each group to balance load across suppliers at the same priority level.
   *
   * @param suppliers list of suppliers to organize
   * @param exchangeKind the exchange kind to determine which entity's consumerUsage to use
   * @return list of suppliers randomized within priority groups
   */
  protected List<GTNet> getSuppliersByPriorityWithRandomization(List<GTNet> suppliers,
      GTNetExchangeKindType exchangeKind) {
    if (suppliers == null || suppliers.size() <= 1) {
      return suppliers != null ? suppliers : new ArrayList<>();
    }

    // Group by priority (consumerUsage value from GTNetConfigEntity)
    Map<Byte, List<GTNet>> byPriority = suppliers.stream()
        .collect(Collectors.groupingBy(gtNet -> {
          return gtNet.getGtNetEntities().stream()
              .filter(e -> e.getEntityKindValue() == exchangeKind.getValue())
              .findFirst()
              .map(e -> e.getGtNetConfigEntity().getConsumerUsage())
              .orElse((byte) 0);
        }));

    // Shuffle within each priority group and flatten
    List<GTNet> result = new ArrayList<>();
    byPriority.keySet().stream()
        .sorted()
        .forEach(priority -> {
          List<GTNet> group = byPriority.get(priority);
          Collections.shuffle(group);
          result.addAll(group);
        });

    return result;
  }

  /**
   * Sorts AC_OPEN suppliers using optimized scoring algorithm: coverage x success_rate as primary,
   * priority as secondary, and random shuffle as tertiary for ties.
   *
   * This method provides better supplier selection than priority-only by considering:
   * - How many of the requested instruments each supplier actually supports (coverage)
   * - How reliable each supplier has been historically (success rate)
   *
   * Note: This method is ONLY for AC_OPEN suppliers. AC_PUSH_OPEN suppliers should continue
   * using getSuppliersByPriorityWithRandomization().
   *
   * @param suppliers list of AC_OPEN suppliers to organize
   * @param exchangeKind the exchange kind for priority lookup
   * @param scoreCalculator the pre-initialized score calculator with success rates
   * @param filter the instrument filter for coverage calculation
   * @param requestedInstrumentIds the set of instrument IDs being requested
   * @return list of suppliers sorted by score desc, priority asc, with random shuffle for ties
   */
  protected List<GTNet> getSuppliersByScoreWithRandomization(
      List<GTNet> suppliers,
      GTNetExchangeKindType exchangeKind,
      SupplierScoreCalculator scoreCalculator,
      SupplierInstrumentFilter filter,
      Set<Integer> requestedInstrumentIds) {

    if (scoreCalculator == null) {
      // Fallback to priority-only if no score calculator provided
      return getSuppliersByPriorityWithRandomization(suppliers, exchangeKind);
    }

    return scoreCalculator.sortSuppliersByScore(suppliers, exchangeKind, filter, requestedInstrumentIds);
  }
}
