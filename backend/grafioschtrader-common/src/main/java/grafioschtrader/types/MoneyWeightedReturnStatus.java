package grafioschtrader.types;

/**
 * Outcome of the search for the money-weighted return (internal rate of return) of a period.
 *
 * <p>
 * An internal rate of return may have several solutions or none when deposits and withdrawals alternate. Instead of
 * reporting an arbitrary root, the calculation names the case. The constant names are the NLS keys shown to the user;
 * the status is neither persisted nor carries a byte value.
 * </p>
 */
public enum MoneyWeightedReturnStatus {
  /** Exactly one solution was found in the searched range. */
  MWR_CALCULATED,
  /** Nothing flowed back to the investor, the whole capital was lost; the return is -100 %. */
  MWR_TOTAL_LOSS,
  /** The present value of the flows never changes its sign between -100 % and a very high return. */
  MWR_NO_ROOT_IN_RANGE,
  /** Two or more solutions exist, typically because deposits and withdrawals alternate. */
  MWR_AMBIGUOUS,
  /** Fewer than two non-zero flows or no payment by the investor at all. */
  MWR_INSUFFICIENT_DATA
}
