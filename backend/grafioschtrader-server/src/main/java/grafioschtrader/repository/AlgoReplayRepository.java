package grafioschtrader.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Tenant;

/** Bulk writes a historical replay needs before it starts, none of which belongs to a single entity repository. */
public interface AlgoReplayRepository extends Repository<Tenant, Integer> {

  /**
   * Breaks the circular references before removing transfers and action applications. Opening transactions never carry
   * these references and remain untouched.
   *
   * @param idTenant the simulation environment
   * @return number of updated transactions
   */
  @Transactional
  @Modifying
  @Query("UPDATE Transaction t SET t.idSecurityTransfer = null, t.idSecurityActionApp = null"
      + " WHERE t.idTenant = ?1 AND t.simulationOpening = false")
  int clearGeneratedTransactionReferences(Integer idTenant);

  /** Removes the environment's transfers after their incoming transaction references have been cleared. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SecurityTransfer t WHERE t.idTenant = ?1")
  int deleteSecurityTransfers(Integer idTenant);

  /** Removes tenant applications, including those without SELL/BUY pairs, while keeping shared security actions. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SecurityActionApplication a WHERE a.idTenant = ?1")
  int deleteSecurityActionApplications(Integer idTenant);

  /**
   * Removes replay-generated and user-entered transactions, leaving the opening ledger untouched. This is what makes a
   * repeat run start from the recorded opening state rather than append a second replay to the first one.
   *
   * <p>
   * The holdings tables are not maintained by this delete. The caller rebuilds them from the remaining transactions
   * afterwards, the same way the creation of an environment does, which is also why the delete may be a bulk statement
   * rather than a walk through the transaction write path.
   * </p>
   *
   * @param idTenant the simulation environment
   * @return number of removed transactions
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM Transaction t WHERE t.idTenant = ?1 AND t.simulationOpening = false")
  int deleteGeneratedTransactions(Integer idTenant);

  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoEventLog e WHERE e.idTenant = ?1")
  int deleteEvents(Integer idTenant);
}
