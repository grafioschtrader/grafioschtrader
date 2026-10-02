package grafioschtrader.reportviews.performance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.MoneyWeightedReturn;
import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
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
 * <li>Relative return, risk and cost figures ({@link PerformancePeriodMetrics}), and the time-weighted return of every
 * window and every step</li>
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

  @Schema(description = "Return, risk and cost figures of the whole period; null while no holdings exist.")
  private PerformancePeriodMetrics metrics;

  @JsonIgnore
  private ReturnSeries returnSeries;

  @JsonIgnore
  private List<Double> externalCashTransfers = List.of();

  /** Exact chart valuations, kept out of the REST representation. */
  @JsonIgnore
  public ReturnSeries getReturnSeries() {
    return returnSeries;
  }

  /** Cumulative external flows at the same indexes as the retained valuation series. */
  @JsonIgnore
  public List<Double> getExternalCashTransfers() {
    return externalCashTransfers;
  }

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
   * <p>
   * Beforehand the daily holdings are turned into the time-weighted series of the report, from which every step and
   * every window receives its return and the metrics of the whole period are derived.
   * </p>
   *
   * @param firstAndMissingTradingDays trading day metadata for handling holidays and missing data
   * @param periodHoldings             list of daily holding data for the analysis period, the first one is the excluded
   *                                   base
   * @param dailyFlows                 net external flows per calendar day of the scope, ascending by date
   * @param feesMC                     separately booked fees of the period in main currency, a charge is positive
   */
  public void createPeriodWindows(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, List<IDailyExternalFlow> dailyFlows, double feesMC) {
    createPeriodWindows(firstAndMissingTradingDays, periodHoldings, dailyFlows, feesMC, 0);
  }

  /** Builds the screen figures and records fee bookings omitted for lack of an exchange rate. */
  public void createPeriodWindows(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, List<IDailyExternalFlow> dailyFlows, double feesMC, int skippedFees) {
    ReturnSeries series = ReturnSeries.of(toValuationPoints(periodHoldings, dailyFlows, firstAndMissingTradingDays),
        Convention.REPORT);
    returnSeries = series;
    externalCashTransfers = periodHoldings.stream().map(IPeriodHolding::getExternalCashTransferMC).toList();
    if (periodSplit == WeekYear.WM_WEEK) {
      createPeriodWindowsYear(WeekYear.WM_WEEK, firstAndMissingTradingDays, periodHoldings, null, series);
    } else {
      createPeriodWindowsYear(WeekYear.WM_YEAR, firstAndMissingTradingDays, periodHoldings, new FilterLastDayMonth(),
          series);
    }
    periodWindows.forEach(periodWindow -> periodWindow.fillTwrPercent(series));
    createMetrics(firstAndMissingTradingDays, periodHoldings, dailyFlows, feesMC, series, skippedFees);
  }

  /**
   * Turns the daily holdings into the valuation points of the time-weighted series. The value includes the open result
   * of margin positions. The net flow of an interval comes from the cumulative external cash transfer of the holdings;
   * the dated flows only tell how much of it was withdrawn, so that the withdrawals count as flowed at the end and the
   * rest as flowed at the start of the interval.
   */
  private List<ValuationPoint> toValuationPoints(List<IPeriodHolding> periodHoldings,
      List<IDailyExternalFlow> dailyFlows, FirstAndMissingTradingDays fmtd) {
    List<ValuationPoint> points = new ArrayList<>(periodHoldings.size());
    int flowIndex = 0;
    for (int i = 0; i < periodHoldings.size(); i++) {
      IPeriodHolding periodHolding = periodHoldings.get(i);
      LocalDate date = periodHolding.getDate();
      if (i == 0) {
        points.add(new ValuationPoint(date, totalValue(periodHolding), 0, 0, true));
        continue;
      }
      LocalDate dateBefore = periodHoldings.get(i - 1).getDate();
      double outflow = 0;
      for (; flowIndex < dailyFlows.size() && !dailyFlows.get(flowIndex).getFlowDate().isAfter(date); flowIndex++) {
        IDailyExternalFlow flow = dailyFlows.get(flowIndex);
        if (flow.getFlowDate().isAfter(dateBefore) && flow.getFlowMC() < 0) {
          outflow -= flow.getFlowMC();
        }
      }
      double netFlow = periodHolding.getExternalCashTransferMC()
          - periodHoldings.get(i - 1).getExternalCashTransferMC();
      points.add(new ValuationPoint(date, totalValue(periodHolding), Math.max(0, netFlow + outflow), outflow,
          noExpectedTradingDayBetween(dateBefore, date, fmtd)));
    }
    return points;
  }

  private static double totalValue(IPeriodHolding periodHolding) {
    return periodHolding.getCashBalanceMC() + periodHolding.getSecuritiesMC() + periodHolding.getMarginCloseGainMC();
  }

  /** An expected trading day is a weekday that is no holiday of the held instruments. */
  private static boolean isExpectedTradingDay(LocalDate date, FirstAndMissingTradingDays fmtd) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
        && !fmtd.isHoliday(date);
  }

  /**
   * Whether no expected trading day lies strictly between the two dates, i.e. the interval misses no valuation.
   * Weekends and holidays do not make an interval irregular, a day with missing prices does.
   */
  private static boolean noExpectedTradingDayBetween(LocalDate from, LocalDate to, FirstAndMissingTradingDays fmtd) {
    for (LocalDate date = from.plusDays(1); date.isBefore(to); date = date.plusDays(1)) {
      if (isExpectedTradingDay(date, fmtd)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether a step covers exactly what its column stands for. In the weekly split that is a single trading day without
   * gap. In the yearly split it is one whole month: the base is the last expected trading day of the previous month and
   * the step ends on the last expected trading day of its month. A month whose last valuation is missing therefore
   * makes both its own step and the following one incomplete.
   */
  private static boolean isCompleteStep(WeekYear weekYear, LocalDate baseDate, LocalDate lastDate,
      FirstAndMissingTradingDays fmtd) {
    if (weekYear == WeekYear.WM_WEEK) {
      return noExpectedTradingDayBetween(baseDate, lastDate, fmtd);
    }
    return YearMonth.from(baseDate).plusMonths(1).equals(YearMonth.from(lastDate))
        && isLastExpectedTradingDayOfMonth(baseDate, fmtd) && isLastExpectedTradingDayOfMonth(lastDate, fmtd);
  }

  private static boolean isLastExpectedTradingDayOfMonth(LocalDate date, FirstAndMissingTradingDays fmtd) {
    return noExpectedTradingDayBetween(date, date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1), fmtd);
  }

  /**
   * Derives the figures of the whole period from the series, the money-weighted return of the dated flows and the steps
   * of all windows.
   */
  private void createMetrics(FirstAndMissingTradingDays fmtd, List<IPeriodHolding> periodHoldings,
      List<IDailyExternalFlow> dailyFlows, double feesMC, ReturnSeries series, int skippedFees) {
    IPeriodHolding first = periodHoldings.getFirst();
    IPeriodHolding last = periodHoldings.getLast();
    List<DatedFlow> flows = dailyFlows.stream().map(f -> new DatedFlow(f.getFlowDate(), f.getFlowMC())).toList();
    MoneyWeightedReturn.Result mwr = MoneyWeightedReturn.solve(first.getDate(), totalValue(first), last.getDate(),
        totalValue(last), flows);
    int expectedSessions = 0;
    for (LocalDate date = first.getDate().plusDays(1); !date.isAfter(last.getDate()); date = date.plusDays(1)) {
      if (isExpectedTradingDay(date, fmtd)) {
        expectedSessions++;
      }
    }
    List<PeriodStep> steps = periodWindows.stream().flatMap(periodWindow -> periodWindow.periodStepList.stream())
        .filter(PeriodStep.class::isInstance).map(PeriodStep.class::cast).toList();
    metrics = PerformancePeriodMetrics.of(series, mwr, expectedSessions, steps, feesMC, skippedFees);
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
   * @param series                     time-weighted series of the holdings, same indexes as periodHoldings
   */
  private void createPeriodWindowsYear(WeekYear weekYear, FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, IFilterPeriodDay filter, ReturnSeries series) {
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

      if (firstAndMissingTradingDays.isHoliday(weekDay)
          && (periodHolding == null || !periodHolding.getDate().isEqual(weekDay))) {
        periodWindow.addPeriodStepHolidayMissing(weekYear, weekDay, HolidayMissing.HM_HOLIDAY);
      } else if (firstAndMissingTradingDays.isMissingQuote(weekDay)) {
        periodWindow.addPeriodStepHolidayMissing(weekYear, weekDay, HolidayMissing.HM_HISTORY_DATA_MISSING);
        missingDayCountFromDayToDay++;
      } else if (periodHolding != null) {

        if (periodHolding.getDate().isEqual(weekDay)) {
          // Should have history data
          if (filter == null || filter.nextPeriodStartsNewWindow(periodHoldings, periodHolding.getDate(), phIndex)) {

            lastDayWeekGainMap.put(weekStartDay, totalGainMC(periodHolding));
            if (phIndex > 0) {
              IPeriodHolding pHDayBefore = periodHoldings.get(lastIndex);
              LocalDate baseDate = pHDayBefore.getDate();
              periodWindow.registerStepIndexes(lastIndex, phIndex);
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
                  missingDayCountFromDayToDay, baseDate,
                  PerformancePeriodMetrics.percent(series.linked(lastIndex, phIndex)),
                  isCompleteStep(weekYear, baseDate, weekDay, firstAndMissingTradingDays));
              lastIndex = phIndex;
              int columnIndex = (weekYear == WeekYear.WM_WEEK) ? weekDay.getDayOfWeek().getValue() - 1
                  : weekDay.getMonthValue() - 1;
              sumPeriodColSteps[columnIndex] += totalGainMC(periodHolding) - totalGainMC(pHDayBefore);

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

  /** Gain including the open result of margin positions, the figure every cell of the period table shows. */
  private static double totalGainMC(IPeriodHolding periodHolding) {
    return periodHolding.getGainMC() + periodHolding.getMarginCloseGainMC();
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

  public PerformancePeriodMetrics getMetrics() {
    return metrics;
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
