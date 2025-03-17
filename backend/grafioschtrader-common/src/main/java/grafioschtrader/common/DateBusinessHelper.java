package grafioschtrader.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import grafiosch.BaseConstants;
import grafioschtrader.GlobalConstants;

public class DateBusinessHelper {

  public static Date getOldestTradingDay() throws ParseException {
    SimpleDateFormat format = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    return format.parse(GlobalConstants.OLDEST_TRADING_DAY);
  }

}
