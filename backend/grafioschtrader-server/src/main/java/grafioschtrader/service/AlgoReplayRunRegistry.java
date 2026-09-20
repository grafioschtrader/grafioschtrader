package grafioschtrader.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Which simulation environments are replaying in this server right now, and which of them the user has asked to stop.
 *
 * <p>
 * Keyed by the environment rather than by the run, because an environment has exactly one run row and because the
 * environment is what everything else asks about: the tenant of a request is known long before a run id exists, so a
 * reservation can be taken before the run is recorded, and "may this environment be entered?" is then answered without
 * touching the database - a question every request carrying an environment token has to ask.
 * </p>
 *
 * <p>
 * Deliberately in memory. A cancellation only means anything while the worker that would honour it is alive: if the
 * server stops, the run stops with it and its left-over row is reconciled as interrupted when it is next read, so
 * persisting the request would describe a job that no longer exists. Progress is written to the run row as well,
 * because that is what a client polls; this registry is what the worker itself reads between two days.
 * </p>
 */
@Component
public class AlgoReplayRunRegistry {

  private final Map<Integer, AtomicBoolean> reservations = new ConcurrentHashMap<>();

  /**
   * The environment the current thread is replaying, if it is a replay worker.
   *
   * <p>
   * The worker adopts the security context of whoever submitted the run, and a run may be started from inside an idle
   * environment, so its own writes are indistinguishable from an interactive request by their tenant alone. The thread
   * is what tells them apart. A caller-supplied exemption would be forgeable; this one cannot leave the worker.
   * </p>
   */
  private static final ThreadLocal<Integer> WORKER_ENVIRONMENT = new ThreadLocal<>();

  /**
   * Claims an environment for a replay that is about to be recorded.
   *
   * <p>
   * Taken before the run row is written, so that no reader can ever observe a recorded run that is not yet reserved -
   * which would look exactly like a run whose worker has died.
   * </p>
   *
   * @param idTenant the simulation environment
   * @return false when the environment is already reserved, which is how a duplicate submit is refused
   */
  public boolean reserve(Integer idTenant) {
    return reservations.putIfAbsent(idTenant, new AtomicBoolean()) == null;
  }

  /**
   * Gives an environment back. Called only after the final state of the run has been committed and the worker has
   * stopped, because until then the environment must stay unavailable.
   *
   * @param idTenant the simulation environment
   */
  public void release(Integer idTenant) {
    reservations.remove(idTenant);
  }

  /**
   * @param idTenant the simulation environment
   * @return true while a replay of this environment is queued or executing in this server
   */
  public boolean isReserved(Integer idTenant) {
    return reservations.containsKey(idTenant);
  }

  /**
   * Requests the cancellation of a replay. The reservation is kept: the environment becomes available again only once
   * the worker has actually stopped.
   *
   * @param idTenant the simulation environment
   * @return false when no replay of this environment is executing in this server, so there is nothing to interrupt
   */
  public boolean cancel(Integer idTenant) {
    AtomicBoolean flag = reservations.get(idTenant);
    if (flag == null) {
      return false;
    }
    flag.set(true);
    return true;
  }

  /**
   * @param idTenant the simulation environment
   * @return true when the user has asked the running replay to stop
   */
  public boolean isCancelled(Integer idTenant) {
    AtomicBoolean flag = reservations.get(idTenant);
    return flag != null && flag.get();
  }

  /** @return how many replays are executing in this server */
  public int runningCount() {
    return reservations.size();
  }

  /**
   * Marks the current thread as the replay worker of an environment, for the duration of one run.
   *
   * @param idTenant the simulation environment being replayed
   */
  public void beginWorker(Integer idTenant) {
    WORKER_ENVIRONMENT.set(idTenant);
  }

  /** Clears the worker marker of the current thread. */
  public void endWorker() {
    WORKER_ENVIRONMENT.remove();
  }

  /**
   * Tells the replay's own writes apart from an interactive request into the same environment, so that the guards which
   * keep users out of an active environment do not lock the replay out of it.
   *
   * @param idTenant the simulation environment a write is aimed at
   * @return true when the current thread is the replay worker of exactly that environment
   */
  public boolean isWorkerThreadFor(Integer idTenant) {
    return idTenant != null && idTenant.equals(WORKER_ENVIRONMENT.get());
  }
}
