package grafiosch.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DateHelper {

  /**
   * Returns the maximum or minimum of two dates. If one date is null, the other date is returned. If both are null,
   * null is returned.
   *
   * @param d1  The first date.
   * @param d2  The second date.
   * @param max If true, returns the maximum of the two dates; otherwise, returns the minimum.
   * @return The maximum or minimum date, or one of the dates if the other is null, or null if both are null.
   */
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

  /**
   * Calculates the difference between two dates in the specified time unit.
   *
   * @param from     The start date.
   * @param to       The end date.
   * @param timeUnit The unit of time for the difference (e.g., TimeUnit.DAYS).
   * @return The difference between the two dates in the specified time unit.
   */
  public static long getDateDiff(Date from, Date to, TimeUnit timeUnit) {
    long diffInMillies = to.getTime() - from.getTime();
    return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
  }

  /**
   * Sets the time fields (hour, minute, second, millisecond) of a given date to zero and optionally adds a specified
   * number of days.
   *
   * @param date   The date to modify.
   * @param addDay The number of days to add to the date.
   * @return A new Date object with time set to zero and days added.
   */
  public static Date setTimeToZeroAndAddDay(Date date, int addDay) {
    Calendar day = Calendar.getInstance();
    day.setTime(date);
    DateHelper.setTimeToZero(day);
    day.add(Calendar.DATE, addDay);

    return day.getTime();
  }

  /**
   * Converts a {@link Date} to a {@link Calendar} instance with time fields (hour, minute, second, millisecond) set to
   * zero.
   *
   * @param date The date to convert.
   * @return A Calendar instance representing the given date with time set to zero.
   */
  public static Calendar getCalendar(final Date date) {
    final Calendar currentCalendar = Calendar.getInstance();
    currentCalendar.setTime(date);
    DateHelper.setTimeToZero(currentCalendar);
    return currentCalendar;
  }

  /**
   * Sets the time fields (hour, minute, second, millisecond) of a given {@link Calendar} instance to zero.
   *
   * @param day The Calendar instance to modify.
   */
  public static void setTimeToZero(Calendar day) {
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
  }

  /**
   * Checks if a date is today.
   *
   * @param date the date, not altered, not null.
   * @return true if the date is today.
   * @throws IllegalArgumentException if the date is <code>null</code>
   */
  public static boolean isToday(Date date) {
    return DateHelper.isSameDay(date, Calendar.getInstance().getTime());
  }

  /**
   * Checks if a given date is today or any day after today.
   *
   * @param date The date to check.
   * @return {@code true} if the date is today or in the future, {@code false} otherwise.
   */
  public static boolean isTodayOrAfter(Date date) {
    Date today = Calendar.getInstance().getTime();
    return DateHelper.isSameDay(date, today) || date.after(today);
  }

  /**
   * Checks if a given date is today, or after today, or was part of the most recent weekend (Saturday or Sunday) but
   * only if today is Monday.
   *
   * @param date The date to check.
   * @return {@code true} if the condition is met, {@code false} otherwise.
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
   * Checks if two calendars represent the same day ignoring time.
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
   * Checks if two dates are on the same day ignoring time.
   *
   * @param date1 the first date, not altered, not null
   * @param date2 the second date, not altered, not null
   * @return true if they represent the same day
   * @throws IllegalArgumentException if either date is <code>null</code>
   */
  public static boolean isSameDay(Date date1, Date date2) {
    if (date1 == null || date2 == null) {
      throw new IllegalArgumentException("The dates must not be null!");
    }
    Calendar cal1 = Calendar.getInstance();
    cal1.setTime(date1);
    Calendar cal2 = Calendar.getInstance();
    cal2.setTime(date2);
    return isSameDay(cal1, cal2);
  }

  /**
   * Converts a {@link Date} object to a {@link LocalDate} object using the system's default time zone.
   *
   * @param date The Date object to convert.
   * @return The corresponding LocalDate object.
   */
  public static LocalDate getLocalDate(Date date) {
    Instant instant = date.toInstant();
    ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
    return zdt.toLocalDate();
  }

  /**
   * Converts a {@link LocalDate} object to a {@link Date} object. The time is set to the start of the day in the
   * system's default time zone.
   *
   * @param localDate The LocalDate object to convert. Can be null.
   * @return The corresponding Date object, or null if localDate is null.
   */
  public static Date getDateFromLocalDate(LocalDate localDate) {
    return localDate == null ? null : Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  /**
   * Converts a {@link LocalDate} object to the number of seconds since the epoch (January 1, 1970, 00:00:00 GMT). The
   * conversion uses the start of the day in the system's default time zone.
   *
   * @param localDate The LocalDate object to convert.
   * @return The number of seconds since the epoch.
   */
  public static long LocalDateToEpocheSeconds(LocalDate localDate) {
    ZoneId zoneId = ZoneId.systemDefault();
    return localDate.atStartOfDay(zoneId).toEpochSecond();
  }

  /**
   * Converts a {@link LocalDateTime} object to a {@link Date} object using the system's default time zone.
   *
   * @param dateToConvert The LocalDateTime object to convert.
   * @return The corresponding Date object.
   */
  public static Date convertToDateViaInstant(LocalDateTime dateToConvert) {
    return java.util.Date.from(dateToConvert.atZone(ZoneId.systemDefault()).toInstant());
  }

}
