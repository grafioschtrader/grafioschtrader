package grafioschtrader.types;

/**
 * Notes attached to the series of the security position chart. A missing price or rate never turns into 0; the chart
 * names the cause instead, so a gap can be told apart from a genuine zero. The constant names are the NLS keys shown
 * to the user; the remark is neither persisted nor carries a byte value.
 */
public enum SecurityPositionSeriesRemark {
  /** The security was never held overnight in the scope; there is no phase and no series. */
  NO_HOLDING,
  /** A session day of the security's stock exchange before today has units but no price; the day is left out. */
  NOT_VALUED_DAYS,
  /**
   * A day lacks the closing rate into the main currency, or a transaction lacks the rate of its date; the affected
   * main currency figures are missing.
   */
  MISSING_FX,
  /** A day carries negative units of a non-margin security; that day has no value and is left out. */
  NEGATIVE_UNITS_DAYS,
  /**
   * A security transfer between two accounts of the scope falls into the phase. Invested capital, purchases and the
   * realized result restart at the transfer value there, as GT books the transfer; the drawdown is not affected.
   */
  INTERNAL_TRANSFER
}
