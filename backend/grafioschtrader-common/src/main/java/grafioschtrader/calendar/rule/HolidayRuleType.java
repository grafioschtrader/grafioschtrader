package grafioschtrader.calendar.rule;

/**
 * Kind of calculation used to resolve a {@link HolidayRule} to concrete dates for a given year.
 *
 * <p>
 * The trading calendar of a stock exchange is largely deterministic: most closures are either on a fixed calendar date,
 * on the n-th weekday of a month, or at a fixed distance from Easter. These cases are expressed by rules so that the
 * calendar can be produced for any year, including years in the future for which no price data exists yet. The
 * remaining cases -- above all the lunisolar calendars of the Asian exchanges and closures decreed by a government for
 * a single year -- cannot be computed and are enumerated with {@link #EXPLICIT_DATES}.
 * </p>
 */
public enum HolidayRuleType {

  /**
   * A fixed day in a fixed month, for example the 1st of January. Combined with {@link Observance} to express what
   * happens when the date falls on a weekend.
   */
  FIXED,

  /**
   * The n-th occurrence of a weekday within a month, for example the fourth Thursday in November (US Thanksgiving).
   * {@code nth = -1} selects the last occurrence, for example the last Monday in May (US Memorial Day).
   */
  NTH_WEEKDAY,

  /**
   * Every occurrence of a weekday. This represents a non-standard exchange weekend, for example Friday at the Saudi
   * and Tel Aviv exchanges, where Grafioschtrader's global Monday-to-Friday base calendar needs an explicit closure.
   */
  WEEKLY,

  /**
   * A fixed number of days before or after Western (Gregorian) Easter Sunday. Good Friday is offset {@code -2}, Easter
   * Monday {@code +1}, Ascension {@code +39}, Whit Monday {@code +50} and Corpus Christi {@code +60}.
   */
  EASTER_RELATIVE,

  /**
   * The same as {@link #EASTER_RELATIVE} but based on Orthodox (Julian) Easter, which in most years falls on a
   * different Sunday. Relevant for exchanges in countries with a predominantly Orthodox calendar.
   */
  ORTHODOX_EASTER_RELATIVE,

  /**
   * A day in the Islamic (Hijri) calendar, for example 1 Shawwal (Eid al-Fitr) or 10 Dhu al-Hijjah (Eid al-Adha).
   * Because the observed date depends on the sighting of the moon it may deviate from the arithmetic conversion by a
   * day, which is why such rules carry an {@code offsetTolerance}.
   */
  HIJRI,

  /**
   * An enumerated list of dates that cannot be derived by calculation. Used for lunisolar holidays (Chinese New Year,
   * Chuseok), for bridge and make-up days announced annually by a government, and for one-off closures such as a
   * national day of mourning or a natural disaster.
   */
  EXPLICIT_DATES
}
