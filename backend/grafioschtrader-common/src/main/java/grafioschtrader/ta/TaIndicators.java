package grafioschtrader.ta;

/**
 * Enumerates the types of technical analysis (TA) indicators supported for calculations.
 * <p>
 * The system supports overlay indicators (SMA, EMA) that are displayed on the price chart, and oscillator indicators
 * (RSI) that are displayed in a separate panel below the price chart.
 * </p>
 */
public enum TaIndicators {
  /**
   * Simple Moving Average (SMA).
   * <p>
   * A moving average that gives equal weight to all data points in the calculation period. Displayed as an overlay on
   * the price chart.
   * </p>
   */
  SMA(false),
  /**
   * Exponential Moving Average (EMA).
   * <p>
   * A type of moving average that gives more weight to recent prices in an attempt to make it more responsive to new
   * information. Displayed as an overlay on the price chart.
   * </p>
   */
  EMA(false),
  /**
   * Relative Strength Index (RSI).
   * <p>
   * A momentum oscillator that measures the speed and magnitude of price changes. It oscillates between 0 and 100,
   * with values above 70 typically indicating overbought conditions and values below 30 indicating oversold conditions.
   * Displayed in a separate panel below the price chart.
   * </p>
   */
  RSI(true);

  private final boolean oscillator;

  TaIndicators(boolean oscillator) {
    this.oscillator = oscillator;
  }

  /**
   * Indicates whether this indicator is an oscillator type.
   * <p>
   * Oscillator indicators (like RSI) have bounded output ranges (typically 0-100) and should be displayed in a
   * separate panel below the price chart. Non-oscillator indicators (like SMA, EMA) follow the price and should be
   * displayed as overlays on the main price chart.
   * </p>
   *
   * @return true if this is an oscillator indicator, false otherwise
   */
  public boolean isOscillator() {
    return oscillator;
  }
}
