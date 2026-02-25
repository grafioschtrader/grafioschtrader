package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.StandingOrderFailure;

/**
 * Repository for persisting and querying standing order execution failures.
 */
public interface StandingOrderFailureJpaRepository extends JpaRepository<StandingOrderFailure, Integer> {

  /**
   * Returns all failures for a given standing order, newest first.
   *
   * @param idStandingOrder the standing order to query
   * @return failures ordered by execution date descending
   */
  List<StandingOrderFailure> findByIdStandingOrderOrderByExecutionDateDesc(Integer idStandingOrder);

  /**
   * Returns failure counts grouped by standing order ID, for batch-loading into the transient
   * {@code failureCount} field on {@link grafioschtrader.entities.StandingOrder}.
   *
   * @param ids the standing order IDs to count failures for
   * @return list of [idStandingOrder, count] pairs
   */
  @Query("SELECT f.idStandingOrder, COUNT(f) FROM StandingOrderFailure f WHERE f.idStandingOrder IN :ids GROUP BY f.idStandingOrder")
  List<Object[]> countByStandingOrderIds(List<Integer> ids);
}
