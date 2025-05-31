package grafioschtrader.ta;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Represents a single data point for a technical indicator series.
    This typically consists of a date and the corresponding calculated value of the indicator.""")
public class TaIndicatorData {

  @Schema(description = "The date for which the technical indicator's value was calculated. Formatted according to the ISO 8601 standard (yyyy-MM-dd).")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  @Schema(description = """
      The calculated value of the technical indicator for the corresponding 'date'.
      This can be null if the indicator could not be calculated for this specific date (e.g., insufficient historical data to meet the period requirement at the beginning of a series).""")
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
