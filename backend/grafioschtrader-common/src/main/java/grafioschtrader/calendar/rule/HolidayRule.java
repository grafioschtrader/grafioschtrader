package grafioschtrader.calendar.rule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single holiday of a stock exchange, expressed as a rule that can be resolved to concrete dates for any year.
 *
 * <p>
 * Instances are deserialized from the YAML of a trading calendar rule set. Which of the optional fields are
 * relevant depends on {@link #type}; a rule that omits a field its type requires is rejected by {@link #validate()} at
 * load time rather than silently producing wrong dates.
 * </p>
 *
 * <p>
 * The validity range is essential rather than decorative. GT supports trading data back to the year 2000 and exchange
 * holidays have changed within that window -- Juneteenth became a closure at the US exchanges only in 2021, and
 * Euronext Paris stopped closing on Whit Monday in 2008. A rule without {@link #validFrom} / {@link #validTo} would
 * therefore be wrong for a large part of the supported period.
 * </p>
 *
 * <p>
 * Unknown YAML keys are rejected by the mapper configured in {@link HolidayRuleSetYamlParser}, so a misspelled field
 * name is refused when saving instead of being silently ignored.
 * </p>
 */
public class HolidayRule {

  /** Descriptive name of the holiday, used only for diagnostics and in the comparison report. */
  private String name;

  /** Kind of calculation used to resolve this rule to dates. Required. */
  private HolidayRuleType type;

  /** First year in which the closure applies, inclusive. Null means "since the beginning of the supported range". */
  private Integer validFrom;

  /** Last year in which the closure applies, inclusive. Null means "still in force". */
  private Integer validTo;

  /**
   * True when the exchange only shortens its trading hours on this day instead of closing. Such days are recorded so
   * the rule file describes the exchange completely, but they are never emitted as closures -- a half day is a trading
   * day and must not end up in {@code trading_days_minus}.
   */
  private boolean halfDay;

  /** Month 1-12. Required for {@link HolidayRuleType#FIXED}, {@link HolidayRuleType#NTH_WEEKDAY} and HIJRI. */
  private Integer month;

  /** Day of month. Required for {@link HolidayRuleType#FIXED} and {@link HolidayRuleType#HIJRI}. */
  private Integer day;

  /** Occurrence within the month, 1-5, or -1 for the last. Required for {@link HolidayRuleType#NTH_WEEKDAY}. */
  private Integer nth;

  /** Weekday selected by an {@link HolidayRuleType#NTH_WEEKDAY} rule. */
  private DayOfWeek dayOfWeek;

  /** Offset in days from Easter Sunday, negative for dates before it. Required for the Easter-relative types. */
  private Integer offset;

  /** How a closure falling on a weekend is transferred. Defaults to {@link Observance#NONE}. */
  private Observance observance = Observance.NONE;

  /**
   * Days by which the observed Hijri date may deviate from the arithmetic conversion. The start of an Islamic month
   * depends on the sighting of the moon, so the announced holiday can fall a day either side of the computed date. A
   * tolerance greater than zero widens the rule to that whole span.
   */
  private int offsetTolerance;

  /** Explicit dates for {@link HolidayRuleType#EXPLICIT_DATES}. */
  private List<LocalDate> dates = new ArrayList<>();

  /**
   * Resolves this rule to the dates on which the exchange is closed in the given year.
   *
   * <p>
   * Returns an empty list when the rule is not in force in that year, when it is a {@link #halfDay} rule, or when the
   * resolved date falls on a weekend and the observance does not transfer it. Weekend results are dropped because
   * Saturdays and Sundays are handled separately by the trading calendar and are deliberately absent from
   * {@code trading_days_minus}.
   * </p>
   *
   * @param year the year for which the dates are wanted
   * @return the resolved closure dates, possibly empty, never null
   */
  public List<LocalDate> resolve(int year) {
    return halfDay ? Collections.emptyList() : resolveIgnoringHalfDayFlag(year);
  }

  /**
   * Resolves this rule to dates regardless of the {@link #halfDay} flag. Used by the comparison report, which needs to
   * know where the shortened trading days fall in order to explain disagreements; the calendar generation itself always
   * goes through {@link #resolve(int)}.
   *
   * @param year the year for which the dates are wanted
   * @return the resolved dates, possibly empty, never null
   */
  public List<LocalDate> resolveIgnoringHalfDayFlag(int year) {
    if (!isValidInYear(year)) {
      return Collections.emptyList();
    }
    List<LocalDate> resolved = switch (type) {
    case FIXED -> resolveFixed(year);
    case NTH_WEEKDAY -> resolveNthWeekday(year);
    case WEEKLY -> resolveWeekly(year);
    case EASTER_RELATIVE -> List.of(EasterCalculator.westernEaster(year).plusDays(offset));
    case ORTHODOX_EASTER_RELATIVE -> List.of(EasterCalculator.orthodoxEaster(year).plusDays(offset));
    case HIJRI -> resolveHijri(year);
    case EXPLICIT_DATES -> dates.stream().filter(d -> d.getYear() == year).toList();
    };
    return resolved.stream().filter(HolidayRule::isWeekday).toList();
  }

  /**
   * Whether the rule is in force in the given year according to its validity range.
   *
   * @param year the year to test
   * @return true when the year lies within {@link #validFrom} and {@link #validTo}
   */
  public boolean isValidInYear(int year) {
    return (validFrom == null || year >= validFrom) && (validTo == null || year <= validTo);
  }

  private List<LocalDate> resolveFixed(int year) {
    return List.of(observance.apply(LocalDate.of(year, month, day)));
  }

  private List<LocalDate> resolveNthWeekday(int year) {
    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
    LocalDate resolvedDate = nth == -1 ? firstOfMonth.with(TemporalAdjusters.lastInMonth(dayOfWeek))
        : firstOfMonth.with(TemporalAdjusters.dayOfWeekInMonth(nth, dayOfWeek));
    return List.of(resolvedDate);
  }

  private List<LocalDate> resolveWeekly(int year) {
    List<LocalDate> result = new ArrayList<>();
    LocalDate date = LocalDate.of(year, 1, 1).with(TemporalAdjusters.nextOrSame(dayOfWeek));
    while (date.getYear() == year) {
      result.add(date);
      date = date.plusWeeks(1);
    }
    return result;
  }

  /**
   * Resolves an Islamic date to Gregorian dates. Because the Hijri year is about eleven days shorter than the Gregorian
   * one, the same Islamic date can occur twice within a single Gregorian year, so both Hijri years overlapping the
   * requested year are examined. The tolerance widens each hit into a span, reflecting that the observed start of the
   * month is fixed by moon sighting rather than by arithmetic.
   */
  private List<LocalDate> resolveHijri(int year) {
    List<LocalDate> result = new ArrayList<>();
    HijrahDate startOfYear = HijrahChronology.INSTANCE.date(LocalDate.of(year, 1, 1));
    int firstHijriYear = startOfYear.get(ChronoField.YEAR);
    for (int hijriYear = firstHijriYear; hijriYear <= firstHijriYear + 1; hijriYear++) {
      LocalDate candidate = toGregorian(hijriYear, month, day);
      if (candidate == null) {
        continue;
      }
      for (int shift = -offsetTolerance; shift <= offsetTolerance; shift++) {
        LocalDate shifted = candidate.plusDays(shift);
        if (shifted.getYear() == year) {
          result.add(shifted);
        }
      }
    }
    return result;
  }

  /**
   * Converts a Hijri year/month/day to a Gregorian date, returning null when the date lies outside the range the JDK's
   * Hijrah calendar supports.
   */
  private static LocalDate toGregorian(int hijriYear, int hijriMonth, int hijriDay) {
    try {
      return LocalDate.from(HijrahChronology.INSTANCE.date(hijriYear, hijriMonth, hijriDay));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static boolean isWeekday(LocalDate date) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
  }

  /**
   * Checks that the fields required by this rule's {@link #type} are present, so that a malformed YAML file fails at
   * load time with a clear message instead of producing a silently wrong calendar.
   *
   * @throws IllegalStateException when a required field is missing
   */
  public void validate() {
    require(type != null, "type is required");
    require(name != null && !name.isBlank(), "name is required");
    switch (type) {
    case FIXED -> require(month != null && day != null, "month and day are required for FIXED");
    case NTH_WEEKDAY -> require(month != null && nth != null && dayOfWeek != null,
        "month, nth and dayOfWeek are required for NTH_WEEKDAY");
    case WEEKLY -> require(dayOfWeek != null, "dayOfWeek is required for WEEKLY");
    case EASTER_RELATIVE, ORTHODOX_EASTER_RELATIVE -> require(offset != null, "offset is required for " + type);
    case HIJRI -> require(month != null && day != null, "month and day are required for HIJRI");
    case EXPLICIT_DATES -> require(!dates.isEmpty(), "dates must not be empty for EXPLICIT_DATES");
    }
    require(validFrom == null || validTo == null || validFrom <= validTo, "validFrom must not be after validTo");
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException("Holiday rule '" + name + "': " + message);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public HolidayRuleType getType() {
    return type;
  }

  public void setType(HolidayRuleType type) {
    this.type = type;
  }

  public Integer getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Integer validFrom) {
    this.validFrom = validFrom;
  }

  public Integer getValidTo() {
    return validTo;
  }

  public void setValidTo(Integer validTo) {
    this.validTo = validTo;
  }

  public boolean isHalfDay() {
    return halfDay;
  }

  public void setHalfDay(boolean halfDay) {
    this.halfDay = halfDay;
  }

  public Integer getMonth() {
    return month;
  }

  public void setMonth(Integer month) {
    this.month = month;
  }

  public Integer getDay() {
    return day;
  }

  public void setDay(Integer day) {
    this.day = day;
  }

  public Integer getNth() {
    return nth;
  }

  public void setNth(Integer nth) {
    this.nth = nth;
  }

  public DayOfWeek getDayOfWeek() {
    return dayOfWeek;
  }

  public void setDayOfWeek(DayOfWeek dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public Integer getOffset() {
    return offset;
  }

  public void setOffset(Integer offset) {
    this.offset = offset;
  }

  public Observance getObservance() {
    return observance;
  }

  public void setObservance(Observance observance) {
    this.observance = observance;
  }

  public int getOffsetTolerance() {
    return offsetTolerance;
  }

  public void setOffsetTolerance(int offsetTolerance) {
    this.offsetTolerance = offsetTolerance;
  }

  public List<LocalDate> getDates() {
    return dates;
  }

  public void setDates(List<LocalDate> dates) {
    this.dates = dates == null ? new ArrayList<>() : dates;
  }

  @Override
  public String toString() {
    return name + " (" + type + ")";
  }
}
