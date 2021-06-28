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

import grafioschtrader.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains the data for period performance")
public class PerformancePeriod {
  public final static int PERIOD_WEEK = 5;
  public final static int PERIOD_YEAR = 12;

  private final WeekYear periodSplit;

  @Schema(description = "Totals of the first day in the period")
  private final PeriodHoldingAndDiff firstDayTotals;

  @Schema(description = "Totals of the last in the period")
  private final PeriodHoldingAndDiff lastDayTotals;

  @Schema(description = "The difference between the first and last day of the period")
  private final PeriodHoldingAndDiff difference;
  private final double[] sumPeriodColSteps;
  private List<PerformanceChartDayDiff> performanceChartDayDiff = new ArrayList<>();
  private List<PeriodWindow> periodWindows = new ArrayList<>();

  private PerformanceChartDayDiff firstChartDayDiff;

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

  public void addPerformceChartDayDiff(IPeriodHolding periodHolding) {
    if (periodHolding != null) {
      if (performanceChartDayDiff.isEmpty()) {
        performanceChartDayDiff.add(new PerformanceChartDayDiff(periodHolding.getDate()));
      } else {
        performanceChartDayDiff.add(new PerformanceChartDayDiff(periodHolding.getDate(),
            DataHelper.roundStandard(periodHolding.getExternalCashTransferMC() - firstDayTotals.externalCashTransferMC),
            DataHelper.roundStandard(periodHolding.getGainMC() - firstDayTotals.gainMC),
            DataHelper.roundStandard(periodHolding.getCashBalanceMC() - firstDayTotals.cashBalanceMC),
            DataHelper.roundStandard(periodHolding.getSecuritiesMC() - firstDayTotals.securitiesMC),
            DataHelper.roundStandard(periodHolding.getSecuritiesMC() - firstDayTotals.securitiesMC
                + periodHolding.getCashBalanceMC() - firstDayTotals.cashBalanceMC)));
      }
    }
  }

  public void createPeriodWindows(FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings) {
    if (periodSplit == WeekYear.WM_WEEK) {
      createPeriodWindowsYear(WeekYear.WM_WEEK, firstAndMissingTradingDays, periodHoldings, null);
    } else {
      createPeriodWindowsYear(WeekYear.WM_YEAR, firstAndMissingTradingDays, periodHoldings, new FilterLastDayMonth());
    }
  }

  private void createPeriodWindowsYear(WeekYear weekYear, FirstAndMissingTradingDays firstAndMissingTradingDays,
      List<IPeriodHolding> periodHoldings, IFilterPeriodDay filter) {
    List<LocalDate> weekDays = LongStream.range(firstDayTotals.date.toEpochDay(), lastDayTotals.date.toEpochDay() + 1)
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
                  DataHelper.roundStandard(
                      periodHolding.getExternalCashTransferMC() - pHDayBefore.getExternalCashTransferMC()),
                  DataHelper.roundStandard(periodHolding.getGainMC() - pHDayBefore.getGainMC()),
                  DataHelper.roundStandard(periodHolding.getMarginCloseGainMC() - pHDayBefore.getMarginCloseGainMC()),
                  DataHelper.roundStandard(periodHolding.getCashBalanceMC() - pHDayBefore.getCashBalanceMC()),
                  DataHelper.roundStandard(periodHolding.getSecuritiesMC() - pHDayBefore.getSecuritiesMC()),
                  DataHelper.roundStandard(periodHolding.getSecuritiesMC() - pHDayBefore.getSecuritiesMC()
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

  private void calculateGainPeriodMC(PeriodWindow periodWindowBefore, PeriodWindow periodWindow,
      Map<LocalDate, Double> lastDayWeekGainMap) {
    Double beforePeriodGainMC = lastDayWeekGainMap.get(periodWindowBefore.startDate);
    Double periodGainMC = lastDayWeekGainMap.get(periodWindow.startDate);
    if (beforePeriodGainMC != null && periodGainMC != null) {
      periodWindow.gainPeriodMC = DataHelper.roundStandard(periodGainMC - beforePeriodGainMC);
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
      sumPeriodColSteps[i] = DataHelper.roundStandard(sumPeriodColSteps[i]);
    }
    return sumPeriodColSteps;
  }

  public List<PerformanceChartDayDiff> getPerformanceChartDayDiff() {
    return performanceChartDayDiff;
  }

  public List<PeriodWindow> getPeriodWindows() {
    return periodWindows;
  }

  static interface IFilterPeriodDay {
    boolean nextPeriodStartsNewWindow(List<IPeriodHolding> periodHoldings, LocalDate askedDay, int index);
  }

  static class FilterLastDayMonth implements IFilterPeriodDay {
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
