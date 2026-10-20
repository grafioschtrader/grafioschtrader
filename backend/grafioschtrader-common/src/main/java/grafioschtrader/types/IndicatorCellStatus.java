package grafioschtrader.types;

/**
 * Distinguishes missing inputs from invalid data and evaluation failures. {@link #NO_FUNDAMENTAL} means that a
 * fundamental variable of the expression has no selected, fresh value for the instrument, or no exchange rate into its
 * currency.
 */
public enum IndicatorCellStatus {
  OK, NO_HISTORY, NO_BENCHMARK, NO_PRICE, NO_DIVIDEND_DATA, NO_FX_RATE, NO_FUNDAMENTAL, NOT_APPLICABLE, INVALID_DATA,
  ARITHMETIC, ERROR
}
