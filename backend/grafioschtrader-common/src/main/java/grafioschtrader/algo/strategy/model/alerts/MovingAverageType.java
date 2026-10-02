package grafioschtrader.algo.strategy.model.alerts;

/**
 * Kind of moving average a {@link MaCrossingAlert} compares the last price with. The constant names are the values
 * stored in the alert parameters and offered as the options of the edit form, so they must not be renamed.
 */
public enum MovingAverageType {
  /** Simple moving average: the unweighted mean of the closing prices of the period. */
  SMA,
  /** Exponential moving average: recent closing prices weigh more than older ones. */
  EMA
}
