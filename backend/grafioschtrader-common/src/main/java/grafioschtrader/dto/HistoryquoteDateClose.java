package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

// Warning implements IDateAndClose wont work!!!
public class HistoryquoteDateClose {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  public Double close;

  public HistoryquoteDateClose() {
  }

  public HistoryquoteDateClose(Date date, Double close) {
    this.date = ((java.sql.Date) date).toLocalDate();
    this.close = close;
  }

  public HistoryquoteDateClose(LocalDate localDate, Double close) {
    this.date = localDate;
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
