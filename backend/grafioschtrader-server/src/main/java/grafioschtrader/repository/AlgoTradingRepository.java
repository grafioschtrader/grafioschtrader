package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;

import grafioschtrader.entities.*;
import jakarta.persistence.LockModeType;

/** Tenant serialization and bounded dated market reads for the strategy modules and the replay. */
public interface AlgoTradingRepository extends Repository<Tenant, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tenant t WHERE t.idTenant = ?1")
  Tenant lockTenant(Integer tenant);

  /**
   * Reads a tenant without taking a lock, for the callers that only have to establish who owns an environment. A
   * pessimistic lock requires an active transaction, which a read-only request path does not open; using
   * {@link #lockTenant(Integer)} there fails with {@code No active transaction} instead of answering the question.
   *
   * @param tenant the tenant to read
   * @return the tenant, or {@code null} when no such tenant exists
   */
  @Query("SELECT t FROM Tenant t WHERE t.idTenant = ?1")
  Tenant findTenant(Integer tenant);

  @Query("SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.date <= ?2 ORDER BY h.date DESC")
  List<Historyquote> history(Integer security, LocalDate through, Limit limit);

  @Query("SELECT t FROM Transaction t WHERE t.idTenant = ?1 AND t.algoFillId = ?2")
  java.util.Optional<Transaction> fill(Integer tenant, String fillId);

  @Query("SELECT COALESCE(SUM(ABS(t.units)), 0) FROM Transaction t WHERE t.idTenant = ?1 AND t.algoSignalId = ?2")
  double filledUnits(Integer tenant, String signalId);

  @Modifying
  @Query("UPDATE Transaction t SET t.idAlgoStrategy = ?3 WHERE t.idTenant = ?1 AND t.connectedIdTransaction = ?2 AND t.transactionType IN (4, 5)")
  void assignMarginCloses(Integer tenant, Integer opening, Integer strategy);

  /** Strategy deletion leaves portfolio trades intact and removes their obsolete assignment, including simulations. */
  @org.springframework.transaction.annotation.Transactional
  @Modifying
  @Query(nativeQuery = true)
  void clearRemovedAssignments(Integer owner);
}
