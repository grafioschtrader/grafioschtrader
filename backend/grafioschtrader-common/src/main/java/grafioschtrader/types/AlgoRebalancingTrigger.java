package grafioschtrader.types;

/**
 * Why a rebalancing plan became actionable. Every trigger uses the same target calculation; they differ only in what
 * made the evaluation report rather than stay quiet.
 */
public enum AlgoRebalancingTrigger {

  /**
   * The configured checkpoint interval has elapsed and at least one executable allocation is outside its tolerance.
   * This is the only trigger a rebalancing is produced under: the interval decides whether the allocation is compared
   * at all, and the tolerance decides which of its lines are then traded.
   */
  PERIODIC,
  /**
   * No longer produced. A drift beyond the tolerance used to be a trigger of its own, evaluated on every day rather
   * than at the interval, which made a hierarchy rebalance daily and pushed its interval forward indefinitely. The
   * constant is retained because {@code algo_recommendation.trigger_kind} and {@code algo_event_log.rationale} hold it
   * in rows written before the checkpoint semantics.
   */
  DRIFT,
  /** The plan was calculated, but neither the interval nor the tolerance asked for an action. */
  NONE,
  /** The one-time purchase that brings an environment opening without positions to its configured targets. */
  INITIAL,
  /** Daily-close mean-reversion decision, including blocked and unavailable outcomes. */
  MEAN_REVERSION;

}
