package grafioschtrader.ta;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

/**
 * Single data point for the chart
 */
public class TaIndicatorData {

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  public Double value;

  public TaIndicatorData(LocalDate date, Double value) {
    this.date = date;
    this.value = value;
  }

  @Override
  public String toString() {
    return "TaIndicatorData [date=" + date + ", value=" + value + "]";
  }

}
