package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.AlgoMessageAlert;

public interface AlgoMessageAlertJpaRepository extends JpaRepository<AlgoMessageAlert, Integer> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AlgoMessageAlert a WHERE a.idAlgoMessageAlert = ?1")
  Optional<AlgoMessageAlert> lockDelivery(Integer id);

  @Query("SELECT a.idAlgoMessageAlert FROM AlgoMessageAlert a WHERE a.deliveryStatus IN ('PENDING', 'RETRY', 'SENDING') AND (a.nextAttemptAt IS NULL OR a.nextAttemptAt <= ?1) AND (a.deliveryLeaseUntil IS NULL OR a.deliveryLeaseUntil <= ?1) ORDER BY a.idAlgoMessageAlert")
  List<Integer> dueDeliveries(java.time.LocalDateTime now, org.springframework.data.domain.Pageable page);

  org.springframework.data.domain.Page<AlgoMessageAlert> findByIdTenant(Integer tenant,
      org.springframework.data.domain.Pageable page);

  org.springframework.data.domain.Page<AlgoMessageAlert> findByIdTenantAndDeliveryStatus(Integer tenant, String status,
      org.springframework.data.domain.Pageable page);

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
}
