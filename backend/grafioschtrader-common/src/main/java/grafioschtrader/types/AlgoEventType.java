package grafioschtrader.types;

/**
 * What one row of the replay audit trail records.
 *
 * <p>
 * A decision and its execution are separate rows, because they happen on different days: a strategy decides at the
 * close of a trading day and the order is filled at the next eligible close. The row that explains why nothing happened
 * is as important as the row that books a trade, so a blocked action and unavailable data are event types of their own
 * rather than an absence of rows.
 * </p>
 */
public enum AlgoEventType {

  /** Opens the trail with the run definition: opening date, end date and the conventions it is calculated under. */
  RUN_START,
  /**
   * The one-time purchase that brings an environment opening without positions to its configured targets became due. It
   * is sized by the rebalancing rules but is not a rebalancing: it happens once, before the checkpoint interval starts
   * counting, and only for an environment that held nothing on its opening date.
   */
  ALLOCATION_PLAN,
  /** A line of that purchase was executed at the closing price of the next eligible trading day. */
  ALLOCATION_FILL,
  /** A strategy proposed an actionable entry, addition, partial or full exit. */
  DECISION,
  /** A proposal was executed at the closing price of the next eligible trading day. */
  FILL,
  /** A proposal was refused, for example by a cooldown, an exhausted budget or a pending exit on the instrument. */
  BLOCKED,
  /** No decision could be taken, for example because history, a closing price or an exchange rate was missing. */
  UNAVAILABLE,
  /**
   * A rebalancing checkpoint was reached and at least one instrument lay outside its tolerance. The interval decides
   * whether the allocation is compared at all; a drift between two checkpoints is not a trigger of its own.
   */
  REBALANCE_PLAN,
  /** A line of that comparison was executed at the closing price of the next eligible trading day. */
  REBALANCE_FILL,
  /**
   * Money was moved between two cash accounts of the environment so that an order could be paid for. A rebalancing is
   * sized against the whole equity of the environment while one order is settled by one cash account, so an environment
   * whose cash is spread over several accounts has to bring it together before it can execute its allocation - which is
   * what a holder of such a portfolio would do too. The row names the instrument the transfer was made for, so it reads
   * next to the fill it enabled.
   */
  FUNDING_TRANSFER,
  /** A frozen cash-account standing order was booked on its adjusted effective date. */
  CASH_STANDING_ORDER,
  /** Gross income earned by the units held before the ex-date. */
  DIVIDEND_ENTITLEMENT,
  /** An earned receivable was paid into the simulation cash ledger. */
  DIVIDEND_PAYMENT,
  /**
   * A directly held bond that reached {@code Security.activeToDate} was repaid at par, with accrued interest when the
   * due date is not a coupon date.
   */
  MATURITY_REDEMPTION,
  /**
   * A leftover non-bond position was closed at the last close on or before {@code Security.activeToDate}, because the
   * instrument is no longer tradable.
   */
  TERMINAL_CLOSE,
  /** Closes the trail, including for a cancelled or failed run. */
  RUN_END,
  /** An imported CFD, Forex or leveraged position was liquidated before ordinary simulation trading. */
  OPENING_EXCLUDED_CLOSE,
  /** A recurring custody charge booked on its settlement date. */
  CUSTODY_FEE,
  /** Commission credits consumed by an accepted trade. */
  CUSTODY_CREDIT;

}
