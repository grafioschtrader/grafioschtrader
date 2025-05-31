package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.entities.Securitycurrency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents the result of a rolling correlation calculation between two financial instruments over a series of dates.")
public class CorrelationRollingResult {

  @Schema(description = "The list of two security/currency instruments for which the rolling correlation was calculated.")
  public final List<Securitycurrency<?>> securitycurrencyList;

  @Schema(description = "Array of dates corresponding to each calculated correlation value. Dates are formatted as ISO 8601")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate[] dates;
  @Schema(description = """
      Array of calculated rolling correlation values. Each value corresponds to a date in the 'dates' array.
      A null value indicates that the correlation could not be calculated for that specific date (e.g., insufficient data for the window).""")
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
