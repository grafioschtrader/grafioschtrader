package grafiosch.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class DateHelper {

  /**
   * Returns the maximum or minimum of two LocalDate values. If one date is null, the other is returned. If both are
   * null, null is returned.
   *
   * @param d1  The first date.
   * @param d2  The second date.
   * @param max If true, returns the maximum of the two dates; otherwise, returns the minimum.
   * @return The maximum or minimum date, or one of the dates if the other is null, or null if both are null.
   */
  public static LocalDate getMaxMinLocalDate(LocalDate d1, LocalDate d2, boolean max) {
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
   * Returns the maximum or minimum of two LocalDateTime values. If one is null, the other is returned. If both are
   * null, null is returned.
   */
  public static LocalDateTime getMaxMinLocalDateTime(LocalDateTime d1, LocalDateTime d2, boolean max) {
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
   * Checks if a given LocalDate is today or any day after today.
   *
   * @param date The date to check.
   * @return {@code true} if the date is today or in the future, {@code false} otherwise.
   */
  public static boolean isTodayOrAfter(LocalDate date) {
    return !date.isBefore(LocalDate.now());
  }

  /**
   * Checks if a given date is today, or after today, or was part of the most recent weekend (Saturday or Sunday) but
   * only if today is Monday.
   *
   * @param date The date to check.
   * @return {@code true} if the condition is met, {@code false} otherwise.
   */
  public static boolean isUntilDateEqualNowOrAfterOrInActualWeekend(LocalDate date) {
    LocalDate today = LocalDate.now();
    return !date.isBefore(today)
        || (ChronoUnit.DAYS.between(date, today) <= 2
            && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY));
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

}
