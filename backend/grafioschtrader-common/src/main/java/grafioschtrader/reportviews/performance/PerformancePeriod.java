package grafioschtrader.reportviews.performance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contains comprehensive data for period performance analysis including aggregated metrics, daily changes, and
 * structured period windows for visualization and reporting.
 * 
 * <p>
 * This class serves as the primary data container for performance analysis results, organizing data into multiple
 * views:
 * </p>
 * <ul>
 * <li>Summary totals for period start and end</li>
 * <li>Daily performance differences for charting</li>
 * <li>Structured period windows (weekly or yearly aggregation)</li>
 * <li>Column-wise summaries for tabular display</li>
 * </ul>
 * 
 * <p>
 * <strong>Period Aggregation:</strong>
 * </p>
 * <p>
 * Supports two aggregation modes controlled by the periodSplit parameter:
 * </p>
 * <ul>
 * <li><strong>Weekly (WM_WEEK):</strong> Groups data into 5-day trading weeks</li>
 * <li><strong>Yearly (WM_YEAR):</strong> Groups data into 12-month periods</li>
 * </ul>
 * 
 * <p>
 * <strong>Data Processing:</strong>
 * </p>
 * <p>
 * The class handles missing trading days, holidays, and data gaps by incorporating information from
 * FirstAndMissingTradingDays to ensure accurate period calculations.
 * </p>
 */
@Schema(description = "Contains the data for period performance")
public class PerformancePeriod {
  /** Number of trading days in a standard work week. */
  public final static int PERIOD_WEEK = 5;
  /** Number of months in a year for yearly aggregation. */
  public final static int PERIOD_YEAR = 12;
  /** The aggregation level for this performance period (weekly or yearly). */
  private final WeekYear periodSplit;

  @Schema(description = "Totals of the first day in the period")
  private final PeriodHoldingAndDiff firstDayTotals;

  @Schema(description = "Totals of the last in the period")
  private final PeriodHoldingAndDiff lastDayTotals;

  @Schema(description = "The difference between the first and last day of the period")
  private final PeriodHoldingAndDiff difference;

  @Schema(description = "Array storing column-wise summaries for tabular display (5 elements for weeks, 12 for months).")
  private final double[] sumPeriodColSteps;

  @Schema(description = "List of daily performance differences for chart visualization.")
  private List<PerformanceChartDayDiff> performanceChartDayDiff = new ArrayList<>();

  @Schema(description = "List of structured period windows containing aggregated data for each time segment.")
  private List<PeriodWindow> periodWindows = new ArrayList<>();

  @Schema(description = "First chart day difference for baseline reference.")
  private PerformanceChartDayDiff firstChartDayDiff;

  /**
   * Creates a new performance period analysis with the specified parameters.
   * 
   * @param periodSplit    the aggregation level (weekly or yearly)
   * @param firstDayTotals aggregated values for the first day of the period
   * @param lastDayTotals  aggregated values for the last day of the period
   * @param difference     calculated differences between first and last day
   */
  public PerformancePeriod(WeekYear periodSplit, PeriodHoldingAndDiff firstDayTotals,
      PeriodHoldingAndDiff lastDayTotals, PeriodHoldingAndDiff difference) {
    super();
    this.periodSplit = periodSplit;
    this.firstDayTotals = firstDayTotals;
    this.lastDayTotals = lastDayTotals;
    this.difference = difference;
    this.sumPeriodColSteps = new double[periodSplit == WeekYear.WM_WEEK ? PerformancePeriod.PERIOD_WEEK
        : PerformancePeriod.PERIOD_YEAR];
  }

