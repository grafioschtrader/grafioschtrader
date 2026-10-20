package grafioschtrader.dto;

import java.time.LocalDate;

/** Daily prices shared by entities and indicator projections. Independent of Spring Data projection marker types. */
public interface IDateCloseHighLow {
  Integer getIdSecuritycurrency();

  LocalDate getDate();

  double getClose();

  Double getHigh();

  Double getLow();
}
