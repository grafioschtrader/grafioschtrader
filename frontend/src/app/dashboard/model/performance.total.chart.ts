import type { DashboardBenchmarkNotice } from './benchmark.notice.text';

/** One point of the chart, the close of a trading day. */
export interface PerformanceTotalChartPoint {
  date: string;
  /** Securities + balance of the period performance: cash, securities and the result of closed margin positions. */
  totalBalanceMC: number;
  /** Deposits less withdrawals accumulated up to this day. */
  investedCapitalMC: number;
  /**
   * Value of the shadow portfolio: the same deposits and withdrawals invested in the benchmark. Absent or null without
   * a benchmark, before its first covered day and after a withdrawal it could not fund.
   */
  shadowValueMC?: number | null;
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
  /** Name of the stored benchmark the shadow line follows, null without one. */
  benchmarkName?: string | null;
  /** First day of the shadow line. */
  benchmarkStartDate?: string | null;
  /** Conditions of the comparison decided on the server; the code is an NLS key, the parameters its placeholders. */
  notices?: DashboardBenchmarkNotice[];
}
