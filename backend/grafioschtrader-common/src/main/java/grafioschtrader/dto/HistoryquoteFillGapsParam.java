package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
Parameters of the linear gap filling of the historical prices of a single instrument. Both settings are chosen by the
user in the corresponding dialog.
""")
public class HistoryquoteFillGapsParam {

  @Schema(description = "True if an existing weekend price is moved to a missing Friday before the gaps are filled")
  public boolean moveWeekendToFriday;

  @Schema(description = """
      Last date for which the linear filling may create a price. Days after this date are left untouched. It may lie in
      the future, but not beyond the active to date of the instrument or the date the trading calendar of its exchange
      is derived for.""")
  @NotNull
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate fillUpToDate;
}
