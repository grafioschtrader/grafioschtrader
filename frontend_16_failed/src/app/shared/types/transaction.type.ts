export enum TransactionType {
  /** Withdrawal cash */
  WITHDRAWAL = 0,
  /** Deposit cash */
  DEPOSIT = 1,
  /** Interest on cash account */
  INTEREST_CASHACCOUNT = 2,
  /** Fee cash or security account, not on a finance instrument */
  FEE = 3,
  /** Accumulate (buy) shares */
  ACCUMULATE = 4,
  /** Reduce (sell) shares */
  REDUCE = 5,
  /** Dividend and Interest on security, can be +/-*/
  DIVIDEND = 6,
  /** Finance cost on a finance instrument, can be +/- */
  FINANCE_COST = 7,


  /** Dividend DRP (Dividend Reinvestment Programme) */
  HYPOTHETICAL_BUY = 9,
  /** Not used for a real Transaction, it may be used for a simulated postion sell */
  HYPOTHETICAL_SELL = 10,
  ACCRUED_INTEREST = 11

}