  /**
   * Adds a daily performance data point to the chart difference list.
   * 
   * <p>
   * For the first entry, creates a baseline with the holding date. For subsequent entries, calculates differences from
   * the first day totals across all performance metrics.
   * </p>
   * 
   * @param periodHolding the daily holding data to process, or null to skip
   */
  public void addPerformceChartDayDiff(IPeriodHolding periodHolding) {
    if (periodHolding != null) {
      if (performanceChartDayDiff.isEmpty()) {
        performanceChartDayDiff.add(new PerformanceChartDayDiff(periodHolding.getDate()));
      } else {
        performanceChartDayDiff.add(new PerformanceChartDayDiff(periodHolding.getDate(),
            DataBusinessHelper
                .roundStandard(periodHolding.getExternalCashTransferMC() - firstDayTotals.getExternalCashTransferMC()),
            DataBusinessHelper.roundStandard(periodHolding.getGainMC() - firstDayTotals.getGainMC()),
            DataBusinessHelper.roundStandard(periodHolding.getCashBalanceMC() - firstDayTotals.getCashBalanceMC()),
            DataBusinessHelper.roundStandard(periodHolding.getSecuritiesMC() - firstDayTotals.getSecuritiesMC()),
            DataBusinessHelper.roundStandard(periodHolding.getSecuritiesMC() - firstDayTotals.getSecuritiesMC()
                + periodHolding.getCashBalanceMC() - firstDayTotals.getCashBalanceMC())));
      }
    }
  }

  /**
   * Creates structured period windows for the performance analysis.
   * 
   * <p>
   * Delegates to the appropriate window creation method based on the period split:
   * </p>
   * <ul>
   * <li>Weekly aggregation: Creates 5-day trading week windows</li>
   * <li>Yearly aggregation: Creates monthly windows with last-day-of-month filtering</li>
   * </ul>
   * 
   * @param firstAndMissingTradingDays trading day metadata for handling holidays and missing data
   * @param periodHoldings             list of daily holding data for the analysis period
   */
  public void createPeriodWindows(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings) {
    if (periodSplit == WeekYear.WM_WEEK) {
      createPeriodWindowsYear(WeekYear.WM_WEEK, firstAndMissingTradingDays, periodHoldings, null);
    } else {
      createPeriodWindowsYear(WeekYear.WM_YEAR, firstAndMissingTradingDays, periodHoldings, new FilterLastDayMonth());
    }
  }

