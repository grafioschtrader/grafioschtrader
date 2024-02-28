import {CashAccountPosition, SecurityDividendsPosition} from './security.dividends.position';

/**
 * Group total per year
 */
export class SecurityDividendsYearGroup {
  public year: number;
  public securityDividendsPositions: SecurityDividendsPosition[];
  public cashAccountPositions: CashAccountPosition[];
}
