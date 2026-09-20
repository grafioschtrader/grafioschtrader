package grafioschtrader.types;

/**
 * Lifecycle of one historical replay of a simulation environment.
 *
 * <p>
 * Only {@link #COMPLETED} carries metrics. Every other terminal state leaves the metric columns empty on purpose, so
 * that a partial distribution of fills can never be read as the result of a finished run.
 * </p>
 */
public enum AlgoSimulationRunStatus {

  /** The replay is queued or executing; the progress counters say how far it has come. */
  RUNNING,
  /** The replay reached its end date and its metrics are valid. */
  COMPLETED,
  /** The user cancelled the replay; the fills booked up to that point remain in the environment. */
  CANCELLED,
  /**
   * The replay stopped on an error, which is recorded in the failure message. Named apart from the plain FAILED of the
   * library, whose text is the one of a notification delivery and reads wrongly for a run.
   */
  RUN_FAILED,
  /**
   * The server stopped while the replay was running. Nothing resumes such a run: the environment has to be replayed
   * again, which restores the opening state first.
   */
  INTERRUPTED;

}
