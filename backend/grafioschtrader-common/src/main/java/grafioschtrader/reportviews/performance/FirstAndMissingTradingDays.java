package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafioschtrader.GlobalConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Comprehensive trading day metadata for performance report validation and date selection.
 * 
 * <p>
 * This immutable class contains all necessary trading day information required for performance analysis, including data
 * boundaries, holiday calendars, missing quote detection, and period limits. It serves as the foundation for validating
 * user date selections and ensuring data quality in performance calculations.
 * </p>
 * 
 * <p>
 * Key components include:
 * </p>
 * <ul>
 * <li>Trading day boundaries (first, latest, historical reference points)</li>
 * <li>Holiday and missing quote calendars for data quality validation</li>
 * <li>Period limits for appropriate aggregation level selection</li>
 * <li>Utility methods for date validation and calendar checking</li>
 * </ul>
 */
@Schema(description = "For the period performance report, certain dates are required for the date selection by the user.")
public class FirstAndMissingTradingDays {

  @Schema(description = "Maximum number of weeks allowed for weekly period performance analysis")
  public final int maxWeekLimit = GlobalConstants.PERFORMANCE_MAX_WEEK_LIMIT;

  @Schema(description = "Minimum number of months required for yearly period performance analysis")
  public final int minIncludeMonthLimit = GlobalConstants.PERFORMANCE_MIN_INCLUDE_MONTH_LIMIT;

  @Schema(description = "The earliest trading day in the complete holdings history")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate firstEverTradingDay;

  @Schema(description = "The second trading day in the holdings history, used for baseline calculations")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate secondEverTradingDay;

  @Schema(description = "The last valid trading day of the previous year for reference analysis")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate lastTradingDayOfLastYear;

  @Schema(description = "The second most recent trading day with complete data")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate secondLatestTradingDay;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate latestTradingDay;

  @Schema(description = "Most recent date for which the data is complete")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate leatestPossibleTradingDay;

  /**
   * Set of all holidays affecting the holdings in the analysis scope.
   * 
   * <p>
   * Contains both global holidays (such as New Year's Day and Christmas) and security-specific market closures based on
   * the exchanges where held securities are traded. This set is used internally for trading day validation and is
   * excluded from JSON serialization to reduce API response size and avoid exposing internal validation data.
   */
  @JsonIgnore
  public final Set<Date> allHolydays;

  /**
   * Set of trading days where end-of-day price quotes are missing for held securities.
   * 
   * <p>
   * Identifies specific dates where historical price data is incomplete or unavailable, preventing accurate performance
   * calculations. Missing quotes can result from data provider issues, market disruptions, trading halts, or incomplete
   * data loading processes. This set is used internally for data quality validation and is excluded from JSON
   * serialization.
   * </p>
   */
  @JsonIgnore
  public final Set<Date> missingQuoteDays;

  /**
   * Constructs a new trading day metadata instance with all required boundaries and calendars.
   * 
   * @param firstEverTradingDay       the earliest trading day in holdings history
   * @param secondEverTradingDay      the second trading day for baseline calculations
   * @param lastTradingDayOfLastYear  the last valid trading day of previous year
   * @param leatestPossibleTradingDay the most recent date with complete data (holidays only)
   * @param latestTradingDay          the most recent trading day with complete quote data
   * @param secondLatestTradingDay    the second most recent trading day with complete data
   * @param allHolydays               set of all holidays affecting the holdings
   * @param missingQuoteDays          set of days with missing price quotes
   */
  public FirstAndMissingTradingDays(LocalDate firstEverTradingDay, LocalDate secondEverTradingDay,
      LocalDate lastTradingDayOfLastYear, LocalDate leatestPossibleTradingDay, LocalDate latestTradingDay,
      LocalDate secondLatestTradingDay, Set<Date> allHolydays, Set<Date> missingQuoteDays) {
    super();
    this.firstEverTradingDay = firstEverTradingDay;
    this.secondEverTradingDay = secondEverTradingDay;
    this.lastTradingDayOfLastYear = lastTradingDayOfLastYear;
    this.leatestPossibleTradingDay = leatestPossibleTradingDay;
    this.latestTradingDay = latestTradingDay;
    this.secondLatestTradingDay = secondLatestTradingDay;
    this.allHolydays = allHolydays;
    this.missingQuoteDays = missingQuoteDays;
  }

  @Schema(description = """
      Returns a combined and sorted list of all holidays and missing quote days.
      This method merges both holiday and missing quote calendars into a single sorted list,
      providing a comprehensive view of all dates that should be excluded from performance
      analysis. Useful for UI components that need to display blocked dates to users.
      Return sorted list of all non-trading days (holidays and missing quote days combined""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public List<Date> getHolidayAndMissingQuoteDays() {
    Set<Date> combinedMissingQuoteDaysAndHoliday = new HashSet<>(allHolydays);
    combinedMissingQuoteDaysAndHoliday.addAll(missingQuoteDays);
    return combinedMissingQuoteDaysAndHoliday.stream().sorted().collect(Collectors.toList());
  }

  public boolean isMissingQuoteDayOrHoliday(Date date) {
    return allHolydays.contains(date) || missingQuoteDays.contains(date);
  }

  public boolean isHoliday(Date date) {
    return allHolydays.contains(date);
  }

  public boolean isMissingQuote(Date date) {
    return missingQuoteDays.contains(date);
  }

}
