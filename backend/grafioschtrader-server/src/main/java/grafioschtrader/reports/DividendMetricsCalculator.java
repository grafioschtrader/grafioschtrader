package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.reportviews.securitydividends.SecurityDividendChart.YearValue;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.DateValue;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.SplitMark;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.YearAmount;

/**
 * Computes the dividend key figures of one security from its dividends, its price and its closes. It holds no state
 * and touches no repository, so the arithmetic can be tested with hand-made input.
 *
 * <p>
 * The definitions follow the dividend functions of the watchlist indicator columns ({@code IndicatorFunctionSet}), so
 * the box of the dividend feed and an indicator column show the same number: the trailing window covers the ex-dates
 * of the last 365 days, annual sums use calendar years, and the growth rates end with the last completed year.
 * </p>
 */
public final class DividendMetricsCalculator {

  /** Days before today that still belong to the trailing window, together with today 365 days. */
  static final int TTM_DAYS_BACK = 364;

  private DividendMetricsCalculator() {
  }

  /**
   * One dividend converted into the currency of the security.
   *
   * @param exDate the ex-date
   * @param amount the split-adjusted amount in the currency of the security, null when no exchange rate exists
   */
  public record DividendPoint(LocalDate exDate, Double amount) {
  }

  /**
   * One close of the security.
   *
   * @param date  the trading day
   * @param close the split-adjusted close
   */
  public record ClosePoint(LocalDate date, double close) {
  }

  /**
   * Computes all key figures.
   *
   * @param currency        the currency of the security
   * @param dividends       the dividends ordered by ex-date ascending, possibly including ex-dates after today
   * @param price           the last price of the security, null when unknown
   * @param paymentsPerYear the regular number of payments per year of the security, null when it has none
   * @param closes          the closes ordered by date ascending, used for the yield history
   * @param eps             the trailing earnings per share in the currency of the security, null when unknown
   * @param splits          the splits of the security
   * @param today           the day of the request
   * @return the key figures; all figures null and empty lists when no dividend lies on or before today
   */
  public static SecurityDividendMetrics calculate(String currency, List<DividendPoint> dividends, Double price,
      Integer paymentsPerYear, List<ClosePoint> closes, Double eps, List<SplitMark> splits, LocalDate today) {
    List<DividendPoint> past = dividends.stream().filter(d -> !d.exDate().isAfter(today)).toList();
    if (past.isEmpty()) {
      return SecurityDividendMetrics.empty(currency, price);
    }
    DividendPoint last = past.getLast();
    DividendPoint next = dividends.stream().filter(d -> d.exDate().isAfter(today)).findFirst().orElse(null);
    Map<Integer, Double> years = yearSums(past, today);
    int lastYear = today.getYear() - 1;

    Double ttm = sum(past, today.minusDays(TTM_DAYS_BACK), today);
    Double yieldTTM = ratioPercent(ttm, price);
    long ttmCount = past.stream().filter(d -> !d.exDate().isBefore(today.minusDays(TTM_DAYS_BACK))).count();
    Integer payments = paymentsPerYear != null ? paymentsPerYear : ttmCount > 0 ? (int) ttmCount : null;
    Double forward = last.amount() == null || payments == null ? null
        : ratioPercent(last.amount() * payments, price);
    Double cagr5 = cagr(years, lastYear, 5);
    Integer cutYear = lastCut(years, lastYear);

    return new SecurityDividendMetrics(currency, past.size(), past.getFirst().exDate(), last.exDate(), last.amount(),
        next == null ? null : next.exDate(), next == null ? null : next.amount(), price, ttm, yieldTTM, payments,
        forward, cagr(years, lastYear, 1), cagr(years, lastYear, 3), cagr5, cagr(years, lastYear, 10),
        yearsWithoutCut(years, lastYear), cutYear,
        cutYear == null ? null : (years.get(cutYear) / years.get(cutYear - 1) - 1) * 100,
        yieldTTM == null || cagr5 == null ? null : yieldTTM + cagr5,
        eps == null || eps <= 0 ? null : ratioPercent(ttm, eps), yearAmounts(years, today),
        yieldHistory(past, closes, today), splits);
  }

  /**
   * Sums the dividends per calendar year, from the year of the first dividend to the current year. A year without any
   * dividend gets zero, a year with an unconverted dividend null.
   */
  static Map<Integer, Double> yearSums(List<DividendPoint> past, LocalDate today) {
    Map<Integer, Double> years = new TreeMap<>();
    for (int y = past.getFirst().exDate().getYear(); y <= today.getYear(); y++) {
      years.put(y, 0.0);
    }
    for (DividendPoint d : past) {
      int y = d.exDate().getYear();
      Double current = years.get(y);
      years.put(y, current == null || d.amount() == null ? null : current + d.amount());
    }
    return years;
  }

  /** Sum of the dividends with an ex-date in the inclusive range, null when one of them has no amount. */
  private static Double sum(List<DividendPoint> past, LocalDate from, LocalDate to) {
    double sum = 0;
    for (DividendPoint d : past) {
      if (!d.exDate().isBefore(from) && !d.exDate().isAfter(to)) {
        if (d.amount() == null) {
          return null;
        }
        sum += d.amount();
      }
    }
    return sum;
  }

