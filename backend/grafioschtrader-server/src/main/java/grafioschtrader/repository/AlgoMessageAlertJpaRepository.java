package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.AlgoMessageAlert;

public interface AlgoMessageAlertJpaRepository extends JpaRepository<AlgoMessageAlert, Integer> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AlgoMessageAlert a WHERE a.idAlgoMessageAlert = ?1")
  Optional<AlgoMessageAlert> lockDelivery(Integer id);

  @Query("SELECT a.idAlgoMessageAlert FROM AlgoMessageAlert a WHERE a.deliveryStatus IN ('PENDING', 'RETRY', 'SENDING') AND (a.nextAttemptAt IS NULL OR a.nextAttemptAt <= ?1) AND (a.deliveryLeaseUntil IS NULL OR a.deliveryLeaseUntil <= ?1) ORDER BY a.idAlgoMessageAlert")
  List<Integer> dueDeliveries(java.time.LocalDateTime now, org.springframework.data.domain.Pageable page);

  /**
   * All recorded notifications of a tenant, newest first. Feeds the notification table of the alert diagnostics, which
   * pages and filters on the client.
   *
   * @param idTenant the tenant whose alerts are evaluated (the home tenant, never a simulation copy)
   * @return the notifications ordered by descending id, which is the order in which they were recorded
   */
  List<AlgoMessageAlert> findByIdTenantOrderByIdAlgoMessageAlertDesc(Integer idTenant);

  /**
   * Named query inserts one daily signal; duplicate identities leave the original delivery untouched.
   *
   * <p>
   * The tranche is part of the identity, so two profit taking tranches of one instrument are two alarms on the same
   * day. Every signal that is not a tranche passes {@link AlgoMessageAlert#NO_TRANCHE}.
   * </p>
   */
  @org.springframework.data.jpa.repository.Modifying
  @Query(nativeQuery = true)
  void recordSignal(Integer tenant, Integer strategy, Integer security, byte kind, byte direction, String trancheKey,
      String details, LocalDate day, java.time.LocalDateTime time, String context, String securityName);

  List<AlgoMessageAlert> findByIdTenantAndIdAlgoStrategy(Integer idTenant, Integer idAlgoStrategy);

  /** Keeps alert history while retiring every unfinished delivery of an ineligible strategy. */
  @org.springframework.data.jpa.repository.Modifying
  @Query
  int cancelPendingForStrategy(Integer tenant, Integer strategy);

  /**
   * The alarm carrying one exact signal identity on one day, if it exists. Used when the unique key has rejected a
   * fresh claim: the signal was already raised, and the question is only whether its notification went out. Reusing the
   * recorded alarm is what keeps a mail server that was briefly unreachable from costing a lost message rather than a
   * delayed one.
   *
   * <p>
   * The seven arguments are exactly the unique key UK_AlarmDay, so at most one row can match.
   * </p>
   */
  @Query("""
      SELECT a FROM AlgoMessageAlert a WHERE a.idTenant = ?1 AND a.idAlgoStrategy = ?2 AND a.idSecurityCurrency = ?3
      AND a.alarmType = ?4 AND a.signalDirection = ?5 AND a.trancheKey = ?6 AND a.alertDay = ?7""")
  Optional<AlgoMessageAlert> findBySignalIdentity(Integer idTenant, Integer idAlgoStrategy, Integer idSecurityCurrency,
      byte alarmType, byte signalDirection, String trancheKey, LocalDate alertDay);

  /**
   * Deletes notifications the user has selected, but only those that may go: finished deliveries (DELIVERED, CANCELLED,
   * FAILED, REVIEW_REQUIRED) whose alert day lies before the protected window. The caller compares the count with the
   * number of requested ids to detect a selection that contained a protected notification.
   *
   * Named query: AlgoMessageAlert.deleteFinishedByIds
   *
   * @param idTenant      the home tenant of the user; ids of another tenant are never deleted
   * @param ids           the selected notifications
   * @param protectedFrom first alert day of the protected window, exclusive bound of the deletable days
   * @return the number of deleted notifications
   */
  @Modifying
  @Query
  int deleteFinishedByIds(Integer idTenant, Collection<Integer> ids, LocalDate protectedFrom);

  /**
   * Deletes the finished notifications of every tenant whose alert day lies before the given day; the age limit of the
   * retention.
   *
   * Named query: AlgoMessageAlert.purgeFinishedBefore
   *
   * @param before exclusive upper bound of the alert day; never later than the start of the protected window
   * @return the number of deleted notifications
   */
  @Modifying
  @Query
  int purgeFinishedBefore(LocalDate before);

  /**
   * Keeps the newest notifications of each tenant, ranked by id in the order they were recorded, and deletes the
   * finished ones beyond that rank. A notification inside the protected window or still being delivered survives even
   * when it is beyond the rank, so a tenant may temporarily hold more than the limit.
   *
   * Named query: AlgoMessageAlert.purgeFinishedBeyondMaxRecords
   *
   * @param maxRecords    number of the newest notifications each tenant keeps
   * @param protectedFrom first alert day of the protected window
   * @return the number of deleted notifications
   */
  @Modifying
  @Query(nativeQuery = true)
  int purgeFinishedBeyondMaxRecords(int maxRecords, LocalDate protectedFrom);

  /**
   * Deletes the notifications of strategies that no longer exist. The table has no foreign key to the strategy, so they
   * would otherwise stay forever; without its strategy such a notification can neither be delivered nor raised again.
   *
   * Named query: AlgoMessageAlert.purgeOfDeletedStrategies
   *
   * @return the number of deleted notifications
   */
  @Modifying
  @Query(nativeQuery = true)
  int purgeOfDeletedStrategies();
}
