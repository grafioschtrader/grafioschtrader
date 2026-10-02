package grafioschtrader.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.types.MoneyWeightedReturnStatus;

//@formatter:off
/**
 * Money-weighted return of a period, that is the internal rate of return of the investor's own money on the booked
 * dates of the flows.
 *
 * <p>
 * From the investor's point of view the opening value is paid at {@code t = 0}, every deposit is paid and every
 * withdrawal received on its day, and the closing value is received at {@code t = T}. The rate {@code s} is the
 * logarithmic return over the whole period and solves
 * </p>
 * <pre>
 * f(s) = Σ CF_j × exp(−s × t_j / T) = 0
 * </pre>
 * <p>
 * Scaling the time to {@code t / T} keeps every exponent within [−20, 20]. Instead of a Newton iteration, which may run
 * to an arbitrary one of several roots, the function is sampled on a grid and only a single sign change is refined by
 * bisection. Several or no sign changes are reported by their {@link MoneyWeightedReturnStatus} instead of a number.
 * Two roots closer than the grid step of 0.01 in {@code s} are not detected.
 * </p>
 */
//@formatter:on
public final class MoneyWeightedReturn {

  private static final double GRID_START = -20;
  private static final double GRID_STEP = 0.01;
  private static final int GRID_STEPS = 4000;
  private static final int MAX_BISECTION_STEPS = 200;
  private static final double BISECTION_WIDTH = 1e-12;
  /** Merged flows below this absolute amount are treated as zero; the input amounts carry two decimals. */
  private static final double ZERO_FLOW = 1e-9;

  /**
   * An external flow of one calendar day.
   *
   * @param date   booking date
   * @param amount amount from the portfolio's point of view: a deposit is positive, a withdrawal negative
   */
  public record DatedFlow(LocalDate date, double amount) {
  }

  /**
   * Result of the search.
   *
   * @param status          which case applied
   * @param periodLogReturn logarithmic return over the whole period, set only with {@code MWR_CALCULATED}
   */
  public record Result(MoneyWeightedReturnStatus status, Double periodLogReturn) {
  }

  private MoneyWeightedReturn() {
  }

  /**
   * Searches the internal rate of return of a period.
   *
   * @param start      first date of the period, the opening value is valued at its end
   * @param startValue value on the start date
   * @param end        last date of the period
   * @param endValue   value on the end date
   * @param flows      external flows; only those dated after start and not after end are taken
   * @return the status and, when calculated, the logarithmic return of the period
   */
  public static Result solve(LocalDate start, double startValue, LocalDate end, double endValue,
      List<DatedFlow> flows) {
    long totalDays = ChronoUnit.DAYS.between(start, end);
    if (totalDays <= 0) {
      return new Result(MoneyWeightedReturnStatus.MWR_INSUFFICIENT_DATA, null);
    }
    double[][] cashFlows = investorCashFlows(start, startValue, end, endValue, flows, totalDays);
    boolean anyNegative = false;
    boolean anyPositive = false;
    for (double[] cf : cashFlows) {
      anyNegative |= cf[1] < 0;
      anyPositive |= cf[1] > 0;
    }
    if (cashFlows.length < 2 || !anyNegative) {
      return new Result(MoneyWeightedReturnStatus.MWR_INSUFFICIENT_DATA, null);
    }
    if (!anyPositive) {
      return new Result(MoneyWeightedReturnStatus.MWR_TOTAL_LOSS, null);
    }
    return searchRoot(cashFlows);
  }

  /**
   * Investor cash flows with equal times merged and zeros removed.
   *
   * @return pairs of time as a fraction of the period and amount, ordered by time
   */
  private static double[][] investorCashFlows(LocalDate start, double startValue, LocalDate end, double endValue,
      List<DatedFlow> flows, long totalDays) {
    Map<Long, Double> byDay = new TreeMap<>();
    byDay.merge(0L, -startValue, Double::sum);
    for (DatedFlow flow : flows) {
      if (flow.date().isAfter(start) && !flow.date().isAfter(end)) {
        byDay.merge(ChronoUnit.DAYS.between(start, flow.date()), -flow.amount(), Double::sum);
      }
    }
    byDay.merge(totalDays, endValue, Double::sum);
    List<double[]> result = new ArrayList<>();
    byDay.forEach((day, amount) -> {
      if (Math.abs(amount) >= ZERO_FLOW) {
        result.add(new double[] { (double) day / totalDays, amount });
      }
    });
    return result.toArray(double[][]::new);
  }

