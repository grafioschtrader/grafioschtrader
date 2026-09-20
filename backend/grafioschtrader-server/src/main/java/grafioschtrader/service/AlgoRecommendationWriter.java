package grafioschtrader.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;

/**
 * Stores the current rebalancing plan of one AlgoTop.
 *
 * <p>
 * A collaborator of its own rather than a method on {@link AlgoRebalancingService}, for the same reason
 * {@link AlgoAlarmRecorder} is one: the replacement has to be a transaction, and a call the service makes on itself
 * never passes the proxy that would open it. Removing the previous plan and writing the new one in two separate
 * transactions would leave an AlgoTop without any plan if the second one failed.
 * </p>
 */
@Service
public class AlgoRecommendationWriter {

  private final AlgoRecommendationJpaRepository repository;

  public AlgoRecommendationWriter(AlgoRecommendationJpaRepository repository) {
    this.repository = repository;
  }

  /**
   * Replaces the stored plan of one AlgoTop. A node that disappeared from the hierarchy between two evaluations leaves
   * no line behind, because the previous plan is removed rather than merged into.
   *
   * @param idTenant  tenant the plan was calculated for
   * @param idAlgoTop the AlgoTop whose plan is replaced
   * @param rows      the lines of the new plan
   */
  @Transactional
  public void replace(Integer idTenant, Integer idAlgoTop, List<AlgoRecommendation> rows) {
    repository.deleteByIdTenantAndIdAlgoTop(idTenant, idAlgoTop);
    repository.saveAll(rows);
  }
}
