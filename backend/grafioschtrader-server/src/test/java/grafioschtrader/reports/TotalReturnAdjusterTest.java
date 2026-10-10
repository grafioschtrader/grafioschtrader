package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.TotalReturnFactor;
import grafioschtrader.reports.TotalReturnAdjuster.TotalReturnFactors;
import grafioschtrader.repository.DividendJpaRepository.DividendTotalReturn;
import grafioschtrader.types.SamplingPeriodType;

/**
 * Pure unit tests of the reinvestment rule of {@link TotalReturnAdjuster} and of the period sampling of
 * {@link ReportHelper}; no database is involved.
 */
class TotalReturnAdjusterTest {

  private static final double EPS = 1e-12;
  private static final LocalDate EX1 = LocalDate.of(2025, 3, 10);
  private static final LocalDate EX2 = LocalDate.of(2025, 9, 15);

  @Test
  @DisplayName("Prices before an ex-date are scaled by P_e / (P_e + D), prices on or after it stay unchanged")
  void singleDividend() {
    TotalReturnFactors factors = TotalReturnFactors.of(1, List.of(dividend(EX1, 2.0, 100.0, 98.0, 1.0)));
    assertEquals(0.98, factors.factorForDate(EX1.minusDays(1)), EPS);
    assertEquals(1.0, factors.factorForDate(EX1), EPS);
    assertEquals(1.0, factors.factorForDate(EX1.plusDays(30)), EPS);
  }

  @Test
  @DisplayName("Factors of several ex-dates multiply for the older prices")
  void cumulativeFactors() {
    TotalReturnFactors factors = TotalReturnFactors.of(1,
        List.of(dividend(EX1, 2.0, 100.0, 98.0, 1.0), dividend(EX2, 1.0, 50.0, 49.0, 1.0)));
    assertEquals(0.98 * 0.98, factors.factorForDate(EX1.minusDays(1)), EPS);
    assertEquals(0.98, factors.factorForDate(EX1), EPS);
    assertEquals(1.0, factors.factorForDate(EX2), EPS);
    List<TotalReturnFactor> steps = factors.getCumulativeFactors();
    assertEquals(2, steps.size());
    assertEquals(EX1, steps.get(0).exDate);
    assertEquals(0.98 * 0.98, steps.get(0).factor, EPS);
    assertEquals(0.98, steps.get(1).factor, EPS);
  }

  @Test
  @DisplayName("The ex-date return equals the holding return (P_e + D) / P of a total return index")
  void equalsForwardTotalReturnIndex() {
    double closeBefore = 100.0;
    double dividend = 2.0;
    for (double closeExDate : new double[] { 98.0, 99.0, 95.5 }) {
      TotalReturnFactors factors = TotalReturnFactors.of(1,
          List.of(dividend(EX1, dividend, closeBefore, closeExDate, 1.0)));
      double adjustedReturn = closeExDate * factors.factorForDate(EX1)
          / (closeBefore * factors.factorForDate(EX1.minusDays(1))) - 1;
      assertEquals((closeExDate + dividend) / closeBefore - 1, adjustedReturn, EPS);
    }
  }

  @Test
  @DisplayName("Growth between two dates covers exactly the ex-dates in (start, end]")
  void growthBetween() {
    TotalReturnFactors factors = TotalReturnFactors.of(1,
        List.of(dividend(EX1, 2.0, 100.0, 98.0, 1.0), dividend(EX2, 1.0, 50.0, 49.0, 1.0)));
    assertEquals(0.98, factors.growthBetween(EX1.minusDays(1), EX1), EPS);
    assertEquals(1.0, factors.growthBetween(EX1, EX2.minusDays(1)), EPS);
    assertEquals(0.98 * 0.98, factors.growthBetween(EX1.minusDays(1), EX2), EPS);
    assertTrue(factors.hasExDateBetween(EX1.minusDays(1), EX1));
    assertFalse(factors.hasExDateBetween(EX1, EX2.minusDays(1)));
  }

  @Test
  @DisplayName("A dividend in a foreign currency is converted with the exchange rate of the ex-date")
  void foreignCurrencyDividend() {
    TotalReturnFactors factors = TotalReturnFactors.of(1, List.of(dividend(EX1, 4.0, 100.0, 98.0, 0.5)));
    assertEquals(0.98, factors.factorForDate(EX1.minusDays(1)), EPS);
  }

  @Test
  @DisplayName("A negative dividend gives a factor above 1")
  void negativeDividend() {
    TotalReturnFactors factors = TotalReturnFactors.of(1, List.of(dividend(EX1, -1.0, 100.0, 100.0, 1.0)));
    assertEquals(100.0 / 99.0, factors.factorForDate(EX1.minusDays(1)), EPS);
  }

  @Test
  @DisplayName("Missing rate, missing closes and an implausible amount skip the dividend")
  void skippedDividends() {
    TotalReturnFactors factors = TotalReturnFactors.of(1,
        List.of(dividend(EX1, 2.0, 100.0, 98.0, null), dividend(EX2, 2.0, null, 98.0, 1.0),
            dividend(EX2.plusDays(1), 2.0, 100.0, null, 1.0), dividend(EX2.plusDays(2), 120.0, 100.0, 98.0, 1.0)));
    assertTrue(factors.isEmpty());
    assertEquals(1.0, factors.factorForDate(EX1.minusDays(1)), EPS);
  }

  @Test
  @DisplayName("Monthly sampling keeps the last common trading day of each month")
  void sampleLastDayOfMonth() {
    TreeMap<LocalDate, double[]> tree = new TreeMap<>();
    tree.put(LocalDate.of(2025, 1, 2), new double[] { 1 });
    tree.put(LocalDate.of(2025, 1, 30), new double[] { 2 });
    tree.put(LocalDate.of(2025, 1, 31), new double[] { 3 });
    tree.put(LocalDate.of(2025, 2, 3), new double[] { 4 });
    tree.put(LocalDate.of(2025, 2, 27), new double[] { 5 });
    TreeMap<LocalDate, double[]> sampled = ReportHelper.sampleLastDayOfPeriod(tree,
        SamplingPeriodType.MONTHLY_RETURNS);
    assertEquals(List.of(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 27)), List.copyOf(sampled.keySet()));
    assertEquals(1, ReportHelper.sampleLastDayOfPeriod(tree, SamplingPeriodType.ANNUAL_RETURNS).size());
    assertEquals(5, ReportHelper.sampleLastDayOfPeriod(tree, SamplingPeriodType.DAILY_RETURNS).size());
  }

  private static DividendTotalReturn dividend(LocalDate exDate, double amount, Double closeBefore, Double closeExDate,
      Double exchangeRate) {
    return new DividendTotalReturn() {
      @Override
      public Integer getIdSecuritycurrency() {
        return 1;
      }

      @Override
      public LocalDate getExDate() {
        return exDate;
      }

      @Override
      public double getAmountAdjusted() {
        return amount;
      }

      @Override
      public String getCurrency() {
        return "USD";
      }

      @Override
      public Double getCloseBefore() {
        return closeBefore;
      }

      @Override
      public Double getCloseExDate() {
        return closeExDate;
      }

      @Override
      public Double getExchangeRate() {
        return exchangeRate;
      }
    };
  }
}
