package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.reports.DividendMetricsCalculator.ClosePoint;
import grafioschtrader.reports.DividendMetricsCalculator.DividendPoint;
import grafioschtrader.reportviews.securitydividends.SecurityDividendChart.YearValue;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.YearAmount;

/**
 * Checks the dividend key figures against values computed by hand for a quarterly payer with one cut, an announced
 * future dividend and a year without exchange rate.
 */
class DividendMetricsCalculatorTest {

  private static final double EPS = 1e-9;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

  /**
   * Quarterly dividends on the 15th of March, June, September and December. Annual sums: 2019 2.00, 2020 2.00, 2021
   * 1.60 (cut of 20 %), 2022 1.80, 2023 2.00, 2024 2.20, 2025 2.40; 2026 pays 0.65 three times so far and has announced
   * the December dividend.
   */
  private static List<DividendPoint> quarterlyPayer(Double amount2024) {
    Double[] perYear = { 0.50, 0.50, 0.40, 0.45, 0.50, 0.55, 0.60 };
    List<DividendPoint> dividends = new ArrayList<>();
    for (int i = 0; i < perYear.length; i++) {
      int year = 2019 + i;
      for (int month : new int[] { 3, 6, 9, 12 }) {
        dividends.add(new DividendPoint(LocalDate.of(year, month, 15), year == 2024 ? amount2024 : perYear[i]));
      }
    }
    for (int month : new int[] { 3, 6, 9, 12 }) {
      dividends.add(new DividendPoint(LocalDate.of(2026, month, 15), 0.65));
    }
    return dividends;
  }

  @Test
  @DisplayName("Trailing, forward, growth, cut and payout figures of a quarterly payer")
  void quarterlyPayerFigures() {
    SecurityDividendMetrics m = DividendMetricsCalculator.calculate("CHF", quarterlyPayer(0.55), 50.0, null,
        List.of(new ClosePoint(LocalDate.of(2026, 11, 18), 50.0)), 5.10, List.of(), TODAY);

    assertEquals(31, m.dividendCount());
    assertEquals(LocalDate.of(2026, 9, 15), m.lastExDate());
    assertEquals(LocalDate.of(2026, 12, 15), m.nextExDate());
    // Window 2025-11-20 .. 2026-11-19: 0.60 (Dec 2025) + 3 * 0.65
    assertEquals(2.55, m.dividendTTM(), EPS);
    assertEquals(5.1, m.yieldTTM(), EPS);
    assertEquals(4, m.paymentsPerYear());
    assertEquals(0.65 * 4 / 50 * 100, m.forwardYield(), EPS);
    assertEquals((2.40 / 2.20 - 1) * 100, m.cagr1(), EPS);
    assertEquals((Math.pow(2.40 / 1.80, 1.0 / 3) - 1) * 100, m.cagr3(), EPS);
    assertEquals((Math.pow(2.40 / 2.00, 1.0 / 5) - 1) * 100, m.cagr5(), EPS);
    assertNull(m.cagr10(), "2015 lies before the first dividend");
    assertEquals(4, m.yearsWithoutCut(), "2022 to 2025 rose, 2021 was cut");
    assertEquals(2021, m.lastCutYear());
    assertEquals(-20.0, m.lastCutPercent(), EPS);
    assertEquals(m.yieldTTM() + m.cagr5(), m.chowder(), EPS);
    assertEquals(50.0, m.payoutRatio(), EPS);
    assertEquals(8, m.years().size());
    assertEquals(true, m.years().getLast().partial());
    assertEquals(true, m.years().get(2).cut());
    assertEquals(TODAY, m.yieldHistory().getLast().date());
    assertEquals(5.1, m.yieldHistory().getLast().value(), EPS);
  }

  @Test
  @DisplayName("The index starts in the first whole year common to all securities")
  void indexOfTwoSecurities() {
    // Security A pays since 2019 (2019 may be incomplete), security B since 2021: the base year is 2022.
    List<YearAmount> a = List.of(new YearAmount(2019, 1.0, false, false), new YearAmount(2020, 2.0, false, false),
        new YearAmount(2021, 2.0, false, false), new YearAmount(2022, 2.5, false, false),
        new YearAmount(2023, 3.0, false, false), new YearAmount(2024, 3.2, true, false));
    List<YearAmount> b = List.of(new YearAmount(2021, 0.3, false, false), new YearAmount(2022, 0.8, false, false),
        new YearAmount(2023, 0.4, false, true), new YearAmount(2024, 0.9, true, false));

    Integer baseYear = DividendMetricsCalculator.indexBaseYear(List.of(a, b, List.of()), 2023);
    assertEquals(2022, baseYear);
    List<YearValue> indexA = DividendMetricsCalculator.index(a, baseYear, 2023);
    assertEquals(List.of(new YearValue(2022, 100.0), new YearValue(2023, 120.0)), indexA,
        "the incomplete current year 2024 is left out");
    assertEquals(50.0, DividendMetricsCalculator.index(b, baseYear, 2023).getLast().value(), EPS);
    assertNull(DividendMetricsCalculator.indexBaseYear(List.of(a, b), 2021), "2022 is not completed in 2022");
  }

  @Test
  @DisplayName("A year without exchange rate blocks the figures that need it")
  void yearWithoutExchangeRate() {
    SecurityDividendMetrics m = DividendMetricsCalculator.calculate("CHF", quarterlyPayer(null), 50.0, 4, List.of(),
        null, List.of(), TODAY);

    assertNull(m.cagr1());
    assertNull(m.yearsWithoutCut(), "whether 2025 was a cut cannot be decided without 2024");
    assertEquals(5.1, m.yieldTTM(), EPS, "the trailing window does not touch 2024");
    assertNull(m.payoutRatio());
  }
}