  /**
   * Core method for creating period windows with comprehensive trading day handling.
   * 
   * <p>
   * This method processes each trading day in the period and:
   * </p>
   * <ul>
   * <li>Creates new period windows when crossing week/year boundaries</li>
   * <li>Handles holidays and missing data appropriately</li>
   * <li>Calculates daily performance differences</li>
   * <li>Aggregates column-wise summaries</li>
   * <li>Manages period-to-period gain calculations</li>
   * </ul>
   * 
   * @param weekYear                   the aggregation level being processed
   * @param firstAndMissingTradingDays trading day metadata for validation
   * @param periodHoldings             list of daily holding data
   * @param filter                     optional filter for determining period boundaries (used for monthly aggregation)
   */
  private void createPeriodWindowsYear(WeekYear weekYear, FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, IFilterPeriodDay filter) {
    List<LocalDate> weekDays = LongStream
        .range(firstDayTotals.getDate().toEpochDay(), lastDayTotals.getDate().toEpochDay() + 1)
        .mapToObj(LocalDate::ofEpochDay)
        .filter(
            localDate -> localDate.getDayOfWeek() != DayOfWeek.SATURDAY && localDate.getDayOfWeek() != DayOfWeek.SUNDAY)
        .collect(Collectors.toList());

    PeriodWindow periodWindow = null;
    int phIndex = 0;
    int missingDayCountFromDayToDay = 0;
    int lastIndex = 0;

    Map<LocalDate, Double> lastDayWeekGainMap = new HashMap<>();
    LocalDate weekStartDay = null;
    IPeriodHolding periodHolding = phIndex < periodHoldings.size() ? periodHoldings.get(phIndex) : null;
    addPerformceChartDayDiff(periodHolding);
    for (LocalDate weekDay : weekDays) {

      if (periodWindow == null || weekDay.isAfter(periodWindow.endDate)) {
        // Start with a new period -> year or week
        if (missingDayCountFromDayToDay == 0) {
          if (periodWindows.size() >= 2) {
            calculateGainPeriodMC(periodWindows.get(periodWindows.size() - 2),
                periodWindows.get(periodWindows.size() - 1), lastDayWeekGainMap);
          } else if (periodWindows.size() == 1) {
            periodWindows.get(0).fillMissinGainPeriodMCByPeriodStep();
          }
        }
        weekStartDay = weekYear == WeekYear.WM_WEEK ? weekDay.with(DayOfWeek.MONDAY) : weekDay.withDayOfYear(1);
        periodWindow = new PeriodWindow(weekYear, weekStartDay,
            weekYear == WeekYear.WM_WEEK ? weekDay.with(DayOfWeek.FRIDAY)
                : weekStartDay.with(TemporalAdjusters.lastDayOfYear()));
        periodWindows.add(periodWindow);
      }

      Date date = Date.from(weekDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
      if (firstAndMissingTradingDays.isHoliday(date)
          && (periodHolding == null || !periodHolding.getDate().isEqual(weekDay))) {
        periodWindow.addPeriodStepHolidayMissing(weekYear, weekDay, HolidayMissing.HM_HOLIDAY);
      } else if (firstAndMissingTradingDays.isMissingQuote(date)) {
        periodWindow.addPeriodStepHolidayMissing(weekYear, weekDay, HolidayMissing.HM_HISTORY_DATA_MISSING);
        missingDayCountFromDayToDay++;
      } else if (periodHolding != null) {

        if (periodHolding.getDate().isEqual(weekDay)) {
          // Should have history data
          if (filter == null || filter.nextPeriodStartsNewWindow(periodHoldings, periodHolding.getDate(), phIndex)) {

            lastDayWeekGainMap.put(weekStartDay, periodHolding.getGainMC());
            if (phIndex > 0) {
              IPeriodHolding pHDayBefore = periodHoldings.get(lastIndex);
              periodWindow.addPeriodStep(weekYear, weekDay,
                  DataBusinessHelper.roundStandard(
                      periodHolding.getExternalCashTransferMC() - pHDayBefore.getExternalCashTransferMC()),
                  DataBusinessHelper.roundStandard(periodHolding.getGainMC() - pHDayBefore.getGainMC()),
                  DataBusinessHelper
                      .roundStandard(periodHolding.getMarginCloseGainMC() - pHDayBefore.getMarginCloseGainMC()),
                  DataBusinessHelper.roundStandard(periodHolding.getCashBalanceMC() - pHDayBefore.getCashBalanceMC()),
                  DataBusinessHelper.roundStandard(periodHolding.getSecuritiesMC() - pHDayBefore.getSecuritiesMC()),
                  DataBusinessHelper.roundStandard(periodHolding.getSecuritiesMC() - pHDayBefore.getSecuritiesMC()
                      + periodHolding.getCashBalanceMC() - pHDayBefore.getCashBalanceMC()),
                  missingDayCountFromDayToDay);
              lastIndex = phIndex;
              int columnIndex = (weekYear == WeekYear.WM_WEEK) ? weekDay.getDayOfWeek().getValue() - 1
                  : weekDay.getMonthValue() - 1;
              sumPeriodColSteps[columnIndex] += periodHolding.getGainMC() - pHDayBefore.getGainMC();

            }
            missingDayCountFromDayToDay = 0;
          }
          phIndex++;
          periodHolding = phIndex < periodHoldings.size() ? periodHoldings.get(phIndex) : null;
          addPerformceChartDayDiff(periodHolding);
        }
      }

    }
    if (periodWindows.size() >= 2) {
      periodWindows.get(periodWindows.size() - 1).fillMissinGainPeriodMCByPeriodStep();
    }
  }

  /**
   * Calculates the gain for a period by comparing it with the previous period.
   * 
   * <p>
   * Updates the current period's gainPeriodMC field with the difference between its gain and the previous period's
   * gain. Cleans up the tracking map to prevent memory leaks.
   * </p>
   * 
   * @param periodWindowBefore the previous period window for comparison
   * @param periodWindow       the current period window to update
   * @param lastDayWeekGainMap tracking map of period start dates to gain values
   */
  private void calculateGainPeriodMC(PeriodWindow periodWindowBefore, PeriodWindow periodWindow,
      Map<LocalDate, Double> lastDayWeekGainMap) {
    Double beforePeriodGainMC = lastDayWeekGainMap.get(periodWindowBefore.startDate);
    Double periodGainMC = lastDayWeekGainMap.get(periodWindow.startDate);
    if (beforePeriodGainMC != null && periodGainMC != null) {
      periodWindow.gainPeriodMC = DataBusinessHelper.roundStandard(periodGainMC - beforePeriodGainMC);
    }
    lastDayWeekGainMap.remove(periodWindowBefore.startDate);
  }

  public PerformanceChartDayDiff getFirstChartDayDiff() {
    return firstChartDayDiff;
  }

  public void setFirstChartDayDiff(PerformanceChartDayDiff firstChartDayDiff) {
    this.firstChartDayDiff = firstChartDayDiff;
  }

  public WeekYear getPeriodSplit() {
    return periodSplit;
  }

  public PeriodHoldingAndDiff getFirstDayTotals() {
    return firstDayTotals;
  }

  public PeriodHoldingAndDiff getLastDayTotals() {
    return lastDayTotals;
  }

  public PeriodHoldingAndDiff getDifference() {
    return difference;
  }

  public double[] getSumPeriodColSteps() {
    for (int i = 0; i < sumPeriodColSteps.length; i++) {
      sumPeriodColSteps[i] = DataBusinessHelper.roundStandard(sumPeriodColSteps[i]);
    }
    return sumPeriodColSteps;
  }

  public List<PerformanceChartDayDiff> getPerformanceChartDayDiff() {
    return performanceChartDayDiff;
  }

  public List<PeriodWindow> getPeriodWindows() {
    return periodWindows;
  }

  /**
   * Strategy interface for determining when a new period window should be created.
   * 
   * <p>
   * Implementations define the logic for period boundaries based on the aggregation level and business rules.
   * </p>
   */
  static interface IFilterPeriodDay {
    /**
     * Determines if the next period should start a new window.
     * 
     * @param periodHoldings the complete list of period holdings
     * @param askedDay       the current day being processed
     * @param index          the current index in the holdings list
     * @return true if a new period window should start
     */
    boolean nextPeriodStartsNewWindow(List<IPeriodHolding> periodHoldings, LocalDate askedDay, int index);
  }

  /**
   * Filter implementation for monthly aggregation that creates new windows when crossing month boundaries.
   * 
   * <p>
   * This filter ensures that each month gets its own period window by detecting changes in month or year values between
   * consecutive holdings.
   * </p>
   */
  static class FilterLastDayMonth implements IFilterPeriodDay {

    /**
     * Creates a new window for the first and last elements, and when the next element has a different month or year.
     * 
     * @param periodHoldings the complete list of period holdings
     * @param askedDay       the current day being processed
     * @param index          the current index in the holdings list
     * @return true if this should start a new period window
     */
    @Override
    public boolean nextPeriodStartsNewWindow(List<IPeriodHolding> periodHoldings, LocalDate askedDay, int index) {
      if (index == 0) {
        return true;
      } else if (index == periodHoldings.size() - 1) {
        return true;
      } else {
        IPeriodHolding ph = periodHoldings.get(index + 1);
        return ph.getDate().getMonthValue() != askedDay.getMonthValue() || ph.getDate().getYear() != askedDay.getYear();
      }
    }

  }

}
