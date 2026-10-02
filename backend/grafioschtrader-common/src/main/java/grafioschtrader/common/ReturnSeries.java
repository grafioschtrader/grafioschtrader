package grafioschtrader.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

//@formatter:off
/**
 * Time-weighted return, drawdown, volatility and average capital of a series of valuations with external flows in
 * between. Shared by the period performance report and the historical replay of the algorithmic trading, which differ
 * only in when a flow is assumed to have happened within an interval.
 *
 * <p>
 * For every interval {@code (d_(i-1), d_i]} a capital base {@code B_i} and a growth factor {@code g_i} are formed:
 * </p>
 * <ul>
 * <li>{@link Convention#REPORT}: inflows count as flowed at the start of the interval, outflows at its end. So
 * {@code B_i = V_(i-1) + inflow_i} and {@code g_i = (V_i + outflow_i) / B_i}. A withdrawal of 90 % on a day with a
 * price gain of 5 % keeps the invested capital as base and yields 5 %, not 50 %. A factor below zero counts as total
 * loss (factor 0), because with margin and short positions a non-positive value is possible.</li>
 * <li>{@link Convention#REPLAY}: every flow counts as flowed at the start, {@code B_i = V_(i-1) + inflow_i - outflow_i}
 * and {@code g_i = V_i / B_i}, without flooring.</li>
 * </ul>
 * <p>
 * An interval with {@code B_i <= 0} (before the first deposit, negative value) is not usable and gets the factor 1. The
 * wealth index is held as a count of total losses plus the product of the remaining factors, so a span without a total
 * loss keeps its own return even after the value was zero once, and no {@code 0/0} ever occurs.
 * </p>
 * <p>
 * Instances are immutable; every figure is a pure function of the points given to {@link #of(List, Convention)}.
 * Figures are ratios (0.1 means 10 %); an undefined figure is {@code null}, never {@code NaN}.
 * </p>
 */
//@formatter:on
public final class ReturnSeries {

  /** Minimum calendar days from which a period return is scaled to a year. Captures every full year. */
  public static final long MIN_ANNUALIZATION_DAYS = 360;

  /** Trading days a standard deviation of daily returns is scaled by to become an annual figure. */
  public static final double TRADING_DAYS_PER_YEAR = 252.0;

  /** Tolerance with which a later value counts as having regained a previous high. */
  private static final double RECOVERY_TOLERANCE = 1e-12;

  /**
   * One valuation of the series.
   *
   * @param date    valuation date
   * @param value   total value on that date in main currency
   * @param inflow  deposits since the previous point, ignored on the first point
   * @param outflow withdrawals since the previous point as a positive amount, ignored on the first point
   * @param regular whether the interval since the previous point misses no expected trading day; ignored on the first
   *                point. Only regular intervals enter the volatility.
   */
  public record ValuationPoint(LocalDate date, double value, double inflow, double outflow, boolean regular) {
  }

  /** When the flows of an interval are assumed to have happened. */
  public enum FlowTiming {
    /** All flows at the start of the interval. */
    ALL_AT_START,
    /** Inflows at the start, outflows at the end of the interval. */
    INFLOW_START_OUTFLOW_END
  }

  /**
   * Calculation convention of a series.
   *
   * @param timing         assumed timing of the flows within an interval
   * @param floorTotalLoss whether a factor below zero is treated as total loss (factor 0)
   */
  public record Convention(FlowTiming timing, boolean floorTotalLoss) {
    /** Convention of the period performance report. */
    public static final Convention REPORT = new Convention(FlowTiming.INFLOW_START_OUTFLOW_END, true);
    /** Convention of the historical replay, which keeps its start-of-day convention. */
    public static final Convention REPLAY = new Convention(FlowTiming.ALL_AT_START, false);
  }

  /**
   * Largest decline of the wealth index from a previous high.
   *
   * @param depth        maximum decline as a ratio (at most 0), null without any usable return
   * @param current      decline of the last point from the highest point of the series, null without any usable return
   * @param peakDate     date of the high before the largest decline, null without decline
   * @param troughDate   date of the low of the largest decline, null without decline
   * @param recoveryDate first date the previous high was regained, null when it was not regained or without decline
   */
  public record Drawdown(Double depth, Double current, LocalDate peakDate, LocalDate troughDate,
      LocalDate recoveryDate) {
  }

  private final List<ValuationPoint> points;
  /** B_i, index 0 unused. */
  private final double[] capitalBase;
  /** g_i, index 0 is 1. */
  private final double[] factor;
  private final boolean[] usable;
  /** U_i: number of usable factors up to and including i. */
  private final int[] usableCount;
  /** Z_i: number of zero factors up to and including i. */
  private final int[] zeroCount;
  /** P_i: product of the non-zero factors up to and including i. */
  private final double[] product;

