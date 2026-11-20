package grafioschtrader.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//@formatter:off
/**
 * Drawdown of the money in one position: how far its value lies below its own reference high. Used by the security
 * position chart, whose position receives new money with every purchase and gives some back with every sale or
 * distribution.
 *
 * <p>
 * The reference high {@code M} starts at 0. A purchase is new money that starts at its own high and raises {@code M}
 * by its full amount. A sale or a distribution takes a share {@code f} of the position out and the same share of
 * {@code M} with it, so a sale at the valuation price leaves the drawdown unchanged. Inflows count from the start of a
 * point, outflows at its close, as in {@link ReturnSeries.Convention#REPORT}:
 * </p>
 *
 * <pre>
 * base_i  = M_(i-1) + in_i                       no drawdown when base_i &lt;= 0
 * ratio_i = max(0, (V_i + out_i) / base_i)       a total loss is -100 %, never below
 * dd_i    = min(0, ratio_i - 1)
 * f_i     = out_i / (V_i + out_i)                0 without outflow, 1 when nothing is left
 * M_i     = max(base_i, V_i + out_i) * (1 - f_i)
 * </pre>
 *
 * <p>
 * A purchase above the valuation price shows a drawdown on its first day, measured against the money paid. On the day
 * of the last sale the value is 0, {@code f} is 1 and the ratio is the share of the high the proceeds recovered, so a
 * position closed at a loss ends with that loss and not with -100 %. A point without a value keeps its flows for the
 * next valued point. The class is a pure function of its steps; figures are ratios (-0.2 means -20 %).
 * </p>
 */
//@formatter:on
public final class MoneyDrawdown {

  /** Tolerance with which a value counts as back at the reference high, the same as in {@link ReturnSeries}. */
  public static final double RECOVERY_TOLERANCE = 1e-12;

  private MoneyDrawdown() {
  }

  /**
   * One point of the position.
   *
   * @param date    the date of the point
   * @param value   value at the close, null when the point has no value; its flows move to the next valued point
   * @param inflow  money that went into the position on this point, at least 0
   * @param outflow money that came out of the position on this point as a positive amount
   */
  public record Step(LocalDate date, Double value, double inflow, double outflow) {
  }

  /**
   * The episode of the deepest decline.
   *
   * @param depth        the deepest drawdown as a ratio (at most 0), null without any valued point
   * @param peakDate     last point at the reference high before the trough, or the first valued point when the series
   *                     starts below its high; null without a decline
   * @param troughDate   point of the deepest drawdown, null without a decline
   * @param recoveryDate first point after the trough back at the reference high, null while not regained
   * @param durationDays calendar days from the peak to the recovery or to the last valued point
   */
  public record Episode(Double depth, LocalDate peakDate, LocalDate troughDate, LocalDate recoveryDate,
      Integer durationDays) {
  }

  /**
   * The longest run below the reference high.
   *
   * @param from         last point at the high before the run, or the first valued point
   * @param to           first point back at the high, or the last valued point while the run is open
   * @param durationDays calendar days from {@code from} to {@code to}
   */
  public record Underwater(LocalDate from, LocalDate to, int durationDays) {
  }

  /**
   * Drawdown per step together with its summary.
   *
   * @param drawdown          one ratio per step, null where the step has no value or no capital base
   * @param maxDrawdown       the episode of the deepest decline
   * @param longestUnderwater the longest run below the high, null when it is the episode of {@code maxDrawdown} or
   *                          there is none
   * @param current           drawdown of the last valued point, null without any
   */
  public record Result(List<Double> drawdown, Episode maxDrawdown, Underwater longestUnderwater, Double current) {
  }

  /**
   * Calculates the drawdown of every step and its summary.
   *
   * @param steps the points of the position in ascending date order
   * @return the drawdown per step and the summary
   */
  public static Result of(List<Step> steps) {
    List<Double> drawdown = new ArrayList<>(steps.size());
    double mark = 0;
    double carriedIn = 0;
    double carriedOut = 0;
    for (Step step : steps) {
      double in = step.inflow() + carriedIn;
      double out = step.outflow() + carriedOut;
      if (step.value() == null) {
        carriedIn = in;
        carriedOut = out;
        drawdown.add(null);
        continue;
      }
      carriedIn = 0;
      carriedOut = 0;
      double base = mark + in;
      double total = step.value() + out;
      drawdown.add(base <= 0 ? null : drawdownOf(Math.max(0, total / base)));
      double share = out <= 0 ? 0 : (total <= 0 ? 1 : Math.min(1, out / total));
      mark = Math.max(base, total) * (1 - share);
    }
    return summarize(steps, drawdown);
  }

  private static double drawdownOf(double ratio) {
    return ratio >= 1 - RECOVERY_TOLERANCE ? 0 : ratio - 1;
  }

  private static Result summarize(List<Step> steps, List<Double> drawdown) {
    List<LocalDate> dates = new ArrayList<>();
    List<Double> values = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      if (drawdown.get(i) != null) {
        dates.add(steps.get(i).date());
        values.add(drawdown.get(i));
      }
    }
    if (values.isEmpty()) {
      return new Result(drawdown, new Episode(null, null, null, null, null), null, null);
    }
    Episode maxDrawdown = maxDrawdown(dates, values);
    Underwater longest = longestUnderwater(dates, values);
    if (longest != null && longest.from().equals(maxDrawdown.peakDate())) {
      longest = null;
    }
    return new Result(drawdown, maxDrawdown, longest, values.getLast());
  }

  private static Episode maxDrawdown(List<LocalDate> dates, List<Double> values) {
    double depth = Collections.min(values);
    if (depth == 0) {
      return new Episode(0.0, null, null, null, 0);
    }
    int trough = values.indexOf(depth);
    int peak = 0;
    for (int i = trough; i >= 0; i--) {
      if (values.get(i) == 0) {
        peak = i;
        break;
      }
    }
    Integer recovery = null;
    for (int i = trough + 1; i < values.size() && recovery == null; i++) {
      if (values.get(i) == 0) {
        recovery = i;
      }
    }
    LocalDate end = recovery == null ? dates.getLast() : dates.get(recovery);
    return new Episode(depth, dates.get(peak), dates.get(trough), recovery == null ? null : dates.get(recovery),
        (int) ChronoUnit.DAYS.between(dates.get(peak), end));
  }

  private static Underwater longestUnderwater(List<LocalDate> dates, List<Double> values) {
    Underwater longest = null;
    int runStart = -1;
    int lastAtHigh = 0;
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) < 0) {
        if (runStart < 0) {
          runStart = values.get(lastAtHigh) == 0 ? lastAtHigh : i;
        }
      } else {
        if (runStart >= 0) {
          longest = longer(longest, dates.get(runStart), dates.get(i));
          runStart = -1;
        }
        lastAtHigh = i;
      }
    }
    if (runStart >= 0) {
      longest = longer(longest, dates.get(runStart), dates.getLast());
    }
    return longest;
  }

  private static Underwater longer(Underwater longest, LocalDate from, LocalDate to) {
    int days = (int) ChronoUnit.DAYS.between(from, to);
    return longest == null || days > longest.durationDays() ? new Underwater(from, to, days) : longest;
  }
}
