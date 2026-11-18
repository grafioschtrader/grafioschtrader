package grafioschtrader.types;

/**
 * Why a row of the period performance per security lacks some of its figures. A missing price or rate never turns
 * into 0; the row names the cause instead, so a blank return or gain can be told apart from a genuine zero.
 *
 * <p>
 * The constants are listed in order of precedence: a row carries the first one that applies. The constant names are
 * the NLS keys shown to the user; the remark is neither persisted nor carries a byte value.
 * </p>
 */
public enum SecurityPeriodRemark {
  /**
   * The security is held at the close of the start or end date and that day has no price or no exchange rate for it.
   * All value figures and both returns are missing.
   */
  MISSING_VALUATION,
  /**
   * A non-zero flow of the period has no closing rate from the security currency into the main currency on the day it
   * is placed. The main currency flows, the main currency gain and the main currency return are missing.
   */
  MISSING_EXCHANGE_RATE,
  /** A valuation day of the period carries negative units of a non-margin security; both returns are missing. */
  NEGATIVE_UNITS,
  /** No interval of the period has invested capital, for example income received after the sale; no return. */
  NO_CAPITAL_BASE,
  /** A margin instrument, whose value is the open result and not invested capital; gain only. */
  MARGIN_GAIN_ONLY
}
