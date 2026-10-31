import type { CashAccountPosition, SecurityDividendsPosition } from './security.dividends.position';

/**
 * Group total per year
 */
export interface SecurityDividendsYearGroup {
  year: number;
  securityDividendsPositions: SecurityDividendsPosition[];
  cashAccountPositions: CashAccountPosition[];
  yearFinanceCostMC?: number;
  yearIctaxTotalTaxValueChf?: number;
  yearIctaxTotalPaymentValueChf?: number;
}
