package grafioschtrader.calendar.rule;

import java.time.LocalDate;

/**
 * Calculates the date of Easter Sunday, from which a substantial part of the European exchange holidays is derived.
 *
 * <p>
 * Good Friday, Easter Monday, Ascension, Whit Monday and Corpus Christi are all defined as a fixed distance from Easter
 * Sunday, so a single calculation covers them all. Both the Western (Gregorian) and the Orthodox (Julian) computus are
 * provided because they yield different Sundays in most years.
 * </p>
 */
public final class EasterCalculator {

  private EasterCalculator() {
  }

  /**
   * Returns Western (Gregorian) Easter Sunday using the Anonymous Gregorian algorithm, also known as the
   * Meeus/Jones/Butcher algorithm. It is exact for all years in the Gregorian calendar.
   *
   * @param year the Gregorian year
   * @return the date of Easter Sunday in that year
   */
  public static LocalDate westernEaster(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }

  /**
   * Returns Orthodox (Julian) Easter Sunday expressed as a Gregorian date. The Julian computus is applied and the
   * result is then shifted by the Julian-to-Gregorian offset, which is 13 days for the years 1900 to 2099 and therefore
   * constant across the whole range GT supports.
   *
   * @param year the Gregorian year
   * @return the date of Orthodox Easter Sunday in that year, in the Gregorian calendar
   */
  public static LocalDate orthodoxEaster(int year) {
    int a = year % 4;
    int b = year % 7;
    int c = year % 19;
    int d = (19 * c + 15) % 30;
    int e = (2 * a + 4 * b - d + 34) % 7;
    int month = (d + e + 114) / 31;
    int day = ((d + e + 114) % 31) + 1;
    LocalDate julian = LocalDate.of(year, month, day);
    return julian.plusDays(julianToGregorianOffset(year));
  }

  /**
   * Number of days that must be added to a Julian date to obtain the corresponding Gregorian date. GT only supports
   * dates from the year 2000 onwards, so in practice this always returns 13, but the general formula is used so the
   * method stays correct if the supported range is ever widened.
   *
   * @param year the Gregorian year
   * @return the offset in days
   */
  private static int julianToGregorianOffset(int year) {
    int century = year / 100;
    return century - (century / 4) - 2;
  }
}
