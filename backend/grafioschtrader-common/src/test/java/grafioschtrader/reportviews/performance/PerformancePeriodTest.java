package grafioschtrader.reportviews.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import grafioschtrader.common.DataBusinessHelper;

/**
 * Builds the period report from hand-made daily holdings and checks the time-weighted returns of steps, windows and the
 * whole period, the completeness of steps, the data basis counters and that the amounts of the period table include the
 * open margin result.
 */
class PerformancePeriodTest {

  /**
   * Daily holding as the report query delivers it; {@code externalCashTransferMC} is the cumulative level up to the
   * day.
   */
  record Holding(LocalDate date, double cash, double securities, double margin, double externalCashTransfer)
      implements IPeriodHolding {
    @Override
    public LocalDate getDate() {
      return date;
    }

    @Override
    public double getDividendRealMC() {
      return 0;
    }

    @Override
    public double getFeeRealMC() {
      return 0;
    }

    @Override
    public double getInterestCashaccountRealMC() {
      return 0;
    }

    @Override
    public double getAccumulateReduceMC() {
      return 0;
    }

    @Override
    public double getCashBalanceMC() {
      return cash;
    }

    @Override
    public double getExternalCashTransferMC() {
      return externalCashTransfer;
    }

    @Override
    public double getSecuritiesMC() {
      return securities;
    }

    @Override
    public double getMarginCloseGainMC() {
      return margin;
    }

    @Override
    public double getSecurityRiskMC() {
      return 0;
    }

    @Override
    public double getGainMC() {
      return cash + securities - externalCashTransfer;
    }
  }

  record Flow(LocalDate date, double amount) implements IDailyExternalFlow {
    @Override
    public LocalDate getFlowDate() {
      return date;
    }

    @Override
    public double getFlowMC() {
      return amount;
    }
  }

  private static PerformancePeriod report(WeekYear split, List<IPeriodHolding> holdings, List<IDailyExternalFlow> flows,
      Set<LocalDate> holidays, Set<LocalDate> missingQuoteDays) {
    PeriodHoldingAndDiff first = new PeriodHoldingAndDiff();
    PeriodHoldingAndDiff last = new PeriodHoldingAndDiff();
    BeanUtils.copyProperties(holdings.getFirst(), first);
    BeanUtils.copyProperties(holdings.getLast(), last);
    FirstAndMissingTradingDays fmtd = new FirstAndMissingTradingDays(first.getDate(), first.getDate(), null, null,
        last.getDate(), last.getDate(), null, holidays, missingQuoteDays);
    PerformancePeriod performancePeriod = new PerformancePeriod(split, first, last, new PeriodHoldingAndDiff());
    performancePeriod.createPeriodWindows(fmtd, holdings, flows, 0);
    return performancePeriod;
  }

  private static List<PeriodStep> steps(PeriodWindow periodWindow) {
    return periodWindow.periodStepList.stream().filter(PeriodStep.class::isInstance).map(PeriodStep.class::cast)
        .toList();
  }

  private static double pct(double ratio) {
    return DataBusinessHelper.roundPercentage(ratio * 100);
  }