  private ReturnSeries(List<ValuationPoint> points, Convention convention) {
    this.points = List.copyOf(points);
    int size = this.points.size();
    capitalBase = new double[size];
    factor = new double[size];
    usable = new boolean[size];
    usableCount = new int[size];
    zeroCount = new int[size];
    product = new double[size];
    if (size > 0) {
      factor[0] = 1;
      product[0] = 1;
    }
    for (int i = 1; i < size; i++) {
      calculateFactor(i, convention);
      usableCount[i] = usableCount[i - 1] + (usable[i] ? 1 : 0);
      zeroCount[i] = zeroCount[i - 1] + (factor[i] == 0 ? 1 : 0);
      product[i] = factor[i] == 0 ? product[i - 1] : product[i - 1] * factor[i];
    }
  }

  /**
   * Calculates all factors of the series once.
   *
   * @param points     the valuations in ascending order of date
   * @param convention the flow timing and total loss convention
   * @return the series
   */
  public static ReturnSeries of(List<ValuationPoint> points, Convention convention) {
    return new ReturnSeries(points, convention);
  }

  private void calculateFactor(int i, Convention convention) {
    ValuationPoint before = points.get(i - 1);
    ValuationPoint point = points.get(i);
    double numerator;
    if (convention.timing() == FlowTiming.ALL_AT_START) {
      capitalBase[i] = before.value() + point.inflow() - point.outflow();
      numerator = point.value();
    } else {
      capitalBase[i] = before.value() + point.inflow();
      numerator = point.value() + point.outflow();
    }
    if (capitalBase[i] > 0) {
      double g = numerator / capitalBase[i];
      factor[i] = convention.floorTotalLoss() ? Math.max(0, g) : g;
      usable[i] = Double.isFinite(factor[i]);
      if (!usable[i]) {
        factor[i] = 1;
      }
    } else {
      factor[i] = 1;
    }
  }

  /** Number of valuation points. */
  public int size() {
    return points.size();
  }

  /** Date of the valuation point with the given index. */
  public LocalDate date(int index) {
    return points.get(index).date();
  }

  /** Value in main currency, including the open margin result, at the given valuation point. */
  public double value(int index) {
    return points.get(index).value();
  }

  /** Whether the interval ending at the given index is regular; the first point has no interval. */
  public boolean isRegular(int index) {
    return index > 0 && points.get(index).regular();
  }

  /**
   * Wealth index W_i: 0 after a total loss, otherwise the product of the factors.
   *
   * @param index index of the valuation point
   * @return the wealth index relative to the first point, which is 1
   */
  public double wealth(int index) {
    return zeroCount[index] > 0 ? 0 : product[index];
  }

  /**
   * Time-weighted return between two valuation points.
   *
   * @param from index of the base point
   * @param to   index of the end point
   * @return the linked return as a ratio, -1 when a total loss lies in between, null when no usable interval lies in
   *         between
   */
  public Double linked(int from, int to) {
    if (usableCount[to] - usableCount[from] == 0) {
      return null;
    }
    if (zeroCount[to] - zeroCount[from] > 0) {
      return -1.0;
    }
    return product[to] / product[from] - 1;
  }

  /** Time-weighted return of the whole series, null without a usable interval. */
  public Double totalReturn() {
    return points.isEmpty() ? null : linked(0, points.size() - 1);
  }

  /**
   * Scales a period return to a year of 365 days.
   *
   * @param periodReturn    return of the period as a ratio
   * @param calendarDays    calendar days of the period
   * @param minCalendarDays shortest period that is scaled; shorter ones give null
   * @return the annualized return, null when the return is null, the period too short or everything was lost
   */
  public static Double annualize(Double periodReturn, long calendarDays, long minCalendarDays) {
    if (periodReturn == null || calendarDays <= 0 || calendarDays < minCalendarDays || 1 + periodReturn <= 0) {
      return null;
    }
    double annualized = Math.pow(1 + periodReturn, 365.0 / calendarDays) - 1;
    return Double.isFinite(annualized) ? annualized : null;
  }

