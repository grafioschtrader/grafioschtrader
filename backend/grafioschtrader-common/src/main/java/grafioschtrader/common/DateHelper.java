package grafioschtrader.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import grafioschtrader.GlobalConstants;

public class DateHelper {

  ///////////////////////////////////////////////////////////////////////////////
  // taken from
  /////////////////////////////////////////////////////////////////////////////// http://www.java2s.com/Code/Java/Data-Type/Checksifacalendardateistoday.htm
  ///////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  public static Date getOldestTradingDay() throws ParseException {
    SimpleDateFormat format = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return format.parse(GlobalConstants.OLDEST_TRADING_DAY);
  }

  public static Date getMaxMinDate(Date d1, Date d2, boolean max) {
    if (d1 == null && d2 != null) {
      return d2;
    } else if (d2 == null) {
      return d1;
    } else if (max) {
      return d1.compareTo(d2) > 0 ? d1 : d2;
    } else {
      return d1.compareTo(d2) < 0 ? d1 : d2;
    }
  }

  public static long getDateDiff(Date from, Date to, TimeUnit timeUnit) {
    long diffInMillies = to.getTime() - from.getTime();
    return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
  }

  public static Date setTimeToZeroAndAddDay(Date date, int addDay) {
    Calendar day = Calendar.getInstance();
    day.setTime(date);
    DateHelper.setTimeToZero(day);
    day.add(Calendar.DATE, addDay);

    return day.getTime();
  }

  public static Calendar getCalendar(final Date date) {
    final Calendar currentCalendar = Calendar.getInstance();
    currentCalendar.setTime(date);
    DateHelper.setTimeToZero(currentCalendar);
    return currentCalendar;
  }

  public static void setTimeToZero(Calendar day) {
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
  }

  /**
   * <p>
   * Checks if a date is today.
   * </p>
   *
   * @param date the date, not altered, not null.
   * @return true if the date is today.
   * @throws IllegalArgumentException if the date is <code>null</code>
   */
  public static boolean isToday(Date date) {
    return DateHelper.isSameDay(date, Calendar.getInstance().getTime());
  }

  public static boolean isTodayOrAfter(Date date) {
    Date today = Calendar.getInstance().getTime();
    return DateHelper.isSameDay(date, today) || date.after(today);
  }

  /**
   * Return true when date is today or after today or a last weekend day (only
   * true when today is Monday after last weekend)
   *
   * @param date
   * @return
   */
  public static boolean isUntilDateEqualNowOrAfterOrInActualWeekend(Date date) {
    Calendar untilCalendar = Calendar.getInstance();
    untilCalendar.setTime(date);
    Date today = Calendar.getInstance().getTime();
    return DateHelper.isSameDay(date, today) || date.after(today)
        || DateHelper.getDateDiff(date, today, TimeUnit.DAYS) <= 2
            && (untilCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || untilCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
  }

  /**
   * <p>
   * Checks if two calendars represent the same day ignoring time.
   * </p>
   *
   * @param cal1 the first calendar, not altered, not null
   * @param cal2 the second calendar, not altered, not null
   * @return true if they represent the same day
   * @throws IllegalArgumentException if either calendar is <code>null</code>
   */
  public static boolean isSameDay(Calendar cal1, Calendar cal2) {
    if (cal1 == null || cal2 == null) {
      throw new IllegalArgumentException("The dates must not be null");
    }
    return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
        && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
  }

  /**
   * <p>
   * Checks if two dates are on the same day ignoring time.
   * </p>
   *
   * @param date1 the first date, not altered, not null
   * @param date2 the second date, not altered, not null
   * @return true if they represent the same day
   * @throws IllegalArgumentException if either date is <code>null</code>
   */
  public static boolean isSameDay(Date date1, Date date2) {
    if (date1 == null || date2 == null) {
      throw new IllegalArgumentException("The dates must not be null");
    }
    Calendar cal1 = Calendar.getInstance();
    cal1.setTime(date1);
    Calendar cal2 = Calendar.getInstance();
    cal2.setTime(date2);
    return isSameDay(cal1, cal2);
  }

  public static LocalDate getLocalDate(Date date) {
    Instant instant = date.toInstant();
    ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
    return zdt.toLocalDate();
  }

  public static Date getDateFromLocalDate(LocalDate localDate) {
    return localDate == null ? null : Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  public static long LocalDateToEpocheSeconds(LocalDate localDate) {
    ZoneId zoneId = ZoneId.systemDefault();
    return localDate.atStartOfDay(zoneId).toEpochSecond();
  }

  public static Date convertToDateViaInstant(LocalDateTime dateToConvert) {
    return java.util.Date.from(dateToConvert.atZone(ZoneId.systemDefault()).toInstant());
  }

}
