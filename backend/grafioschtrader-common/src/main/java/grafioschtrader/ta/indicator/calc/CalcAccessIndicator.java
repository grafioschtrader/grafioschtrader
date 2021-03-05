package grafioschtrader.ta.indicator.calc;

import java.time.LocalDate;

import grafioschtrader.ta.TaIndicatorData;

public interface CalcAccessIndicator {
  void addData(LocalDate date, double closePrice);

  TaIndicatorData[] getTaIndicatorData();
}
