package grafioschtrader.types;

/**
 * What a rebalancing line asks the user to do. The names carry the {@code REBALANCE_} prefix because the frontend
 * translates the stored value itself: a bare {@code BUY} would collide with the transaction type texts, which describe
 * a booking that already happened rather than a proposal.
 *
 * <p>
 * The action is the direction of the trade, not the direction of the exposure. Reducing a short position is therefore
 * {@link #REBALANCE_BUY}, and the rationale of the line is what says whether the exposure grows or shrinks.
 * </p>
 */
public enum AlgoRecommendationAction {

  /** Acquire units, either to reach a long target or to cover part of a short position. */
  REBALANCE_BUY,
  /** Dispose of units, either to reduce a long position or to extend a short one. */
  REBALANCE_SELL,
  /** The position is inside its tolerance, or it is a tactical position that no rebalancing may open. */
  REBALANCE_HOLD,
  /** The action would increase exposure while the gross exposure ceiling is already breached. */
  REBALANCE_BLOCKED;

}
