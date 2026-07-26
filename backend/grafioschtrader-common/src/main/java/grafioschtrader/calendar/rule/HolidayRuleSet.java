package grafioschtrader.calendar.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The complete set of holiday rules from which the closures of a stock exchange are calculated.
 *
 * <p>
 * Parsed from the YAML of a {@code trading_calendar_rule_set} row by {@link HolidayRuleSetYamlParser}. An exchange
 * whose calendar is identical to another one -- the German regional venues follow Xetra, the Swiss venues follow SIX
 * -- does not repeat the rules; its row points at the other row and {@link HolidayRuleSetResolver} merges the two.
 * The identity of the set, its MIC and name, are columns of the entity and therefore not fields here.
 * </p>
 */
public class HolidayRuleSet {

  /** Free text explaining sources and known limitations of this calendar, for the reader of the YAML. */
  private String note;

  /** First year for which this rule set is backed by an authoritative exchange calendar. Null means unbounded. */
  private Integer authoritativeFrom;

  /** Last year for which this rule set is backed by an authoritative exchange calendar. Null means unbounded. */
  private Integer authoritativeThrough;

  private List<HolidayRule> rules = new ArrayList<>();

  /** Announced or historical full closures that cannot be expressed safely as recurring rules. */
  private List<LocalDate> additionalClosures = new ArrayList<>();

  /** Dates produced by a broad recurring rule on which the exchange nevertheless held a trading session. */
  private List<LocalDate> openDates = new ArrayList<>();

  /**
   * Returns the dates on which the exchange is closed in the given year, excluding weekends and half days.
   *
   * @param year the year for which the closures are wanted
   * @return the closure dates in ascending order, never null
   */
  public SortedSet<LocalDate> closureDatesForYear(int year) {
    return new TreeSet<>(closuresWithRuleName(year).keySet());
  }

  /**
   * Returns the closures for a range of years.
   *
   * @param fromYear first year, inclusive
   * @param toYear   last year, inclusive
   * @return the closure dates in ascending order, never null
   */
  public SortedSet<LocalDate> closureDates(int fromYear, int toYear) {
    SortedSet<LocalDate> result = new TreeSet<>();
    for (int year = fromYear; year <= toYear; year++) {
      result.addAll(closureDatesForYear(year));
    }
    return result;
  }

  /**
   * Returns the closures of the given year together with the name of the rule that produced each one. The comparison
   * report uses the names to explain why a date was expected to be a closure.
   *
   * <p>
   * When two rules resolve to the same date -- which happens for instance when a fixed national day coincides with an
   * Easter-relative holiday -- both names are kept, separated by a slash, so the overlap stays visible instead of one
   * rule silently masking the other.
   * </p>
   *
   * @param year the year for which the closures are wanted
   * @return map from closure date to rule name, in ascending date order, never null
   */
  public SortedMap<LocalDate, String> closuresWithRuleName(int year) {
    requireAuthoritativeCoverage(year);
    SortedMap<LocalDate, String> result = new TreeMap<>();
    for (HolidayRule rule : rules) {
      for (LocalDate date : rule.resolve(year)) {
        result.merge(date, rule.getName(), (existing, added) -> existing + "/" + added);
      }
    }
    openDates.stream().filter(date -> date.getYear() == year).forEach(result::remove);
    additionalClosures.stream().filter(date -> date.getYear() == year)
        .forEach(date -> result.merge(date, "ExplicitClosure", (existing, added) -> existing + "/" + added));
    return result;
  }

