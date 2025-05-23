package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.GlobalConstants;
import grafioschtrader.types.CreateType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains the dates on which trading or non-trading is possible.")
public class TradingDaysWithDateBoundaries {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final String oldestTradingCalendarDay;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final String youngestTradingCalendarDay;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final List<LocalDate> dates;
  public final List<CreateType> createTypes;

  public TradingDaysWithDateBoundaries(List<LocalDate> dates, List<CreateType> createTypes) {
    this.dates = dates;
    this.createTypes = createTypes;
    this.oldestTradingCalendarDay = GlobalConstants.OLDEST_TRADING_DAY;
    this.youngestTradingCalendarDay = GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY;
  }

}
