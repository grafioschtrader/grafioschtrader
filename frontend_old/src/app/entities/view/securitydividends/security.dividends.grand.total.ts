import {SecurityDividendsYearGroup} from './security.dividends.year.group';
import {Portfolio} from '../../portfolio';

export class SecurityDividendsGrandTotal {
  public mainCurrency: string;
  public securityDividendsYearGroup: SecurityDividendsYearGroup[];
  public portfolioList: Portfolio[];
  public numberOfSecurityAccounts: number;
  public numberOfCashAccounts: number;
}
