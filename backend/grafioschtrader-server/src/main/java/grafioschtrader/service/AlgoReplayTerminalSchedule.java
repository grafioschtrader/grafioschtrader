package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Expiry events for one replay, built from its immutable instrument life dates. Due events remain pending until their
 * positions have been closed; completed events never cause another ledger read. The pending order follows instrument
 * registration, preserving the replay's booking order even when several expiries are caught up on the same day.
 */
final class AlgoReplayTerminalSchedule {
  private final NavigableMap<LocalDate, List<Integer>> future = new TreeMap<>();
  private final Map<Integer, Integer> order = new HashMap<>();
  private final NavigableMap<Integer, Integer> pending = new TreeMap<>();

  /** Registers an instrument once, including instruments first encountered by a strategy during the run. */
  void register(Integer security, LocalDate expiry) {
    if (expiry == null || order.containsKey(security)) {
      return;
    }
    order.put(security, order.size());
    future.computeIfAbsent(expiry, _ -> new ArrayList<>()).add(security);
  }

  /** Catches up weekends and pre-opening expiries; unsuccessful events remain eligible on the next replay day. */
  List<Integer> due(LocalDate date) {
    while (!future.isEmpty() && !future.firstKey().isAfter(date)) {
      for (Integer security : future.pollFirstEntry().getValue()) {
        pending.put(order.get(security), security);
      }
    }
    return List.copyOf(pending.values());
  }

  /** Retires an empty position or an instrument whose terminal bookings all succeeded. */
  void complete(Integer security) {
    pending.remove(order.get(security));
  }
}
