package grafioschtrader.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
 * <li>A round trip counts once it is closed. A position still open on the end date belongs to no trade, so it neither
 * wins nor loses; its value is in the terminal equity instead.</li>
 * </ul>
 */
public final class AlgoReplayMetrics {

  /** Trading days a standard deviation of daily returns is scaled by to become an annual figure. */
  public static final double TRADING_DAYS_PER_YEAR = 252.0;

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
  public record Metrics(Double totalReturn, Double annualizedReturn, Double maxDrawdown, Double sharpeRatio,
      int totalTrades, int winningTrades, int losingTrades) {
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
    double[] returns = periodReturns(observed);
    Double totalReturn = totalReturn(returns);
    int winning = (int) trades.stream().filter(t -> t.realizedGain() > 0).count();
    int losing = (int) trades.stream().filter(t -> t.realizedGain() < 0).count();
    return new Metrics(totalReturn, annualizedReturn(observed, totalReturn),
        observed.isEmpty() ? null : maxDrawdown(returns), sharpeRatio(returns), trades.size(), winning, losing);
  }

  /** Terminal calendar-day income affects return and drawdown, without adding a Sharpe trading-day observation. */
  public static Metrics of(List<EquityPoint> series, List<Trade> trades, EquityPoint terminal) {
    List<EquityPoint> valuationSeries = new java.util.ArrayList<>(series);
    if (valuationSeries.isEmpty() || terminal.date().isAfter(valuationSeries.getLast().date()))
      valuationSeries.add(terminal);
    Metrics result = of(valuationSeries, trades);
    return new Metrics(result.totalReturn(), result.annualizedReturn(), result.maxDrawdown(),
        sharpeRatio(periodReturns(series.stream().filter(EquityPoint::priced).toList())), result.totalTrades(),
        result.winningTrades(), result.losingTrades());
  }

  private static Double totalReturn(double[] returns) {
    if (returns.length == 0) {
      return null;
    }
    double wealth = 1;
    for (double value : returns) {
      wealth *= 1 + value;
    }
    return wealth - 1;
  }

  private static Double annualizedReturn(List<EquityPoint> series, Double totalReturn) {
    if (totalReturn == null) {
      return null;
    }
    long days = ChronoUnit.DAYS.between(series.getFirst().date(), series.getLast().date());
    // A run shorter than a day cannot be scaled to a year, and a portfolio wiped out entirely has no growth rate.
    if (days <= 0 || 1 + totalReturn <= 0) {
      return null;
    }
    return Math.pow(1 + totalReturn, 365.0 / days) - 1;
  }

  private static Double maxDrawdown(double[] returns) {
    if (returns.length == 0) {
      return 0.0;
    }
    double wealth = 1;
    double peak = 1;
    double worst = 0;
    for (double value : returns) {
      wealth *= 1 + value;
      peak = Math.max(peak, wealth);
      worst = Math.min(worst, wealth / peak - 1);
    }
    return worst;
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

  /** Cash-flow-adjusted returns between consecutive observations. */
  private static double[] periodReturns(List<EquityPoint> series) {
    double[] returns = new double[Math.max(0, series.size() - 1)];
    int count = 0;
    for (int i = 1; i < series.size(); i++) {
      double capitalBase = series.get(i - 1).equity() + series.get(i).externalCashFlow();
      if (capitalBase > 0) {
        returns[count++] = series.get(i).equity() / capitalBase - 1;
      }
    }
    double[] result = new double[count];
    System.arraycopy(returns, 0, result, 0, count);
    return result;
  }
}
