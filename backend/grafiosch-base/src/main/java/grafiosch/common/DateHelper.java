package grafiosch.common;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

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
   * Converts a {@link Date} object to a {@link LocalDate} object using the system's default time zone. Handles both
   * {@link java.util.Date} and {@link java.sql.Date} instances correctly.
   *
   * @param date The Date object to convert.
   * @return The corresponding LocalDate object.
   */
  public static LocalDate getLocalDate(Date date) {
    if (date instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
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
