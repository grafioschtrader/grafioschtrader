package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Proposal to the user for recording the financing costs for margin instruments.")
public class ProposedMarginFinanceCost {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "The current date or the date on which the position was closed.")
  public LocalDate untilDate;
  @Schema(description = "The calculated number of days on which financing costs are to be paid.")
  public int daysToPay;
  @Schema(description = "The total financing costs calculated.")
  public double financeCost;
}
