package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;

/**
 * The result figures of a historical replay, calculated from its daily equity and its closed round trips.
 *
 * <p>
 * Everything here is a pure function of two lists, which is what makes the conventions checkable by hand instead of
 * only by running a replay. The conventions themselves are decisions, not facts, and are recorded with the run so that
 * a reader knows which ones a number was produced under:
 * </p>
 *
 * <ul>
 * <li>The series starts at the opening equity of the environment. Opening capital is not profit, so the first point is
 * a starting level and never a gain.</li>
 * <li>Annualization of the return uses calendar days and a 365 day year; annualization of the deviation uses 252
 * trading days, because a standard deviation is measured per observation and a year has about that many.</li>
 * <li>The Sharpe ratio is calculated against a risk free rate of zero, from simple daily returns and their sample
 * standard deviation.</li>
 * <li>Deposits and withdrawals are external capital flows. Each period return compares closing equity with the previous
 * equity plus the flows booked before that period's valuation.</li>
 * <li>A metric that has no value is empty rather than zero. Fewer than two observations give no return series, a series
 * that does not move gives no deviation to divide by, and an equity that starts at or below zero gives no relative
 * return at all.</li>
 * <li>A day whose closing state could not be valued completely is not an observation. Its equity would be the sum of
 * whatever happened to be priced, so counting it would report a loss the portfolio never took and would annualize the
 * result over days the run never measured. Such days are dropped here and listed in the course of the run instead.</li>
 * <li>The drawdown duration counts the calendar days of the longest phase the cash-flow adjusted wealth index spent
 * below a previous high, from that high to the first day that regains it. A phase still open on the end date counts up
 * to the last observation.</li>
 * <li>A round trip counts once it is closed. A position still open on the end date belongs to no trade, so it neither
 * wins nor loses; its value is in the terminal equity instead.</li>
 * </ul>
 *
 * <p>
 * The return arithmetic is shared with the period performance report through {@link ReturnSeries}; the replay uses
 * {@link Convention#REPLAY}, which keeps the start-of-period convention for every flow described above.
 * </p>
 */
public final class AlgoReplayMetrics {

  /** Trading days a standard deviation of daily returns is scaled by to become an annual figure. */
  public static final double TRADING_DAYS_PER_YEAR = ReturnSeries.TRADING_DAYS_PER_YEAR;

  /**
   * Closing equity of the environment on one day of the run, in tenant currency.
   *
   * @param priced           whether every position and every currency of that day had a usable closing price. A point
   *                         that is not priced carries an equity that is missing whatever could not be valued, so it is
   *                         kept for the record but is no observation for the figures below.
   * @param externalCashFlow deposits and withdrawals booked since the preceding fully priced observation, expressed in
   *                         tenant currency
   */
  public record EquityPoint(LocalDate date, double equity, boolean priced, double externalCashFlow) {
    public EquityPoint(LocalDate date, double equity, boolean priced) {
      this(date, equity, priced, 0);
    }
  }

  /**
   * One closed round trip.
   *
   * @param realizedGain sum of the tenant currency cash flows of the lifecycle: negative for every purchase, positive
   *                     for every sale, so the sum is what the round trip earned or lost
   */
  public record Trade(double realizedGain) {
  }

  /** Empty fields are undefined rather than zero; the result view says which and why. */
  public record Metrics(Double totalReturn, Double annualizedReturn, Double maxDrawdown,
      Integer maxDrawdownDurationDays, Double sharpeRatio, int totalTrades, int winningTrades, int losingTrades) {
  }

  private AlgoReplayMetrics() {
  }

  /**
   * @param series the closing equity of every evaluated day, in order, starting with the opening day
   * @param trades the round trips the run closed
   * @return the figures of the run, with every metric that has no defined value left empty
   */
  public static Metrics of(List<EquityPoint> series, List<Trade> trades) {
    List<EquityPoint> observed = series.stream().filter(EquityPoint::priced).toList();
    ReturnSeries returnSeries = returnSeries(observed);
    Double totalReturn = returnSeries.totalReturn();
    int winning = (int) trades.stream().filter(t -> t.realizedGain() > 0).count();
    int losing = (int) trades.stream().filter(t -> t.realizedGain() < 0).count();
    return new Metrics(totalReturn, ReturnSeries.annualize(totalReturn, returnSeries.calendarDays(), 0),
        observed.isEmpty() ? null : maxDrawdown(returnSeries),
        observed.isEmpty() ? null : maxDrawdownDurationDays(returnSeries), sharpeRatio(returnSeries.usableReturns()),
        trades.size(), winning, losing);
  }

  /** Terminal calendar-day income affects return and drawdown, without adding a Sharpe trading-day observation. */
  public static Metrics of(List<EquityPoint> series, List<Trade> trades, EquityPoint terminal) {
    Metrics result = of(valuationSeries(series, terminal), trades);
    return new Metrics(result.totalReturn(), result.annualizedReturn(), result.maxDrawdown(),
        result.maxDrawdownDurationDays(), sharpeRatio(returnSeries(series.stream().filter(EquityPoint::priced).toList()).usableReturns()),
        result.totalTrades(), result.winningTrades(), result.losingTrades());
  }

  /**
   * The series the return and drawdown figures of a completed run are calculated from, which is also the series its
   * equity curve shows. The valuation of the end date is appended only when it lies after the last evaluated day: an end
   * date that is itself a trading day was already valued as one.
   *
   * @param series   the closing equity of every evaluated day, in order
   * @param terminal the valuation of the end date
   * @return a new list holding the series and, where it extends it, the terminal point
   */
  public static List<EquityPoint> valuationSeries(List<EquityPoint> series, EquityPoint terminal) {
    List<EquityPoint> valuationSeries = new ArrayList<>(series);
    if (valuationSeries.isEmpty() || terminal.date().isAfter(valuationSeries.getLast().date())) {
      valuationSeries.add(terminal);
    }
    return valuationSeries;
  }

  /** A series without any usable return has no decline, which is a drawdown of zero rather than an empty one. */
  private static Double maxDrawdown(ReturnSeries returnSeries) {
    Double depth = returnSeries.drawdown().depth();
    return depth == null ? 0.0 : depth;
  }

  /** Like the depth, a series without any usable return never was below a high, which is a duration of zero. */
  private static Integer maxDrawdownDurationDays(ReturnSeries returnSeries) {
    Integer days = returnSeries.maxDrawdownDurationDays();
    return days == null ? 0 : days;
  }

  private static Double sharpeRatio(double[] returns) {
    if (returns.length < 2) {
      return null;
    }
    double mean = 0;
    for (double value : returns) {
      mean += value;
    }
    mean /= returns.length;
    double sumOfSquares = 0;
    for (double value : returns) {
      sumOfSquares += (value - mean) * (value - mean);
    }
    double deviation = Math.sqrt(sumOfSquares / (returns.length - 1));
    if (deviation <= 0) {
      return null;
    }
    return mean / deviation * Math.sqrt(TRADING_DAYS_PER_YEAR);
  }

  /**
   * Maps the observed points onto the shared return series. Each flow counts as booked before the valuation of its
   * period, so the capital base is the previous equity plus the flow.
   */
  private static ReturnSeries returnSeries(List<EquityPoint> observed) {
    return ReturnSeries.of(
        observed.stream()
            .map(point -> new ValuationPoint(point.date(), point.equity(), point.externalCashFlow(), 0, true)).toList(),
        Convention.REPLAY);
  }
}
