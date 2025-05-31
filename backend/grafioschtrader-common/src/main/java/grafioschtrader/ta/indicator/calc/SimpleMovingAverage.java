package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.Queue;

import grafioschtrader.ta.TaIndicatorData;

public class SimpleMovingAverage implements CalcAccessIndicator {
  /**
   * Array to store the calculated SMA data points. Each entry contains a date and the SMA value for that date.
   */
  private final TaIndicatorData[] taIndicatorData;
  /**
   * Queue to maintain the current window of data points for SMA calculation.
   */
  private final Queue<Double> dataset = new LinkedList<>();
  /**
   * The number of data points to include in the SMA calculation period.
   */
  private final int period;
  /**
   * The number of data points to include in the SMA calculation period.
   */
  private double sum;
  /**
   * Index to keep track of the next position to fill in the taIndicatorData array.
   */
  private int dataIndex = 0;

  /**
   * Constructs a SimpleMovingAverage calculator.
   *
   * @param period             The number of data points to include in the moving average calculation (e.g., 20 for a
   *                           20-day SMA).
   * @param numberOfDatapoints The total number of historical data points that will be processed. This is used to
   *                           pre-allocate the size of the result array. The first SMA value is available after
   *                           'period' data points have been added.
   */
  public SimpleMovingAverage(int period, int numberOfDatapoints) {
    this.period = period;
    taIndicatorData = new TaIndicatorData[numberOfDatapoints - period];
  }

  /**
   * Adds a new data point (closing price for a given date) to the calculation set.
   * <p>
   * This function updates the internal sum and dataset with the new data. If the number of data points in the dataset
   * exceeds the defined 'period', the oldest data point is removed from the sum and dataset, and a new SMA value is
   * calculated (sum / period). This new SMA value, associated with the provided 'date', is then stored.
   * </p>
   * Note: The first SMA value is calculated and stored only when the dataset has more than 'period' elements, meaning
   * at least 'period' + 1 elements have been added.
   *
   * @param date       The date corresponding to the 'closePrice'.
   * @param closePrice The value of the data point (e.g., closing price) to be added.
   */
  @Override
  public void addData(LocalDate date, double closePrice) {
    sum += closePrice;
    dataset.add(closePrice);

    if (dataset.size() > period) {
      sum -= dataset.remove();
      taIndicatorData[dataIndex++] = new TaIndicatorData(date, sum / period);
    }
  }

  @Override
  public TaIndicatorData[] getTaIndicatorData() {
    return taIndicatorData;
  }

}
