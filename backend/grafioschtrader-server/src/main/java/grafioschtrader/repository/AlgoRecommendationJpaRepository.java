package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoRecommendation;

/**
 * Access to the current rebalancing plans. The rows are written by the evaluation and read by the report and by the
 * periodic due check; there is no create or update endpoint, which is why nothing here is exposed over REST.
 */
public interface AlgoRecommendationJpaRepository extends JpaRepository<AlgoRecommendation, Integer> {

  /**
   * The current plan of one AlgoTop in the tenant it was calculated for. Ordered so that the top level line comes
   * first, then the buckets, then the instruments, which is the order the report renders them in.
   *
   * @param idTenant  tenant whose positions the plan was calculated against
   * @param idAlgoTop the AlgoTop the plan belongs to
   * @return the lines of the current plan, empty when the AlgoTop has never been evaluated
   */
  @Query("SELECT r FROM AlgoRecommendation r WHERE r.idTenant = ?1 AND r.idAlgoTop = ?2 AND r.triggerKind <> grafioschtrader.types.AlgoRebalancingTrigger.MEAN_REVERSION ORDER BY r.levelType, r.idNode")
  List<AlgoRecommendation> findByIdTenantAndIdAlgoTopOrderByLevelTypeAscIdNodeAsc(Integer idTenant, Integer idAlgoTop);

  List<AlgoRecommendation> findByIdTenantAndTriggerKind(Integer tenant,
      grafioschtrader.types.AlgoRebalancingTrigger kind);

  java.util.Optional<AlgoRecommendation> findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyAndTriggerKind(
      Integer tenant, Integer strategy, Integer security, grafioschtrader.types.AlgoRebalancingTrigger kind);

  /**
   * The day the current plan of one AlgoTop was calculated. This decides whether the daily evaluation still has work to
   * do; it says nothing about the periodic interval, because the plan is replaced on every evaluation.
   *
   * @param idTenant  tenant whose positions the plan was calculated against
   * @param idAlgoTop the AlgoTop the plan belongs to
   * @return the run day of the stored plan, or empty when there is none
   */
  @Query("SELECT MAX(r.runDate) FROM AlgoRecommendation r WHERE r.idTenant = ?1 AND r.idAlgoTop = ?2 AND r.triggerKind <> grafioschtrader.types.AlgoRebalancingTrigger.MEAN_REVERSION")
  Optional<LocalDate> findLastRunDate(Integer idTenant, Integer idAlgoTop);

  /**
   * The valuation day of the last periodic checkpoint of one AlgoTop. The interval to the next checkpoint counts from
   * this day. Every plan carries it over from the plan it replaces, so it survives the daily replacement of the rows.
   *
   * @param idTenant  tenant whose positions the plan was calculated against
   * @param idAlgoTop the AlgoTop the plan belongs to
   * @return the last checkpoint day, or empty when no checkpoint has been recorded
   */
  @Query("SELECT MAX(r.checkpointDate) FROM AlgoRecommendation r WHERE r.idTenant = ?1 AND r.idAlgoTop = ?2 AND r.triggerKind <> grafioschtrader.types.AlgoRebalancingTrigger.MEAN_REVERSION")
  Optional<LocalDate> findLastCheckpointDate(Integer idTenant, Integer idAlgoTop);

  /**
   * Removes the previous plan of one AlgoTop, so that the rows written afterwards are the whole current plan rather
   * than a mixture of two evaluations. A node that disappeared from the hierarchy between two runs leaves no orphan
   * line behind this way.
   *
   * @param idTenant  tenant whose plan is replaced
   * @param idAlgoTop the AlgoTop whose plan is replaced
   * @return number of removed lines
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM AlgoRecommendation r WHERE r.idTenant = ?1 AND r.idAlgoTop = ?2 AND r.triggerKind <> grafioschtrader.types.AlgoRebalancingTrigger.MEAN_REVERSION")
  int deleteByIdTenantAndIdAlgoTop(Integer idTenant, Integer idAlgoTop);

}
