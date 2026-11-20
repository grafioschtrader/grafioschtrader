/**
 * Dividend key figures of one security, computed by the backend from the shared dividend table and not from the
 * distributions of the tenant. Every amount is split-adjusted and in the currency of the security. A figure is null
 * when its input is missing, for example without price, with too short a history or a year without exchange rate.
 *
 * Corresponds to the backend record grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.
 */
export interface SecurityDividendMetrics {
  /** Currency of the security and of every amount */
  currency: string | null;
  /** Number of dividends with an ex-date up to today */
  dividendCount: number;
  firstExDate: string | null;
  lastExDate: string | null;
  lastAmount: number | null;
  /** Ex-date of an already known future dividend */
  nextExDate: string | null;
  nextAmount: number | null;
  /** Last price, the base of the yields */
  price: number | null;
  /** Sum of the dividends of the last 365 days */
  dividendTTM: number | null;
  /** Trailing twelve month yield in percent */
  yieldTTM: number | null;
  paymentsPerYear: number | null;
  /** Newest dividend times payments per year, divided by the price, in percent */
  forwardYield: number | null;
  cagr1: number | null;
  cagr3: number | null;
  cagr5: number | null;
  cagr10: number | null;
  yearsWithoutCut: number | null;
  lastCutYear: number | null;
  lastCutPercent: number | null;
  /** Trailing yield plus five year growth rate */
  chowder: number | null;
  /** Trailing dividend divided by trailing earnings per share, in percent */
  payoutRatio: number | null;
  years: DividendYearAmount[];
  yieldHistory: DividendDateValue[];
  splits: DividendSplitMark[];
}

/** Sum of the dividends of one calendar year; partial marks the current year. */
export interface DividendYearAmount {
  year: number;
  amount: number | null;
  partial: boolean;
  cut: boolean;
}

/** One point of the trailing yield history. */
export interface DividendDateValue {
  date: string;
  value: number;
}

/** One split of the security. */
export interface DividendSplitMark {
  date: string;
  fromFactor: number;
  toFactor: number;
}

/**
 * Series of the dividend chart for one or several securities. With one security the chart shows its annual dividends,
 * trailing yield and splits; with several it compares the trailing yield and the annual dividend indexed to a common
 * base year, both free of a unit, so no currency is converted.
 *
 * Corresponds to the backend record grafioschtrader.reportviews.securitydividends.SecurityDividendChart.
 */
export interface SecurityDividendChart {
  /** First completed year in which every security paid for a whole year; null when there is none */
  baseYear: number | null;
  series: DividendChartSeries[];
}

/** The chart series of one security. */
export interface DividendChartSeries {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  years: DividendYearAmount[];
  yieldHistory: DividendDateValue[];
  splits: DividendSplitMark[];
  /** Annual dividend of the completed years from the base year on, as a percentage of the base year */
  index: DividendYearValue[];
}

/** A value of one calendar year. */
export interface DividendYearValue {
  year: number;
  value: number;
}
