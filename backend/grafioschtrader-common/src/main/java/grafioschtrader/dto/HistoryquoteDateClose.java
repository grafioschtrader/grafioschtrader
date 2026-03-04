package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

// Warning implements IDateAndClose wont work!!!
public class HistoryquoteDateClose {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  public Double close;

  public HistoryquoteDateClose() {
  }

  public HistoryquoteDateClose(LocalDate date, Double close) {
    this.date = date;
    this.close = close;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public Double getClose() {
    return close;
  }

  public void setClose(Double close) {
    this.close = close;
  }
}
