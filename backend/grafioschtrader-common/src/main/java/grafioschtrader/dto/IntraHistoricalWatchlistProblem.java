package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = """
    Defines criteria for identifying instruments in a watchlist that have problems 
    with their intraday or historical price data. The values of this class are filled in by a user dialog.""")
public class IntraHistoricalWatchlistProblem {
  @Schema(description = "Flag to indicate whether to check for problems with intraday price data. If true, instruments with intraday data issues will be considered.")
  public boolean addIntraday;
  @Schema(description = "Flag to indicate whether to check for problems with historical price data. If true, instruments with historical data issues will be considered.")
  public boolean addHistorical;

  @Schema(description = """
     The number of days since the last successful price data update. 
     Used to identify instruments with outdated data. Minimum 2, maximum 90 days.""")
  @Min(2)
  @Max(90)
  public int daysSinceLastWork;

}
