package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Date boundaries the deletion of linear filled and manually imported prices offers for a single instrument. They are the
oldest and the most recent stored price, which is the only period in which a deletion can remove anything. Deliberately
not taken from the data quality figures: those stop at the last completed trading day, while prices may well be stored
beyond it.
""")
public class HistoryquoteDeleteBounds {

  @Schema(description = "Oldest stored price of the instrument, the earliest selectable date")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate minDate;

  @Schema(description = """
      Most recent stored price of the instrument, the latest selectable date. It may lie in the future, because a
      linear filling can reach up to the active to date of the instrument.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate maxDate;

  public HistoryquoteDeleteBounds(LocalDate minDate, LocalDate maxDate) {
    this.minDate = minDate;
    this.maxDate = maxDate;
  }
}
