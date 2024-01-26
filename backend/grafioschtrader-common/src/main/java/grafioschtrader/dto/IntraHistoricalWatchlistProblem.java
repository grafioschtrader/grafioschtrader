package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class IntraHistoricalWatchlistProblem {

  public boolean addIntraday;
  public boolean addHistorical;

  @Schema(description = "How many days since the last functioning")
  @Min(2)
  @Max(90)
  public int daysSinceLastWork;

}
