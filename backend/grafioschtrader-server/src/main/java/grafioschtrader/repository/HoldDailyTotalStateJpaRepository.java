package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.HoldDailyTotalState;
import jakarta.persistence.LockModeType;

/**
 * Per tenant marker of {@code hold_daily_total}. Internal state of the task {@code HOLD_DAILY_TOTAL_UPDATE}; no REST
 * repository is exposed. None of the modifying methods opens a transaction of its own: each runs in the transaction of
 * its caller, so that a booking and the lowering of the marker commit or roll back together.
 */
public interface HoldDailyTotalStateJpaRepository extends JpaRepository<HoldDailyTotalState, Integer> {

  /**
   * Reads the marker of a tenant and locks it until the end of the transaction. The update task holds this lock while
   * it recomputes the tenant, so a booking that lowers the marker meanwhile waits and lowers the marker the task has
   * just written, instead of being overwritten by it.
   *
   * @param idTenant the tenant
   * @return the locked marker, empty when the tenant has never been computed
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM HoldDailyTotalState s WHERE s.idTenant = ?1")
  Optional<HoldDailyTotalState> lockByIdTenant(Integer idTenant);

  /**
   * Lowers the marker of a tenant to the given day, unless it already lies on or before it. A tenant without a marker
   * is left alone, it is computed in full anyway.
   *
   * @param idTenant the tenant
   * @param fromDate the first day whose rows a change has made outdated
   * @return 1 when the tenant has a marker, 0 when it has never been computed
   */
  @Modifying
  @Query(value = """
      UPDATE hold_daily_total_state SET recalc_from_date = LEAST(recalc_from_date, ?2) WHERE id_tenant = ?1""",
      nativeQuery = true)
  int lowerRecalcFromDate(Integer idTenant, LocalDate fromDate);

  /**
   * Removes the marker of a tenant, so that the next run computes its whole history again.
   *
   * @param idTenant the tenant
   * @return the number of removed rows, 0 or 1
   */
  @Modifying
  @Query(value = "DELETE FROM hold_daily_total_state WHERE id_tenant = ?1", nativeQuery = true)
  int removeByIdTenant(Integer idTenant);

  /** Removes the marker of every tenant, so that the next run computes all of them in full. */
  @Modifying
  @Query(value = "DELETE FROM hold_daily_total_state", nativeQuery = true)
  int removeAllStates();

  /**
   * The current time of the database. {@code historyquote.create_modify_time} is set by the database, so the time a
   * quote scan starts is taken from the same clock.
   *
   * @return the database time, in UTC because the sessions are pinned to UTC
   */
  @Query(value = "SELECT NOW()", nativeQuery = true)
  LocalDateTime getDatabaseNow();
}
