package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.WatchlistIndicatorColumn;

/** Atomic bulk operations use the same tenant lock and limits as individual writes. */
public interface WatchlistIndicatorColumnJpaRepositoryCustom {
  List<WatchlistIndicatorColumn> createDefaults();

  List<WatchlistIndicatorColumn> reorder(List<Integer> ids);
}
