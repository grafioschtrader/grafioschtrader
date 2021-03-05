package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;

import grafioschtrader.ta.TaIndicatorData;

public class ExponentialMovingAverage implements CalcAccessIndicator {
//queue used to store list so that we get the average
  private final TaIndicatorData[] taIndicatorData;
  private final int period;
  private double ema = 0d;
  private int dataIndex = 0;
  private final double multiplier;
  private int dataPointCounter = 0;

  // constructor to initialize period
  public ExponentialMovingAverage(int period, int numberOfDatapoints) {
    this.period = period;
    taIndicatorData = new TaIndicatorData[numberOfDatapoints - period];
    multiplier = 2d / (period + 1d);
  }

  @Override
  public void addData(LocalDate date, double closePrice) {
    ema += (closePrice - ema) * multiplier;
    if (dataPointCounter >= period) {
      taIndicatorData[dataIndex++] = new TaIndicatorData(date, ema);
    } else if (dataPointCounter == 0) {
      ema = closePrice;
    }
    dataPointCounter++;
  }

  @Override
  public TaIndicatorData[] getTaIndicatorData() {
    return taIndicatorData;
  }
}
