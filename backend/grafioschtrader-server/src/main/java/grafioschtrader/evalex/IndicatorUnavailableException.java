package grafioschtrader.evalex;

/**
 * Raised when an indicator cannot be computed because the instrument has too little price history.
 *
 * <p>
 * It exists because the alternative that was in place is worse than an error. The indicator functions used to log a
 * warning and return {@code 0.0}, which makes {@code price > SMA(200)} true for every instrument whose history is too
 * short and {@code price < SMA(200)} false for all of them - a fabricated signal that looks exactly like a real one. An
 * alert is specified to report unavailable data rather than to invent a value for it, so the evaluation of that one
 * instrument is abandoned instead.
 * </p>
 *
 * <p>
 * Unchecked, because it is thrown from inside the EvalEx function contract, which does not declare it.
 * </p>
 */
public class IndicatorUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param indicator the indicator that could not be computed, for example SMA
   * @param period    the requested period
   * @param available the number of observations actually loaded
   */
  public IndicatorUnavailableException(String indicator, int period, int available) {
    super(indicator + "(" + period + ") needs more than " + period + " observations, " + available + " are available");
  }

}
