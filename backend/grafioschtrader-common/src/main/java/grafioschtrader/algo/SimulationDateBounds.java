package grafioschtrader.algo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What limits the opening date of a simulation environment, so the dialog can say it before the date is chosen rather
 * than refusing it afterwards.
 *
 * <p>
 * None of this forbids an earlier opening date. Opening with cash alone, before any instrument of the hierarchy has
 * price data, is a legitimate starting point; the replay simply decides nothing until the data begins. The dialog
 * therefore reports these dates and never bounds the date picker with them.
 * </p>
 */
@Schema(description = """
    Dates that limit what a simulation environment and its historical replay can evaluate. They are informational:
    an earlier opening date remains allowed.""")
public class SimulationDateBounds {

  @Schema(description = """
      Earliest date on which every instrument of the linked watchlist and every currency pair they need carries a
      closing price. A replay can decide nothing before this day. Null when at least one instrument has no price data
      at all, in which case instrumentsWithoutHistory names them.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate universeFromDate;

  @Schema(description = "Instruments of the linked watchlist that carry no historical price at all")
  public List<String> instrumentsWithoutHistory = new ArrayList<>();

  @Schema(description = """
      First transaction of the main portfolio. The two initialization modes that read the portfolio cannot open
      before it. Null when the portfolio holds no transaction yet, which leaves only the manual cash balances.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate firstTransactionDate;

  @Schema(description = """
      Positions of the main portfolio that have no usable closing price on the requested opening date, and the
      currency pairs missing for them. Empty unless an opening date was passed. Taking the portfolio over opens with
      these positions regardless; they carry no value until their price data begins.""")
  public List<String> unpricedAtOpeningDate = new ArrayList<>();
}
