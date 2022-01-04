package grafioschtrader.dto;

import java.time.LocalDate;

public interface IMinMaxDateHistoryquote {
  Integer getIdSecuritycurrency();

  LocalDate getMinDate();

  LocalDate getMaxDate();
 
}
