package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.Queue;

import grafioschtrader.ta.TaIndicatorData;

public class SimpleMovingAverage implements CalcAccessIndicator {
  // queue used to store list so that we get the average
  private final TaIndicatorData[] taIndicatorData;
  private final Queue<Double> dataset = new LinkedList<>();
  private final int period;
  private double sum;
  private int dataIndex = 0;

  // constructor to initialize period
  public SimpleMovingAverage(int period, int numberOfDatapoints) {
    this.period = period;
    taIndicatorData = new TaIndicatorData[numberOfDatapoints - period];
  }

  // function to add new data in the list and update the sum so that
  // we get the new mean
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
