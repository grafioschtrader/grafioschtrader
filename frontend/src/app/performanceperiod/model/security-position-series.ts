/**
 * Response of the security position chart: value, invested capital, results, returns and drawdown of one holding phase
 * of one security, in the scope of the view the chart was opened from. The arrays are columnar: index i of every
 * array belongs to dates[i]. Amounts are in the currency of their series, returns and drawdowns in percent (12.34
 * means 12.34 %). A figure that cannot be formed is null; the remarks say why.
 */
export interface SecurityPositionSeries {
  idSecuritycurrency: number;
  securityName: string;
  securityCurrency: string;
  /** Tenant currency for the tenant scope, portfolio currency otherwise. */
  mainCurrency: string;
  /** Name of the tenant, portfolio or security account the series are calculated for. */
  scopeName: string;
  /** True for a CFD or Forex position: invested is the entry value of the open positions, value adds the open result. */
  marginInstrument: boolean;
  /** Every holding phase of the security in the scope, newest first. */
  phases: SecurityPositionPhase[];
  /** First day of the phase the series belong to, yyyy-MM-dd; null without any phase. */
  phaseFrom: string | null;
  /** Closing day of that phase, null while it is open. */
  phaseTo: string | null;
  /** Date of the last point, null without any point. */
  lastValuedDate: string | null;
  remarks: SecurityPositionSeriesRemark[];
  /** One date per point, yyyy-MM-dd. */
  dates: string[];
  unitsAsTraded: number[];
  /** Series in the currency of the security; null without any phase. */
  security: SecurityPositionCurrencySeries | null;
  /** Series in the main currency, null when it equals the currency of the security. */
  main: SecurityPositionCurrencySeries | null;
  events: SecurityPositionEvent[];
}

/** One holding phase: from the first day with units after a day without units to the next day without units. */
export interface SecurityPositionPhase {
  from: string;
  /** Null while the phase is open. */
  to: string | null;
  open: boolean;
  /** Realized result plus income of a closed phase in the currency of the security; null for the open phase. */
  totalResultSC: number | null;
}

/** One transaction of the phase, shown as a marker. */
export interface SecurityPositionEvent {
  date: string;
  transactionType: string;
  units: number | null;
  /** Cost of a purchase, proceeds of a sale, income received, in the currency of the security. */
  amountSC: number;
  /** True when the transaction is a leg of a security transfer. */
  transfer: boolean;
}

/** The series of one currency; every array has one entry per date. */
export interface SecurityPositionCurrencySeries {
  currency: string;
  value: (number | null)[];
  invested: (number | null)[];
  purchases: (number | null)[];
  income: (number | null)[];
  realized: (number | null)[];
  /** Value - invested: the open result in money; purchases and sales cause no jump. */
  openResult: (number | null)[];
  /** Value - invested + income + realized: the total result of the phase in money. */
  totalResult: (number | null)[];
  openPerformance: (number | null)[];
  totalPerformance: (number | null)[];
  drawdown: (number | null)[];
  priceChange: (number | null)[];
  priceDrawdown: (number | null)[];
  maxDrawdown: SecurityPositionDrawdownEpisode;
  /** Null when it is the episode of maxDrawdown. */
  longestUnderwater: SecurityPositionUnderwaterRun | null;
  /** Drawdown of the last point; for a closed phase the value at the sale. */
  currentDrawdown: number | null;
}

/** The episode of the deepest drawdown. */
export interface SecurityPositionDrawdownEpisode {
  depth: number | null;
  peakDate: string | null;
  troughDate: string | null;
  /** Null while the high is not regained. */
  recoveryDate: string | null;
  durationDays: number | null;
}

/** A run below the reference high. */
export interface SecurityPositionUnderwaterRun {
  from: string;
  to: string;
  durationDays: number;
}

/** The constant names are the text keys. */
export type SecurityPositionSeriesRemark =
  'NO_HOLDING' | 'NOT_VALUED_DAYS' | 'MISSING_FX' | 'NEGATIVE_UNITS_DAYS' | 'INTERNAL_TRANSFER';
