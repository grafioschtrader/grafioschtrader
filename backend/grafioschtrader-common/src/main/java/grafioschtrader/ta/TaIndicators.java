package grafioschtrader.ta;

/**
 * Enumerates the types of technical analysis (TA) indicators supported for calculations.
 * <p>
 * Currently, the system supports Simple Moving Average (SMA) and Exponential Moving Average (EMA). This list may be
 * expanded in the future to include other common technical indicators.
 * </p>
 */
public enum TaIndicators {
  /**
   * Enumerates the types of technical analysis (TA) indicators supported for calculations.
   * <p>
   * Currently, the system supports Simple Moving Average (SMA) and Exponential Moving Average (EMA). This list may be
   * expanded in the future to include other common technical indicators.
   * </p>
   */
  SMA,
  /**
   * Exponential Moving Average (EMA).
   * <p>
   * A type of moving average that gives more weight to recent prices in an attempt to make it more responsive to new
   * information.
   * </p>
   */
  EMA
}
