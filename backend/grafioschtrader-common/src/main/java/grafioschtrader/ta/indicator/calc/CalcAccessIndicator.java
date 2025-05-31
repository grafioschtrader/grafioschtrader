package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;

import grafioschtrader.ta.TaIndicatorData;

public interface CalcAccessIndicator {
  void addData(LocalDate date, double closePrice);

  /**
   * Returns the array containing the calculated Simple Moving Average (SMA) data.
   * <p>
   * Each element is a TaIndicatorData object, which includes the date and the calculated SMA value for that date. The
   * length of this array is determined at construction ('numberOfDatapoints' - 'period'). If fewer data points were
   * processed than 'numberOfDatapoints' such that not all slots in this array were filled, the remaining slots will
   * contain null.
   * </p>
   *
   * @return An array of TaIndicatorData, representing the series of calculated SMA values.
   */
  TaIndicatorData[] getTaIndicatorData();
}
