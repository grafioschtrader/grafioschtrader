/**
 * Response of the period performance per security. Amounts in main currency (MC) are in the tenant currency for the
 * client and in the portfolio currency for a portfolio. A figure that cannot be formed is null; the remark of the row
 * says why. Returns are in percent, 12.34 means 12.34 %.
 */
export interface SecurityPeriodPerformance {
  mainCurrency: string;
  /** Excluded base date of the calculated period, yyyy-MM-dd. */
  dateFrom: string;
  /** Last date of the calculated period, yyyy-MM-dd. */
  dateTo: string;
  /** Ordered by gainPeriodMC descending with nulls last, then by name. */
  rows: SecurityPeriodPerformanceRow[];
  totals: SecurityPeriodPerformanceTotals;
  /** Rows without gainPeriodMC; when greater than 0 the totals are a partial total. */
  omittedRows: number;
  /** The benchmark every row is compared with over its own holding days; null without a benchmark. */
  benchmark: SecurityPeriodBenchmark | null;
}

/** Identity, conversion and return basis of the benchmark of the view, with the notices of its comparison. */
export interface SecurityPeriodBenchmark {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  /** NLS key of the origin. */
  source: string;
  mainCurrency: string;
  fxPair: string | null;
  fxInverse: boolean;
  /** NLS key of what the benchmark return contains. */
  returnBasis: string;
  notices: { code: string; parameters: Record<string, string> }[];
}

/** Sums of the main currency amounts over the rows that carry them. */
export interface SecurityPeriodPerformanceTotals {
  valueStartMC: number;
  valueEndMC: number;
  inflowMC: number;
  outflowMC: number;
  gainPeriodMC: number;
}

/** Why a row lacks figures; the constant names are the text keys. */
export type SecurityPeriodRemark =
  'MISSING_VALUATION' | 'MISSING_EXCHANGE_RATE' | 'NEGATIVE_UNITS' | 'NO_CAPITAL_BASE' | 'MARGIN_GAIN_ONLY';

/** One security of the period. */
export interface SecurityPeriodPerformanceRow {
  idSecuritycurrency: number;
  name: string;
  isin: string | null;
  currency: string;
  marginInstrument: boolean;
  /** Calendar days after the start date up to the end date on which the security was held at the close. */
  holdingDays: number;
  /** Units as traded on the start date, null for a margin instrument. */
  unitsStart: number | null;
  unitsEnd: number | null;
  valueStartMC: number | null;
  valueEndMC: number | null;
  /** Purchases with their cost; for a margin instrument settled losses and cost. */
  inflowMC: number | null;
  /** Sales and income after cost; for a margin instrument settled gains. */
  outflowMC: number | null;
  /** Gain of the period in security currency. */
  gainPeriod: number | null;
  /** valueEndMC - valueStartMC - inflowMC + outflowMC. */
  gainPeriodMC: number | null;
  twrPercent: number | null;
  twrSecurityCurrencyPercent: number | null;
  remark: SecurityPeriodRemark | null;
  /** Benchmark return in percent over exactly the days the position held capital; null when not comparable. */
  benchmarkTwrPercent?: number | null;
  /** twrPercent minus benchmarkTwrPercent in percentage points. */
  excessTwrPercent?: number | null;
  /** Why the comparison of the row is empty; null without a benchmark. */
  benchmarkCoverage?: 'BENCHMARK_COVERED' | 'BENCHMARK_PARTIAL' | 'BENCHMARK_NONE' | null;
}
