package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Basic completeness metrics for historical quotes over a trading period.")
public interface IHistoryquoteQuality {

  @Schema(description = "Earliest available quote date, formatted as YYYY-MM-DD.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  LocalDate getMinDate();

  @Schema(description = "Number of missing quotes before the first available quote.")
  int getMissingStart();

  @Schema(description = "Number of missing quotes after the last available quote.")
  int getMissingEnd();

  @Schema(description = "Latest available quote date, formatted as YYYY-MM-DD.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  LocalDate getMaxDate();

  @Schema(description = "Total number of missing quotes within the evaluated period.")
  int getTotalMissing();

  @Schema(description = "Total number of trading days expected within the evaluated period.")
  int getExpectedTotal();

  @Schema(description = "Percentage of available quotes relative to the expected total (0–100).")
  double getQualityPercentage();

  @Schema(description = "Count of quotes falling outside the official trading calendar.")
  Integer getToManyAsCalendar();

  @Schema(description = "Count of quotes recorded on Saturdays.")
  Integer getQuoteSaturday();

  @Schema(description = "Count of quotes recorded on Sundays.")
  Integer getQuoteSunday();

  @Schema(description = "Count of quotes manually imported into the system.")
  Integer getManualImported();

  @Schema(description = "Count of quotes automatically created by the connector.")
  Integer getConnectorCreated();

  @Schema(description = "Count of quotes populated via linear interpolation.")
  Integer getFilledLinear();

  @Schema(description = "Count of quotes calculated by business logic (e.g., splits, dividends).")
  Integer getCalculated();

  @Schema(description = "Count of quotes manually modified by a user.")
  Integer getUserModified();

  @Schema(description = "Percentage of quotes with valid open, high, and low values (0-100). Both 0 and NULL are treated as missing.")
  Double getOhlPercentage();
}
