package grafioschtrader.entities.projection;

import java.time.LocalDate;

public interface SecurityYearClose {
  LocalDate getDate();

  double getSecurityClose();

  double getYearDiv();

  double getCurrencyClose();
}
