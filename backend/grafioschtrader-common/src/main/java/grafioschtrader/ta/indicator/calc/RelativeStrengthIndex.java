package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;

import grafioschtrader.ta.TaIndicatorData;

/**
 * Calculates the Relative Strength Index (RSI) using Wilder's smoothing method.
 * <p>
 * RSI is a momentum oscillator that measures the speed and magnitude of price changes.
 * It oscillates between 0 and 100, with values above 70 typically indicating overbought conditions
 * and values below 30 indicating oversold conditions.
 * </p>
 * <p>
 * The calculation uses Wilder's smoothing method:
 * <ol>
 * <li>Calculate price changes (current close - previous close)</li>
 * <li>Separate into gains (positive changes) and losses (absolute negative changes)</li>
 * <li>Calculate smoothed average gain and loss using exponential moving average with alpha = 1/period</li>
 * <li>RS = Average Gain / Average Loss</li>
 * <li>RSI = 100 - (100 / (1 + RS))</li>
 * </ol>
 * </p>
 */
public class RelativeStrengthIndex implements CalcAccessIndicator {

  /**
   * Array to store the calculated RSI data points. Each entry contains a date and the RSI value for that date.
   */
  private final TaIndicatorData[] taIndicatorData;

  /**
   * The number of periods to use for the RSI calculation (typically 14).
   */
  private final int period;

  /**
   * Smoothing factor for Wilder's method: 1/period.
   */
  private final double alpha;

  /**
   * Smoothed average gain value.
   */
  private double avgGain = 0;

  /**
   * Smoothed average loss value.
   */
  private double avgLoss = 0;

  /**
   * Previous closing price for calculating price change.
   */
  private Double previousClose = null;

  /**
   * Running sum of gains during the initial period.
   */
  private double sumGain = 0;

  /**
   * Running sum of losses during the initial period.
   */
  private double sumLoss = 0;

  /**
   * Counter for the number of data points added.
   */
  private int count = 0;

  /**
   * Index to keep track of the next position to fill in the taIndicatorData array.
   */
  private int dataIndex = 0;

  /**
   * Constructs a RelativeStrengthIndex calculator.
   *
   * @param period             The number of periods for the RSI calculation (typically 14).
   * @param numberOfDatapoints The total number of historical data points that will be processed.
   *                           This is used to pre-allocate the size of the result array.
   */
  public RelativeStrengthIndex(int period, int numberOfDatapoints) {
    this.period = period;
    this.alpha = 1.0 / period;
    // RSI needs period + 1 data points before producing first value
    this.taIndicatorData = new TaIndicatorData[Math.max(0, numberOfDatapoints - period)];
  }

  /**
   * Adds a new data point (closing price for a given date) to the RSI calculation.
   * <p>
   * The first RSI value is calculated after period + 1 data points have been added
   * (since we need the first price change to start calculating).
   * </p>
   *
   * @param date       The date corresponding to the closing price.
   * @param closePrice The closing price for the given date.
   */
  @Override
  public void addData(LocalDate date, double closePrice) {
    if (previousClose != null) {
      double change = closePrice - previousClose;
      double gain = Math.max(0, change);
      double loss = Math.max(0, -change);

      count++;

      if (count < period) {
        // Accumulate gains and losses during the initial period
        sumGain += gain;
        sumLoss += loss;
      } else if (count == period) {
        // First RSI calculation: use simple average
        sumGain += gain;
        sumLoss += loss;
        avgGain = sumGain / period;
        avgLoss = sumLoss / period;
        double rsi = calculateRsi();
        taIndicatorData[dataIndex++] = new TaIndicatorData(date, rsi);
      } else {
        // Subsequent calculations: use Wilder's smoothing
        avgGain = avgGain * (1 - alpha) + gain * alpha;
        avgLoss = avgLoss * (1 - alpha) + loss * alpha;
        double rsi = calculateRsi();
        taIndicatorData[dataIndex++] = new TaIndicatorData(date, rsi);
      }
    }
    previousClose = closePrice;
  }

  /**
   * Calculates the RSI value from the current average gain and loss.
   *
   * @return The RSI value between 0 and 100.
   */
  private double calculateRsi() {
    if (avgLoss == 0) {
      return 100.0;
    }
    double rs = avgGain / avgLoss;
    return 100.0 - (100.0 / (1.0 + rs));
  }

  @Override
  public TaIndicatorData[] getTaIndicatorData() {
    return taIndicatorData;
  }

}
