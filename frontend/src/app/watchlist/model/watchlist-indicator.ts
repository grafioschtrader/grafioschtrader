import { SecuritycurrencyGroup } from '../../entities/view/securitycurrency.group';

/** Stored definition, with the result type inferred by the server. */
export class WatchlistIndicatorColumn {
  idWatchlistIndicatorColumn: number = null;
  idWatchlist: number = null;
  label: string = null;
  expression: string = null;
  resultType: 'NUMBER' | 'BOOLEAN' = 'NUMBER';
  idBenchmark: number = null;
  decimals = 2;
  lowerMark: number = null;
  upperMark: number = null;
  markColors = 'LOW_NEGATIVE_HIGH_POSITIVE';
  uiOrder = 0;
}
export interface IndicatorCell {
  value: number | boolean | null;
  status: string;
  lastCommonDate?: string;
}
export interface SecuritycurrencyIndicatorGroup extends SecuritycurrencyGroup {
  columns: WatchlistIndicatorColumn[];
  cells: Record<string, Record<string, IndicatorCell>>;
  benchmarks: Record<string, { name: string; currency: string }>;
  /** Per column the fundamental variables its expression reads. */
  columnFundamentalVariables: Record<string, string[]>;
  /** Per instrument and fundamental variable the origin of the bound value. */
  fundamentalInfo: Record<string, Record<string, FundamentalInfo>>;
  tenantHasColumns: boolean;
}
/** Origin of a bound fundamental value, shown in the cell tooltip of a column that reads it. */
export interface FundamentalInfo {
  source: string;
  /** TTM for trailing twelve months, FY for the last fiscal year, null for a value without basis. */
  basis: 'TTM' | 'FY' | null;
  periodEnd: string | null;
  state: 'CONFIRMED' | 'CHECKED' | 'SINGLE';
  fetchedDate: string | null;
  /** Exchange rate into the currency of the security, null when the value needed no conversion. */
  rate: number | null;
  /** Currency of the stored value, set only when it was converted. */
  currency: string | null;
}
