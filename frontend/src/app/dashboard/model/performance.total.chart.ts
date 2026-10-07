/** One point of the chart, the close of a trading day. */
export interface PerformanceTotalChartPoint {
  date: string;
  /** Securities + balance of the period performance: cash, securities and the result of closed margin positions. */
  totalBalanceMC: number;
  /** Deposits less withdrawals accumulated up to this day. */
  investedCapitalMC: number;
}

/**
 * The card payload. The currency is that of the client, or that of the portfolio when one is shown. A long period
 * arrives already reduced to weekly or monthly values, as {@link sampling} reports.
 */
export interface PerformanceTotalChart {
  currency: string;
  idPortfolio: number | null;
  /** The period shown, one of {@link ranges}. */
  range: string;
  /** Every period the card may ask for; the constant names are also their translation keys. */
  ranges: string[];
  sampling: 'DAY' | 'WEEK' | 'MONTH';
  /** The newest day with a value, null when there is none. */
  newestDate: string | null;
  /** The background task has not yet computed every day up to the newest one shown. */
  recalcPending: boolean;
  /** Translation key explaining an empty chart, or null when it has points. */
  reasonKey: string | null;
  points: PerformanceTotalChartPoint[];
}