  /** Largest decline of the wealth index within the series, see {@link Drawdown}. */
  public Drawdown drawdown() {
    int last = points.size() - 1;
    if (last < 1 || usableCount[last] == 0) {
      return new Drawdown(null, null, null, null, null);
    }
    int peak = 0;
    double maxDepth = 0;
    int peakOfMax = -1;
    int trough = -1;
    double highest = wealth(0);
    for (int i = 1; i <= last; i++) {
      double w = wealth(i);
      highest = Math.max(highest, w);
      if (w > wealth(peak)) {
        peak = i;
      } else if (wealth(peak) > 0) {
        double depth = w / wealth(peak) - 1;
        if (depth < maxDepth) {
          maxDepth = depth;
          peakOfMax = peak;
          trough = i;
        }
      }
    }
    Integer recovery = trough < 0 ? null : findRecovery(trough, wealth(peakOfMax));
    return new Drawdown(maxDepth, wealth(last) / highest - 1, peakOfMax < 0 ? null : date(peakOfMax),
        trough < 0 ? null : date(trough), recovery == null ? null : date(recovery));
  }

  /**
   * Longest time the wealth index spent below a previous high, in calendar days. A phase runs from the high to the
   * first point that regains it; a phase still open at the last point runs to that point. Unlike the depth of
   * {@link #drawdown()}, this measures every decline, not only the deepest one, because a shallow decline can last much
   * longer than a deep one.
   *
   * @return the calendar days of the longest phase, 0 for a series that never falls below its high, null without any
   *         usable return
   */
  public Integer maxDrawdownDurationDays() {
    int last = points.size() - 1;
    if (last < 1 || usableCount[last] == 0) {
      return null;
    }
    double peak = wealth(0);
    LocalDate peakDate = date(0);
    boolean underwater = false;
    long longest = 0;
    for (int i = 1; i <= last; i++) {
      double w = wealth(i);
      if (w >= peak * (1 - RECOVERY_TOLERANCE)) {
        if (underwater) {
          longest = Math.max(longest, ChronoUnit.DAYS.between(peakDate, date(i)));
        }
        peak = Math.max(peak, w);
        peakDate = date(i);
        underwater = false;
      } else {
        underwater = true;
      }
    }
    if (underwater) {
      longest = Math.max(longest, ChronoUnit.DAYS.between(peakDate, date(last)));
    }
    return (int) longest;
  }

  private Integer findRecovery(int trough, double peakWealth) {
    for (int i = trough + 1; i < points.size(); i++) {
      if (wealth(i) >= peakWealth * (1 - RECOVERY_TOLERANCE)) {
        return i;
      }
    }
    return null;
  }

  /** All usable interval returns {@code g_i - 1} in chronological order. */
  public double[] usableReturns() {
    double[] returns = new double[points.isEmpty() ? 0 : usableCount[points.size() - 1]];
    int count = 0;
    for (int i = 1; i < points.size(); i++) {
      if (usable[i]) {
        returns[count++] = factor[i] - 1;
      }
    }
    return returns;
  }

  /** Number of returns of usable and regular intervals, the sample of the volatility. */
  public int volatilityObservations() {
    int count = 0;
    for (int i = 1; i < points.size(); i++) {
      if (usable[i] && points.get(i).regular()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Sample standard deviation of the returns of usable and regular intervals, scaled with the square root of 252.
   *
   * @param minObservations fewest returns required, below that the result is null
   * @return the annualized volatility as a ratio, or null
   */
  public Double annualizedVolatility(int minObservations) {
    int n = volatilityObservations();
    if (n < Math.max(2, minObservations)) {
      return null;
    }
    double mean = 0;
    for (int i = 1; i < points.size(); i++) {
      if (usable[i] && points.get(i).regular()) {
        mean += factor[i] - 1;
      }
    }
    mean /= n;
    double sumOfSquares = 0;
    for (int i = 1; i < points.size(); i++) {
      if (usable[i] && points.get(i).regular()) {
        double deviation = factor[i] - 1 - mean;
        sumOfSquares += deviation * deviation;
      }
    }
    return Math.sqrt(sumOfSquares / (n - 1)) * Math.sqrt(TRADING_DAYS_PER_YEAR);
  }

  /**
   * Capital base of every interval weighted with its calendar days.
   *
   * @return the average invested capital in main currency, null when the series spans no calendar day
   */
  public Double averageCapital() {
    long totalDays = calendarDays();
    if (totalDays <= 0) {
      return null;
    }
    double weighted = 0;
    for (int i = 1; i < points.size(); i++) {
      weighted += capitalBase[i] * ChronoUnit.DAYS.between(date(i - 1), date(i));
    }
    return weighted / totalDays;
  }

  /** Number of intervals that span at least one expected trading day without valuation. */
  public int gapIntervals() {
    int count = 0;
    for (int i = 1; i < points.size(); i++) {
      if (!points.get(i).regular()) {
        count++;
      }
    }
    return count;
  }

  /** Calendar days from the first to the last point, 0 for fewer than two points. */
  public long calendarDays() {
    return points.size() < 2 ? 0 : ChronoUnit.DAYS.between(date(0), date(points.size() - 1));
  }
}
