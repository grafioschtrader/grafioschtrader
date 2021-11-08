package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Securitycurrency;

public class CorrelationRollingResult {

  public final List<Securitycurrency<?>> securitycurrencyList;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate[] dates;
  public final Double[] correlation;

  public CorrelationRollingResult(List<Securitycurrency<?>> securitycurrencyList, LocalDate[] dates,
      Double[] correlation) {
    this.securitycurrencyList = securitycurrencyList;
    this.dates = dates;
    this.correlation = correlation;
  }

  @Override
  public String toString() {
    return "CorrelationRollingResult [securitycurrencyList=" + securitycurrencyList + ", dates=" + dates
        + ", correlation=" + Arrays.toString(correlation) + "]";
  }

}
