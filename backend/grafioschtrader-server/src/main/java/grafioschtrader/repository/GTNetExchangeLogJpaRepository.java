package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetExchangeLog;

/**
 * Repository for managing GTNet exchange log entries.
 * Provides methods for querying, aggregating, and deleting log entries.
 */
public interface GTNetExchangeLogJpaRepository
    extends JpaRepository<GTNetExchangeLog, Integer>, GTNetExchangeLogJpaRepositoryCustom {

  /**
   * Finds all log entries for a specific GTNet domain, ordered by timestamp descending.
   *
   * @param idGtNet the GTNet identifier
   * @return list of log entries for the specified GTNet
   */
  List<GTNetExchangeLog> findByIdGtNetOrderByTimestampDesc(Integer idGtNet);

  /**
   * Finds all log entries for a specific GTNet domain and entity kind, ordered by timestamp descending.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind byte value
   * @return list of log entries for the specified GTNet and entity kind
   */
  @Query("SELECT g FROM GTNetExchangeLog g WHERE g.idGtNet = :idGtNet AND g.entityKind = :entityKind " +
         "ORDER BY g.timestamp DESC")
  List<GTNetExchangeLog> findByIdGtNetAndEntityKindOrderByTimestampDesc(
      @Param("idGtNet") Integer idGtNet, @Param("entityKind") byte entityKind);

  /**
   * Finds log entries by period type that started before a specific date.
   * Used by the aggregation job to find records ready for roll-up.
   *
   * @param periodType the period type byte value
   * @param beforeDate the cutoff date
   * @return list of log entries matching the criteria
   */
  @Query("SELECT g FROM GTNetExchangeLog g WHERE g.periodType = :periodType AND g.periodStart < :beforeDate " +
         "ORDER BY g.idGtNet, g.entityKind, g.logAsSupplier, g.periodStart")
  List<GTNetExchangeLog> findByPeriodTypeAndPeriodStartBefore(
      @Param("periodType") byte periodType, @Param("beforeDate") LocalDate beforeDate);

  /**
   * Deletes log entries by their IDs.
   *
   * @param ids list of log entry IDs to delete
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM GTNetExchangeLog g WHERE g.idGtNetExchangeLog IN :ids")
  void deleteByIds(@Param("ids") List<Integer> ids);

  /**
   * Finds all log entries for a specific GTNet, entity kind, and role.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind byte value
   * @param logAsSupplier true for supplier role, false for consumer
   * @return list of matching log entries ordered by period start descending
   */
  @Query("SELECT g FROM GTNetExchangeLog g WHERE g.idGtNet = :idGtNet AND g.entityKind = :entityKind " +
         "AND g.logAsSupplier = :logAsSupplier ORDER BY g.periodStart DESC, g.periodType ASC")
  List<GTNetExchangeLog> findByIdGtNetAndEntityKindAndRole(
      @Param("idGtNet") Integer idGtNet,
      @Param("entityKind") byte entityKind,
      @Param("logAsSupplier") boolean logAsSupplier);
}