  private static double presentValue(double[][] cashFlows, double s) {
    double sum = 0;
    for (double[] cf : cashFlows) {
      sum += cf[1] * Math.exp(-s * cf[0]);
    }
    return sum;
  }

  /**
   * Samples {@code f} on the grid and counts its roots. A sign change counts as one root, an exact zero between two
   * values of equal sign (a touch) or at an end of the grid counts as a root of its own.
   */
  private static Result searchRoot(double[][] cashFlows) {
    int roots = 0;
    double lastNonZeroS = Double.NaN;
    double lastNonZeroValue = 0;
    boolean zeroSinceLastNonZero = false;
    double zeroS = Double.NaN;
    double[] bracket = null;
    for (int k = 0; k <= GRID_STEPS; k++) {
      double s = GRID_START + GRID_STEP * k;
      double value = presentValue(cashFlows, s);
      if (value == 0) {
        zeroSinceLastNonZero = true;
        zeroS = s;
        continue;
      }
      if (!Double.isNaN(lastNonZeroS)) {
        if (Math.signum(value) != Math.signum(lastNonZeroValue)) {
          roots++;
          bracket = zeroSinceLastNonZero ? new double[] { zeroS, zeroS } : new double[] { lastNonZeroS, s };
        } else if (zeroSinceLastNonZero) {
          roots++;
          bracket = new double[] { zeroS, zeroS };
        }
      } else if (zeroSinceLastNonZero) {
        roots++;
        bracket = new double[] { zeroS, zeroS };
      }
      lastNonZeroS = s;
      lastNonZeroValue = value;
      zeroSinceLastNonZero = false;
    }
    if (zeroSinceLastNonZero) {
      roots++;
      bracket = new double[] { zeroS, zeroS };
    }
    if (roots == 0) {
      return new Result(MoneyWeightedReturnStatus.MWR_NO_ROOT_IN_RANGE, null);
    }
    if (roots > 1) {
      return new Result(MoneyWeightedReturnStatus.MWR_AMBIGUOUS, null);
    }
    return new Result(MoneyWeightedReturnStatus.MWR_CALCULATED, bisect(cashFlows, bracket[0], bracket[1]));
  }

  private static double bisect(double[][] cashFlows, double low, double high) {
    double valueLow = presentValue(cashFlows, low);
    for (int i = 0; i < MAX_BISECTION_STEPS && high - low >= BISECTION_WIDTH; i++) {
      double mid = (low + high) / 2;
      double valueMid = presentValue(cashFlows, mid);
      if (valueMid == 0) {
        return mid;
      }
      if (Math.signum(valueMid) == Math.signum(valueLow)) {
        low = mid;
        valueLow = valueMid;
      } else {
        high = mid;
      }
    }
    return (low + high) / 2;
  }

  /**
   * Return of the whole period.
   *
   * @param result result of {@link #solve}
   * @return the return as a ratio, -1 on total loss, null when no return could be determined
   */
  public static Double periodReturn(Result result) {
    if (result.status() == MoneyWeightedReturnStatus.MWR_TOTAL_LOSS) {
      return -1.0;
    }
    return result.periodLogReturn() == null ? null : Math.exp(result.periodLogReturn()) - 1;
  }

  /**
   * Return scaled to a year of 365 days, only for periods of at least {@link ReturnSeries#MIN_ANNUALIZATION_DAYS}
   * calendar days.
   *
   * @param result       result of {@link #solve}
   * @param calendarDays calendar days of the period
   * @return the annualized return as a ratio, -1 on total loss, null for a shorter period or without a return
   */
  public static Double annualizedReturn(Result result, long calendarDays) {
    if (calendarDays < ReturnSeries.MIN_ANNUALIZATION_DAYS) {
      return null;
    }
    if (result.status() == MoneyWeightedReturnStatus.MWR_TOTAL_LOSS) {
      return -1.0;
    }
    if (result.periodLogReturn() == null) {
      return null;
    }
    double annualized = Math.exp(result.periodLogReturn() * 365.0 / calendarDays) - 1;
    return Double.isFinite(annualized) ? annualized : null;
  }
}