  /**
   * Returns the days in the given year on which the exchange only shortens its trading hours. These are not closures
   * and are never part of {@link #closureDatesForYear}, but the comparison report uses them to explain a disagreement:
   * a shortened day sometimes yields no price data and is then wrongly recorded as a closure.
   *
   * @param year the year for which the half days are wanted
   * @return map from date to rule name, in ascending date order, never null
   */
  public SortedMap<LocalDate, String> halfDaysWithRuleName(int year) {
    requireAuthoritativeCoverage(year);
    SortedMap<LocalDate, String> result = new TreeMap<>();
    for (HolidayRule rule : rules) {
      if (!rule.isHalfDay()) {
        continue;
      }
      for (LocalDate date : rule.resolveIgnoringHalfDayFlag(year)) {
        result.merge(date, rule.getName(), (existing, added) -> existing + "/" + added);
      }
    }
    return result;
  }

  /**
   * Validates every rule of this set. Whether a set without rules is acceptable is decided by the caller, because an
   * empty set is valid exactly when the row extends another one, which is not visible here.
   *
   * @throws IllegalStateException when the set or one of its rules is malformed
   */
  public void validate() {
    if (authoritativeFrom != null && authoritativeThrough != null && authoritativeFrom > authoritativeThrough) {
      throw new IllegalStateException("authoritativeFrom is after authoritativeThrough");
    }
    if (new TreeSet<>(additionalClosures).size() != additionalClosures.size()) {
      throw new IllegalStateException("additionalClosures contains a duplicate date");
    }
    if (new TreeSet<>(openDates).size() != openDates.size()) {
      throw new IllegalStateException("openDates contains a duplicate date");
    }
    if (additionalClosures.stream().anyMatch(openDates::contains)) {
      throw new IllegalStateException("a date appears in both additionalClosures and openDates");
    }
    rules.forEach(HolidayRule::validate);
  }

  /**
   * Whether this set contributes nothing of its own, which is only acceptable when it extends another set.
   *
   * @return true when the set has neither rules nor date level corrections
   */
  public boolean isEmpty() {
    return rules.isEmpty() && additionalClosures.isEmpty() && openDates.isEmpty();
  }

  /**
   * Whether this rule set is backed by an authoritative source for the requested year.
   *
   * @param year the year to check
   * @return true when the year lies within the optional authoritative bounds
   */
  public boolean isAuthoritativeForYear(int year) {
    return (authoritativeFrom == null || year >= authoritativeFrom)
        && (authoritativeThrough == null || year <= authoritativeThrough);
  }

  private void requireAuthoritativeCoverage(int year) {
    if (!isAuthoritativeForYear(year)) {
      throw new IllegalStateException(
          "Holiday rule set is not authoritative for " + year + formatAuthoritativeRange());
    }
  }

  private String formatAuthoritativeRange() {
    String from = authoritativeFrom == null ? "unbounded" : authoritativeFrom.toString();
    String through = authoritativeThrough == null ? "unbounded" : authoritativeThrough.toString();
    return " (authoritative range " + from + "-" + through + ")";
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Integer getAuthoritativeFrom() {
    return authoritativeFrom;
  }

  public void setAuthoritativeFrom(Integer authoritativeFrom) {
    this.authoritativeFrom = authoritativeFrom;
  }

  public Integer getAuthoritativeThrough() {
    return authoritativeThrough;
  }

  public void setAuthoritativeThrough(Integer authoritativeThrough) {
    this.authoritativeThrough = authoritativeThrough;
  }

  public List<HolidayRule> getRules() {
    return rules;
  }

  public void setRules(List<HolidayRule> rules) {
    this.rules = rules == null ? new ArrayList<>() : rules;
  }

  public List<LocalDate> getAdditionalClosures() {
    return additionalClosures;
  }

  public void setAdditionalClosures(List<LocalDate> additionalClosures) {
    this.additionalClosures = additionalClosures == null ? new ArrayList<>() : additionalClosures;
  }

  public List<LocalDate> getOpenDates() {
    return openDates;
  }

  public void setOpenDates(List<LocalDate> openDates) {
    this.openDates = openDates == null ? new ArrayList<>() : openDates;
  }

  @Override
  public String toString() {
    return rules.size() + " rules, " + additionalClosures.size() + " additional closures";
  }
}
