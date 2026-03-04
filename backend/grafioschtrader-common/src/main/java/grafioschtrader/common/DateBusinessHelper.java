package grafioschtrader.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import grafioschtrader.GlobalConstants;

public class DateBusinessHelper {

  public static LocalDate getOldestTradingDayAsLocalDate() {
    return LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
  }

  public static LocalDateTime getOldestTradingDayAsLocalDateTime() {
    return LocalDateTime.of(getOldestTradingDayAsLocalDate(), LocalTime.MIDNIGHT);
  }

}
