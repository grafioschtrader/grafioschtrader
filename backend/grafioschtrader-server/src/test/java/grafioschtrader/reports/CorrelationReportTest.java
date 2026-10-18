package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SamplingPeriodType;

/** Numerical and date-alignment tests of the chart response, using already adjusted daily prices. */
class CorrelationReportTest {
  private final CorrelationReport report = new CorrelationReport(null, null, null, null);

  @Test
  @DisplayName("Monthly correlations retain daily chart closes, omit warm-up and respect requested pair order")
  void monthlyCorrelationWithDailyPrices() {
    var set = correlationSet(SamplingPeriodType.MONTHLY_RETURNS);
    var closes = new TreeMap<LocalDate, double[]>();
    closes.put(LocalDate.parse("2026-01-30"), new double[] { 100, 200 });
    closes.put(LocalDate.parse("2026-02-27"), new double[] { 110, 180 });
    closes.put(LocalDate.parse("2026-03-31"), new double[] { 132, 144 });
    closes.put(LocalDate.parse("2026-04-01"), new double[] { 140, 145 });
    closes.put(LocalDate.parse("2026-04-15"), new double[] { 125, 150 });
    closes.put(LocalDate.parse("2026-04-30"), new double[] { 118.8, 158.4 });

    var result = report.calculateRollingResults(set, new Integer[][] { { 2, 1 } }, closes).getFirst();
    assertArrayEquals(new LocalDate[] { LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30") }, result.dates);
    // Returns +10%, +20%, -10% versus -10%, -20%, +10% give -1 in both two-month windows.
    assertEquals(-1, result.correlation[0], 1e-10);
    assertEquals(-1, result.correlation[1], 1e-10);
    assertArrayEquals(new LocalDate[] { LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-15"), LocalDate.parse("2026-04-30") }, result.closeDates);
    assertArrayEquals(new double[] { 144, 145, 150, 158.4 }, result.closes[0]);
    assertArrayEquals(new double[] { 132, 140, 125, 118.8 }, result.closes[1]);
    assertEquals(2, result.securitycurrencyList.getFirst().getIdSecuritycurrency());
  }

  @Test
  @DisplayName("Daily prices share correlation dates and insufficient history produces an empty response")
  void dailyAndInsufficientHistory() {
    var set = correlationSet(SamplingPeriodType.DAILY_RETURNS);
    var closes = new TreeMap<LocalDate, double[]>();
    assertTrue(report.calculateRollingResults(set, new Integer[][] { { 1, 2 } }, closes).isEmpty());
    closes.put(LocalDate.parse("2026-01-05"), new double[] { 100, 200 });
    closes.put(LocalDate.parse("2026-01-06"), new double[] { 110, 180 });
    assertTrue(report.calculateRollingResults(set, new Integer[][] { { 1, 2 } }, closes).isEmpty());
    closes.put(LocalDate.parse("2026-01-07"), new double[] { 132, 144 });
    var result = report.calculateRollingResults(set, new Integer[][] { { 1, 2 } }, closes).getFirst();
    assertArrayEquals(result.dates, result.closeDates);
    assertArrayEquals(new double[] { 132 }, result.closes[0]);
    assertEquals(-1, result.correlation[0], 1e-10);
  }

  @Test
  @DisplayName("With a date from, the line starts on the first common trading day from that date")
  void dateFromStartsTheLineOnTheFirstTradingDay() {
    // Saturday: the first common trading day on or after it is Monday 2026-01-12.
    var set = correlationSet(SamplingPeriodType.DAILY_RETURNS, LocalDate.parse("2026-01-10"));
    var closes = new TreeMap<LocalDate, double[]>();
    closes.put(LocalDate.parse("2026-01-05"), new double[] { 50, 60 });
    closes.put(LocalDate.parse("2026-01-06"), new double[] { 70, 40 });
    // Warm-up of the two-return window: closes of Thursday and Friday.
    closes.put(LocalDate.parse("2026-01-08"), new double[] { 100, 200 });
    closes.put(LocalDate.parse("2026-01-09"), new double[] { 110, 180 });
    closes.put(LocalDate.parse("2026-01-12"), new double[] { 132, 144 });
    closes.put(LocalDate.parse("2026-01-13"), new double[] { 118.8, 158.4 });

    var result = report.calculateRollingResults(set, new Integer[][] { { 1, 2 } }, closes).getFirst();
    assertArrayEquals(new LocalDate[] { LocalDate.parse("2026-01-12"), LocalDate.parse("2026-01-13") }, result.dates);
    // Only +10%, +20%, -10% versus -10%, -20%, +10% enter the windows: the earlier closes would break the -1.
    assertEquals(-1, result.correlation[0], 1e-10);
    assertEquals(-1, result.correlation[1], 1e-10);
    assertArrayEquals(result.dates, result.closeDates);
    assertArrayEquals(new double[] { 132, 118.8 }, result.closes[0]);
  }

  @Test
  @DisplayName("Too little history before the date from keeps every close; no close from that date gives nothing")
  void dateFromWithShortOrMissingHistory() {
    var closes = new TreeMap<LocalDate, double[]>();
    closes.put(LocalDate.parse("2026-01-08"), new double[] { 100, 200 });
    closes.put(LocalDate.parse("2026-01-09"), new double[] { 110, 180 });
    closes.put(LocalDate.parse("2026-01-12"), new double[] { 132, 144 });
    var shortHistory = correlationSet(SamplingPeriodType.DAILY_RETURNS, LocalDate.parse("2026-01-09"));
    var result = report.calculateRollingResults(shortHistory, new Integer[][] { { 1, 2 } }, closes).getFirst();
    assertArrayEquals(new LocalDate[] { LocalDate.parse("2026-01-12") }, result.dates);

    var afterAllCloses = correlationSet(SamplingPeriodType.DAILY_RETURNS, LocalDate.parse("2026-01-13"));
    assertTrue(report.calculateRollingResults(afterAllCloses, new Integer[][] { { 1, 2 } }, closes).isEmpty());
  }

  private CorrelationSet correlationSet(SamplingPeriodType period) {
    return correlationSet(period, null);
  }

  private CorrelationSet correlationSet(SamplingPeriodType period, LocalDate dateFrom) {
    var set = new CorrelationSet(1, "Test", 1, dateFrom, null, period.getValue(), (byte) 2, false);
    var first = new Security();
    first.setIdSecuritycurrency(1);
    first.setName("A");
    var second = new Security();
    second.setIdSecuritycurrency(2);
    second.setName("B");
    // Mutable, because the getter sorts the list by name in place.
    set.setSecuritycurrencyList(new ArrayList<>(List.of(first, second)));
    return set;
  }
}
