package grafioschtrader.algo.strategy.model.alerts;

/**
 * Direction in which the last price must cross its moving average for a {@link MaCrossingAlert} to fire. The constant
 * names are the values stored in the alert parameters and offered as the options of the edit form, so they must not be
 * renamed.
 */
public enum CrossDirection {
  /** The price rises from below the moving average to above it. */
  ABOVE,
  /** The price falls from above the moving average to below it. */
  BELOW
}
