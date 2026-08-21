package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Date boundaries the linear gap filling dialog offers for a single instrument. The dialog uses them to limit the date
picker, to preselect the current day, and to warn when the trading calendar of the exchange cannot even reach that day.
""")
public class HistoryquoteFillGapsBounds {

  @Schema(description = "Earliest selectable date, the active from date of the instrument")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate minFillUpTo;

  @Schema(description = """
      Latest selectable date, the earlier of the active to date of the instrument and the date the trading calendar of
      the exchange is derived for. It commonly lies in the future, for instance at the maturity of a bond, and filling
      up to such a date is intended - the user has to move the date there.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate maxFillUpTo;

  @Schema(description = """
      Date proposed to the user, the current day unless the selectable range ends earlier. Creating prices for days that
      have not happened yet therefore never occurs by accident.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate defaultFillUpTo;

  @Schema(description = """
      Date up to which the trading calendar of the exchange is derived. Beyond it the exchange holidays are unknown,
      which is why it caps the selectable range.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate calendarHorizon;

  @Schema(description = """
      True when the trading calendar lags behind the last completed trading day while the instrument is still active,
      that is when the calendar and not the lifetime of the instrument limits the selectable range""")
  public boolean calendarOutdated;

  @Schema(description = "Number of trading days between the calendar horizon and the last completed trading day")
  public int missingTradingDays;
}
