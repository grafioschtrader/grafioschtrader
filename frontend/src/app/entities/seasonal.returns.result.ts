/**
 * Result of a seasonality heat map calculation for a single security or currency pair. Mirrors the backend DTO
 * {@code grafioschtrader.dto.SeasonalReturnsResult}: a matrix of period returns (rows = years, columns = months or
 * quarters, in percent), a trailing annual-return column and per-column footer statistics. The capability flags tell
 * the UI which of the dividend and currency toggles apply to the analysed instrument.
 */
export interface SeasonalReturnsResult {
  /** Column granularity of the returned matrix. */
  periodType: SeasonalPeriodType;
  /** ISO currency code the returns are expressed in (instrument currency or tenant main currency). */
  currency: string;
  /** True when dividends/interest were included in the returns. */
  dividendsIncluded: boolean;
  /** True when the returns were converted into the tenant main currency. */
  inTenantCurrency: boolean;
  /** True when the instrument pays dividends/interest, i.e. the include-dividends toggle is meaningful. */
  dividendsAvailable: boolean;
  /** True when a conversion into the tenant main currency is possible for this instrument. */
  currencyConversionAvailable: boolean;
  /** One row per calendar year, ordered descending (most recent first). */
  yearRows: SeasonalYearRow[];
  /** Footer statistics, one per period column in column order, followed by one final entry for the annual column. */
  columnStats: SeasonalColumnStat[];
}

/** Period returns of a single calendar year. */
export interface SeasonalYearRow {
  /** The calendar year. */
  year: number;
  /** Period returns in percent, in column order (12 for monthly, 4 for quarterly); null where not computable. */
  periodReturns: (number | null)[];
  /** Full-year return in percent, or null when it cannot be computed. */
  annualReturn: number | null;
  /** True when the year does not contain all period columns (typically the first/last year). */
  partial: boolean;
}

/** Aggregated statistics for one matrix column across all years. */
export interface SeasonalColumnStat {
  /** Mean return of the column in percent. */
  mean: number | null;
  /** Median return of the column in percent. */
  median: number | null;
  /** Standard deviation of the column returns in percent. */
  stdDev: number | null;
  /** Share of years with a positive return in this column, in percent (hit rate). */
  pctPositive: number | null;
  /** Number of years contributing a value to this column. */
  count: number;
}

export enum SeasonalPeriodType {
  MONTHLY = 'MONTHLY',
  QUARTERLY = 'QUARTERLY'
}
