/** The three periods a movers card offers. The codes come from the server and are translated for display. */
export type HoldingMoversBranchType = 'INTRADAY' | 'LAST_TRADING_DAY' | 'CHOSEN_DATE';

/** Which of the two orderings of a branch a table shows. */
export type HoldingMoversRanking = 'byPercentage' | 'byAmount';

/**
 * One instrument within a branch. The two dates are the ones actually used; when they differ from the dates the branch
 * names, the row covers a longer span than its branch and says so through {@link substitute}.
 */
export interface HoldingMoversRow {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  units: number;
  price: number;
  previousPrice: number;
  changePercentage: number;
  amountMC: number;
  usedDate: string | null;
  usedPreviousDate: string | null;
  substitute: boolean;
}

/**
 * One period of the card, with what it could rank and what it had to leave out, so a short list is never mistaken for a
 * quiet market.
 */
export interface HoldingMoversBranch {
  branch: HoldingMoversBranchType;
  date: string | null;
  previousDate: string | null;
  asOf: string | null;
  rankedCount: number;
  substituteCount: number;
  omittedCount: number;
  marginCount: number;
  /** Translation key explaining an empty branch - a weekend or holiday is not a fault - or null when it has rows. */
  reasonKey: string | null;
  byPercentage: HoldingMoversRow[];
  byAmount: HoldingMoversRow[];
}

/** The card payload: the currency every amount is in, the requested row count, and the branches in display order. */
export interface HoldingMovers {
  currency: string;
  topN: number;
  branches: HoldingMoversBranch[];
}
