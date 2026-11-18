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
}
