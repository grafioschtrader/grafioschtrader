package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.CreateType;

public class TradingDaysWithDateBoundaries {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final String oldestTradingCalendarDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final String youngestTradingCalendarDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final List<LocalDate> dates;
  public final List<CreateType> createTypes;

  public TradingDaysWithDateBoundaries(List<LocalDate> dates, List<CreateType> createTypes) {
    this.dates = dates;
    this.createTypes = createTypes;
    this.oldestTradingCalendarDay = GlobalConstants.OLDEST_TRADING_DAY;
    this.youngestTradingCalendarDay = GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY;
  }

}