  private static List<LocalDate> weekdays(LocalDate from, LocalDate to, Set<LocalDate> skip) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !skip.contains(d)) {
        days.add(d);
      }
    }
    return days;
  }

  @Test
  @DisplayName("Weekly split over two weeks: step returns are daily, windows chain their days, the period its windows")
  void weeklyChaining() {
    double[] factors = { 1.01, 0.99, 1.02, 1.0, 0.98, 1.03, 1.01, 0.97, 1.02, 1.0 };
    List<LocalDate> dates = weekdays(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 14), Set.of());
    List<IPeriodHolding> holdings = new ArrayList<>();
    double value = 1000;
    holdings.add(new Holding(dates.getFirst(), 0, value, 0, 1000));
    for (int i = 0; i < factors.length; i++) {
      value *= factors[i];
      holdings.add(new Holding(dates.get(i + 1), 0, value, 0, 1000));
    }
    PerformancePeriod pp = report(WeekYear.WM_WEEK, holdings, List.of(), Set.of(), Set.of());

    List<PeriodWindow> windows = pp.getPeriodWindows();
    assertEquals(3, windows.size());
    assertNull(windows.get(0).twrPercent, "the week of the base holds no step");
    List<PeriodStep> week1 = steps(windows.get(1));
    for (int i = 0; i < 5; i++) {
      assertEquals(pct(factors[i] - 1), week1.get(i).twrPercent);
      assertTrue(week1.get(i).complete);
    }
    assertEquals(pct(1.01 * 0.99 * 1.02 * 1.0 * 0.98 - 1), windows.get(1).twrPercent);
    assertEquals(pct(1.03 * 1.01 * 0.97 * 1.02 * 1.0 - 1), windows.get(2).twrPercent);
    assertEquals(pct(value / 1000 - 1), pp.getMetrics().twrPercent());
    assertEquals(10, pp.getMetrics().valuedSessions());
    assertEquals(10, pp.getMetrics().expectedSessions());
    assertEquals(0, pp.getMetrics().gapIntervals());
    assertEquals(pct(0.03), pp.getMetrics().bestStepPercent());
    assertEquals(pct(-0.03), pp.getMetrics().worstStepPercent());
  }

  @Test
  @DisplayName("Yearly split: a deposit in mid-February is chained daily, not taken as gain over the month start value")
  void yearlyMonthStepChainsDays() {
    LocalDate deposit = LocalDate.of(2025, 2, 14);
    List<LocalDate> dates = weekdays(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 3, 31), Set.of());
    List<IPeriodHolding> holdings = new ArrayList<>();
    double value = 1000;
    double external = 1000;
    double februaryProduct = 1;
    double januaryEnd = 0;
    holdings.add(new Holding(dates.getFirst(), 0, value, 0, external));
    for (int i = 1; i < dates.size(); i++) {
      LocalDate date = dates.get(i);
      double inflow = date.equals(deposit) ? 1000 : 0;
      double factor = 1 + ((i % 3) - 1) * 0.004;
      external += inflow;
      value = (value + inflow) * factor;
      holdings.add(new Holding(date, 0, value, 0, external));
      if (date.getMonthValue() == 2) {
        februaryProduct *= factor;
      } else if (date.equals(LocalDate.of(2025, 1, 31))) {
        januaryEnd = value;
      }
    }
    PerformancePeriod pp = report(WeekYear.WM_YEAR, holdings, List.of(new Flow(deposit, 1000)), Set.of(), Set.of());

    PeriodStep february = steps(pp.getPeriodWindows().getLast()).stream().filter(s -> s.lastDate.getMonthValue() == 2)
        .findFirst().orElseThrow();
    assertEquals(LocalDate.of(2025, 1, 31), february.baseDate);
    assertTrue(february.complete);
    assertEquals(pct(februaryProduct - 1), february.twrPercent);
    double februaryEnd = ((Holding) holdings.stream().filter(h -> h.getDate().equals(LocalDate.of(2025, 2, 28)))
        .findFirst().orElseThrow()).securities();
    assertNotEquals(pct((februaryEnd - januaryEnd - 1000) / januaryEnd), february.twrPercent);
  }

  @Test
  @DisplayName("Missing valuation on 31 December with a deposit on that day: the January step is incomplete")
  void missingYearEndValuation() {
    LocalDate dec31 = LocalDate.of(2024, 12, 31);
    LocalDate jan1 = LocalDate.of(2025, 1, 1);
    List<LocalDate> dates = weekdays(LocalDate.of(2024, 11, 29), LocalDate.of(2025, 2, 28), Set.of(dec31, jan1));
    List<IPeriodHolding> holdings = new ArrayList<>();
    double value = 1000;
    double external = 1000;
    for (int i = 0; i < dates.size(); i++) {
      LocalDate date = dates.get(i);
      if (date.equals(LocalDate.of(2025, 1, 2))) {
        external += 500;
        value += 500;
      }
      value *= i == 0 ? 1 : 1 + ((i % 4) - 1.5) * 0.002;
      holdings.add(new Holding(date, 0, value, 0, external));
    }
    PerformancePeriod pp = report(WeekYear.WM_YEAR, holdings, List.of(new Flow(dec31, 500)), Set.of(jan1),
        Set.of(dec31));

    List<PeriodStep> all = pp.getPeriodWindows().stream().flatMap(w -> steps(w).stream()).toList();
    PeriodStep december = all.stream().filter(s -> s.lastDate.getMonthValue() == 12).findFirst().orElseThrow();
    PeriodStep january = all.stream().filter(s -> s.lastDate.getMonthValue() == 1).findFirst().orElseThrow();
    PeriodStep february = all.stream().filter(s -> s.lastDate.getMonthValue() == 2).findFirst().orElseThrow();
    assertFalse(december.complete);
    assertEquals(LocalDate.of(2024, 12, 30), january.baseDate);
    assertFalse(january.complete);
    assertTrue(february.complete);
    assertEquals(february.lastDate, pp.getMetrics().bestStepDate());
    assertEquals(february.lastDate, pp.getMetrics().worstStepDate());
  }

  @Test
  @DisplayName("A day with a missing price: the following step spans both days and counts as a gap")
  void missingQuoteDay() {
    LocalDate missing = LocalDate.of(2025, 3, 12);
    List<LocalDate> dates = weekdays(LocalDate.of(2025, 3, 7), LocalDate.of(2025, 3, 14), Set.of(missing));
    List<IPeriodHolding> holdings = new ArrayList<>();
    double value = 1000;
    for (LocalDate date : dates) {
      holdings.add(new Holding(date, 0, value, 0, 1000));
      value *= 1.01;
    }
    PerformancePeriod pp = report(WeekYear.WM_WEEK, holdings, List.of(), Set.of(), Set.of(missing));

    PeriodStep thursday = steps(pp.getPeriodWindows().getLast()).stream()
        .filter(s -> s.lastDate.equals(LocalDate.of(2025, 3, 13))).findFirst().orElseThrow();
    assertEquals(LocalDate.of(2025, 3, 11), thursday.baseDate);
    assertFalse(thursday.complete);
    PerformancePeriodMetrics metrics = pp.getMetrics();
    assertEquals(1, metrics.gapIntervals());
    assertEquals(4, metrics.valuedSessions());
    assertEquals(metrics.valuedSessions() + 1, metrics.expectedSessions());
  }

  @Test
  @DisplayName("Cash only with deposits and withdrawals in main currency: time-weighted return 0 %")
  void pureCash() {
    List<IPeriodHolding> holdings = List.of(new Holding(LocalDate.of(2025, 3, 3), 1000, 0, 0, 1000),
        new Holding(LocalDate.of(2025, 3, 4), 1500, 0, 0, 1500),
        new Holding(LocalDate.of(2025, 3, 5), 1200, 0, 0, 1200),
        new Holding(LocalDate.of(2025, 3, 6), 1200, 0, 0, 1200));
    List<IDailyExternalFlow> flows = List.of(new Flow(LocalDate.of(2025, 3, 4), 500),
        new Flow(LocalDate.of(2025, 3, 5), -300));
    PerformancePeriod pp = report(WeekYear.WM_WEEK, holdings, flows, Set.of(), Set.of());
    assertEquals(0.0, pp.getMetrics().twrPercent());
    assertEquals(0.0, pp.getMetrics().maxDrawdownPercent());
  }

  @Test
  @DisplayName("Open margin position: window gain and column total include the open margin result")
  void openMarginResult() {
    List<LocalDate> dates = weekdays(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 14), Set.of());
    List<IPeriodHolding> holdings = new ArrayList<>();
    for (int i = 0; i < dates.size(); i++) {
      holdings.add(new Holding(dates.get(i), 1000, 0, i * 10.0, 1000));
    }
    PerformancePeriod pp = report(WeekYear.WM_WEEK, holdings, List.of(), Set.of(), Set.of());

    List<PeriodWindow> windows = pp.getPeriodWindows();
    assertEquals(50.0, windows.get(1).gainPeriodMC, 1e-9, "window formed against the window before it");
    assertEquals(50.0, windows.get(2).gainPeriodMC, 1e-9, "last window summed from its steps");
    for (double columnTotal : pp.getSumPeriodColSteps()) {
      assertEquals(20.0, columnTotal, 1e-9);
    }
    double rowSum = steps(windows.get(2)).stream().mapToDouble(PeriodStep::getTotalGainMC).sum();
    assertEquals(windows.get(2).gainPeriodMC, rowSum, 1e-9);
  }
}
