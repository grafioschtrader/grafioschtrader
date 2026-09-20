package grafioschtrader.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.repository.AlgoSimulationResultJpaRepository;
import grafioschtrader.types.AlgoSimulationRunStatus;

/**
 * Whether a simulation environment is busy replaying, which decides who may enter it and whether it may be deleted.
 *
 * <p>
 * The reservation held by {@link AlgoReplayRunRegistry} is the answer. It is taken before the run is recorded and given
 * back only after the worker has stopped and the final state has been committed, so it covers exactly the window §5.3
 * of the specification calls an active run - including a run that has been asked to cancel but whose worker is still
 * evaluating a day.
 * </p>
 *
 * <p>
 * A recorded run left in {@link AlgoSimulationRunStatus#RUNNING} with no reservation is not active: it means the server
 * stopped while it was executing. Nothing resumes such a run, so the environment is available again, and the row is
 * reconciled here rather than left to describe progress that will never advance. This follows the single-instance,
 * in-memory assumption the design rests on; a second server with its own registry would need a durable protocol.
 * </p>
 */
@Service
public class SimulationRunActivityService {

  @Autowired
  private AlgoReplayRunRegistry registry;

  @Autowired
  private AlgoSimulationResultJpaRepository results;

  /**
   * Reports whether a replay of this environment may still write to it.
   *
   * @param idSimTenant the simulation environment
   * @return true while a replay of it is queued or executing in this server
   */
  public boolean isActive(Integer idSimTenant) {
    if (registry.isReserved(idSimTenant)) {
      return true;
    }
    reconcileAbandonedRun(idSimTenant);
    return false;
  }

  /**
   * Marks a run whose worker no longer exists as interrupted, so that a left-over row cannot keep showing a replay in
   * progress forever.
   *
   * <p>
   * Runs in its own transaction: the callers are request-scoped guards that may hold no transaction at all, and the
   * ones that do must not have their own work joined to this repair.
   * </p>
   *
   * @param idSimTenant the simulation environment
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reconcileAbandonedRun(Integer idSimTenant) {
    AlgoSimulationResult run = results.findByIdTenant(idSimTenant).orElse(null);
    if (run == null || run.getStatus() != AlgoSimulationRunStatus.RUNNING) {
      return;
    }
    run.setStatus(AlgoSimulationRunStatus.INTERRUPTED);
    run.setFinishedAt(LocalDateTime.now());
    results.save(run);
  }

  /**
   * Tells the replay's own writes apart from an interactive request into the same environment.
   *
   * @param idSimTenant the simulation environment a write is aimed at
   * @return true when the current thread is the replay worker of exactly that environment
   */
  public boolean isReplayWorker(Integer idSimTenant) {
    return registry.isWorkerThreadFor(idSimTenant);
  }
}
