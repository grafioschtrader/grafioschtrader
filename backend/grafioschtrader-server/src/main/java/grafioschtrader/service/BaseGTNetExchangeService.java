package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.entities.GTNet;
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
              .filter(e -> e.getEntityKind() == exchangeKind)
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
}
