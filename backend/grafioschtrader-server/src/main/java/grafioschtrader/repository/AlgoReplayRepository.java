package grafioschtrader.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Tenant;

/** Bulk writes a historical replay needs before it starts, none of which belongs to a single entity repository. */
public interface AlgoReplayRepository extends Repository<Tenant, Integer> {

  /**
   * Removes everything a previous replay generated, leaving the opening ledger of the environment untouched. This is
   * what makes a repeat run start from the recorded opening state rather than append a second replay to the first one.
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
  @Query("DELETE FROM AlgoRecommendation r WHERE r.idTenant = ?1")
  int deleteRecommendations(Integer idTenant);

  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoEventLog e WHERE e.idTenant = ?1")
  int deleteEvents(Integer idTenant);
}
