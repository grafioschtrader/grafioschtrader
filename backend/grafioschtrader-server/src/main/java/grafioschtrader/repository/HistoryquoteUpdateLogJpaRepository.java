package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.HistoryquoteUpdateLog;

/**
 * Repository for managing historical price update log entries. Provides methods to query
 * the update history per stock exchange and retrieve recent update records for monitoring.
 */
public interface HistoryquoteUpdateLogJpaRepository extends JpaRepository<HistoryquoteUpdateLog, Integer> {

  /**
   * Finds all update log entries for a specific stock exchange, ordered by timestamp descending.
   *
   * @param idStockexchange the stock exchange ID to filter by
   * @return list of update log entries for the specified exchange, most recent first
   */
  List<HistoryquoteUpdateLog> findByIdStockexchangeOrderByUpdateTimestampDesc(Integer idStockexchange);

  /**
   * Retrieves the most recent 100 update log entries across all exchanges.
   * Useful for monitoring overall system activity and recent update history.
   *
   * @return list of the 100 most recent update log entries, ordered by timestamp descending
   */
  List<HistoryquoteUpdateLog> findTop100ByOrderByUpdateTimestampDesc();
}