  private static Double ratioPercent(Double numerator, Double denominator) {
    return numerator == null || denominator == null || denominator <= 0 ? null : numerator / denominator * 100;
  }

  /**
   * Compound annual growth from {@code lastYear - n} to {@code lastYear}. Null when the start year lies before the
   * first dividend or one of the two sums is missing or not positive.
   */
  static Double cagr(Map<Integer, Double> years, int lastYear, int n) {
    Double end = years.get(lastYear), start = years.get(lastYear - n);
    if (end == null || start == null || end <= 0 || start <= 0) {
      return null;
    }
    return (Math.pow(end / start, 1.0 / n) - 1) * 100;
  }

  /**
   * Counts the completed years back from {@code lastYear} whose sum is positive and not lower than the year before. A
   * year before the first dividend counts as zero, as in {@code DIVYEARS}. Null when the last completed year precedes
   * the first dividend, or when a year without exchange rate is reached before the counting ends.
   */
  static Integer yearsWithoutCut(Map<Integer, Double> years, int lastYear) {
    if (!years.containsKey(lastYear)) {
      return null;
    }
    int count = 0;
    for (int y = lastYear; years.containsKey(y); y--) {
      Double amount = years.get(y), previous = years.getOrDefault(y - 1, 0.0);
      if (amount == null || previous == null) {
        return null;
      }
      if (amount <= 0 || amount < previous) {
        break;
      }
      count++;
    }
    return count;
  }

  /** The newest completed year with a lower sum than its predecessor, or null when there is none. */
  static Integer lastCut(Map<Integer, Double> years, int lastYear) {
    for (int y = lastYear; years.containsKey(y - 1); y--) {
      Double amount = years.get(y), previous = years.get(y - 1);
      if (amount != null && previous != null && previous > 0 && amount < previous) {
        return y;
      }
    }
    return null;
  }

  private static List<YearAmount> yearAmounts(Map<Integer, Double> years, LocalDate today) {
    List<YearAmount> result = new ArrayList<>();
    years.forEach((year, amount) -> {
      Double previous = years.get(year - 1);
      boolean partial = year == today.getYear();
      boolean cut = !partial && amount != null && previous != null && amount < previous;
      result.add(new YearAmount(year, amount, partial, cut));
    });
    return result;
  }

  /**
   * Trailing yield at the end of every month, from the month in which the first dividend is one year old up to the
   * current month. The current month uses today. A month without close up to its end, or with an unconverted dividend
   * in its window, is left out.
   */
  static List<DateValue> yieldHistory(List<DividendPoint> past, List<ClosePoint> closes, LocalDate today) {
    List<DateValue> result = new ArrayList<>();
    if (closes.isEmpty()) {
      return result;
    }
    YearMonth month = YearMonth.from(past.getFirst().exDate().plusDays(TTM_DAYS_BACK));
    int closeIndex = -1;
    for (; !month.isAfter(YearMonth.from(today)); month = month.plusMonths(1)) {
      LocalDate end = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
      while (closeIndex + 1 < closes.size() && !closes.get(closeIndex + 1).date().isAfter(end)) {
        closeIndex++;
      }
      if (closeIndex < 0) {
        continue;
      }
      Double yield = ratioPercent(sum(past, end.minusDays(TTM_DAYS_BACK), end), closes.get(closeIndex).close());
      if (yield != null) {
        result.add(new DateValue(end, yield));
      }
    }
    return result;
  }

  /**
   * Base year of the dividend index of several securities: the first completed year in which every security with
   * dividends paid for a whole year. The year of the first dividend may be incomplete, so the base is the year after
   * it, taken as the latest over all securities. Securities without dividends do not count.
   *
   * @param perSecurity       the annual dividends of each security, ascending by year
   * @param lastCompletedYear the year before the current one
   * @return the base year, or null when it would lie after the last completed year or no security has dividends
   */
  public static Integer indexBaseYear(List<List<YearAmount>> perSecurity, int lastCompletedYear) {
    Integer base = null;
    for (List<YearAmount> years : perSecurity) {
      if (!years.isEmpty()) {
        int firstWholeYear = years.getFirst().year() + 1;
        base = base == null ? firstWholeYear : Math.max(base, firstWholeYear);
      }
    }
    return base == null || base > lastCompletedYear ? null : base;
  }

  /**
   * Annual dividends of the completed years from the base year on, as a percentage of the base year. Being a ratio of
   * amounts in the same currency, the index needs no conversion between the compared securities.
   *
   * @param years             the annual dividends of one security
   * @param baseYear          the common base year, may be null
   * @param lastCompletedYear the year before the current one
   * @return the index; empty without base year or when the base year sum is missing or not positive. A year without
   *         exchange rate is left out.
   */
  public static List<YearValue> index(List<YearAmount> years, Integer baseYear, int lastCompletedYear) {
    if (baseYear == null) {
      return List.of();
    }
    Double base = years.stream().filter(y -> y.year() == baseYear).map(YearAmount::amount).findFirst().orElse(null);
    if (base == null || base <= 0) {
      return List.of();
    }
    return years.stream()
        .filter(y -> y.year() >= baseYear && y.year() <= lastCompletedYear && y.amount() != null)
        .map(y -> new YearValue(y.year(), y.amount() / base * 100)).toList();
  }
}
