/**
 * One session of the card. Both movements are reported because they answer different questions:
 * {@link totalBalanceChangeMC} is what the client is worth more or less than on the session before,
 * {@link totalGainMC} is what the investments earned. The two differ by exactly {@link externalCashTransferMC}.
 */
export interface LastSessionsRow {
  date: string;
  totalBalanceMC: number;
  totalBalanceChangeMC: number;
  totalGainMC: number;
  externalCashTransferMC: number;
  /** Separately booked account and depot fees, positive although it is a cost, and contained in the result. */
  feeRealMC: number;
  /** Cash account interest, contained in the result for the same reason as the fee. */
  interestCashaccountRealMC: number;
  /** At least one held instrument was valued with a filled price, so the figures are an estimate. */
  substitute: boolean;
}

/** The card payload: the currency every amount is in, the totals over the listed sessions, and the sessions. */
export interface LastSessionsPerformance {
  currency: string;
  idPortfolio: number | null;
  /** Session the first row is measured against; it is not itself a row. */
  baseDate: string | null;
  totalGainMC: number;
  totalBalanceChangeMC: number;
  externalCashTransferMC: number;
  feeRealMC: number;
  interestCashaccountRealMC: number;
  /** Translation key explaining an empty card - a client that holds nothing is not a fault - or null when it has rows. */
  reasonKey: string | null;
  sessions: LastSessionsRow[];
}
