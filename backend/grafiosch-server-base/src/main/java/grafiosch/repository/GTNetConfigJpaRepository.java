package grafiosch.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNetConfig;

/**
 * Repository for managing GTNetConfig entities.
 *
 * GTNetConfig stores the local configuration for remote GTNet connections, including authentication tokens exchanged
 * during the handshake process. Each GTNet entry has an optional 1:1 relationship with a GTNetConfig, which is created
 * after a successful handshake.
 */
public interface GTNetConfigJpaRepository extends GTNetConfigJpaRepositoryBase {

  /**
   * Finds all GTNetConfig entries where the handshake completed after the specified timestamp. Used by
   * GTNetFutureMessageDeliveryTask to find new partners who should receive pending messages.
   *
   * @param timestamp the timestamp after which handshakes should have occurred
   * @return list of configs with handshake after the given timestamp
   */
  List<GTNetConfig> findByHandshakeTimestampAfter(LocalDateTime timestamp);

  /**
   * Charges one incoming request of the given remote against its daily budget, in a single atomic statement so that
   * concurrent requests of the same peer cannot overspend it.
   *
   * The statement raises <code>daily_req_limit_count</code> only while the budget still has room, which is why the
   * number of affected rows is the answer to "may this request be served": it is 1 when the request was charged and 0
   * when the remote has used up its allowance for the day. Because the guard is part of the same UPDATE, no
   * check-then-act window exists.
   *
   * The counters belong to the UTC day held in <code>daily_req_limit_date</code>. When that date is not the day passed
   * in, both counters roll over — the charged one restarts at 1, the opposite one is cleared — so no scheduled reset
   * job is needed and a downtime over midnight cannot leave a stale budget behind.
   *
   * Named query: GTNetConfig.chargeIncomingRequest Parameters in SQL: - ?1 idGtNet - the remote's configuration row
   * (must exist) - ?2 today - the current UTC day - ?3 dailyRequestLimit - requests this server grants the remote per
   * day; pass Integer.MAX_VALUE for unlimited
   *
   * @param idGtNet           the remote GTNet entry whose configuration row is charged
   * @param today             the current UTC day
   * @param dailyRequestLimit the budget to enforce, Integer.MAX_VALUE when the local entry has no limit
   * @return 1 when the request was charged, 0 when the budget is exhausted or no configuration row exists
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int chargeIncomingRequest(Integer idGtNet, LocalDate today, int dailyRequestLimit);

  /**
   * Charges one outgoing request to the given remote against the budget that remote published to us, so this server
   * self-limits before running into the remote's refusal. Mirror image of
   * {@link #chargeIncomingRequest(Integer, LocalDate, int)}, raising <code>daily_req_limit_remote_count</code> and
   * applying the same day rollover.
   *
   * Named query: GTNetConfig.chargeOutgoingRequest Parameters in SQL: - ?1 idGtNet - the remote's configuration row
   * (must exist) - ?2 today - the current UTC day - ?3 dailyRequestLimit - requests the remote grants us per day; pass
   * Integer.MAX_VALUE for unlimited
   *
   * @param idGtNet           the remote GTNet entry whose configuration row is charged
   * @param today             the current UTC day
   * @param dailyRequestLimit the remote's published limit, Integer.MAX_VALUE when it publishes none
   * @return 1 when the request was charged, 0 when the budget is exhausted or no configuration row exists
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int chargeOutgoingRequest(Integer idGtNet, LocalDate today, int dailyRequestLimit);

  /**
   * Reads back the incoming request count that belongs to the given UTC day, yielding 0 when the stored counters belong
   * to an earlier day. Used to refresh the in-memory entity after a charge so that the auto-answer rule engine
   * evaluates its <code>dailyCount</code> variable against the request currently being processed.
   *
   * Named query: GTNetConfig.findChargedIncomingCount
   *
   * @param idGtNet the remote GTNet entry
   * @param today   the current UTC day
   * @return the count for that day, 0 when the stored counters belong to another day, null when no row exists
   */
  @Query(nativeQuery = true)
  Integer findChargedIncomingCount(Integer idGtNet, LocalDate today);
}
