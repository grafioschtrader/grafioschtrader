import {SecurityDividendsYearGroup} from './security.dividends.year.group';
import {Portfolio} from '../../portfolio';
import {TaxStatementExportRequest} from '../../../taxdata/service/tax-data.service';

export class SecurityDividendsGrandTotal {
  public mainCurrency: string;
  public securityDividendsYearGroup: SecurityDividendsYearGroup[];
  public portfolioList: Portfolio[];
  public numberOfSecurityAccounts: number;
  public numberOfCashAccounts: number;
  public grandFinanceCostMC?: number;
  public hasIctaxData?: boolean;
  public hasMarginData?: boolean;
  public tenantCountry?: string;
  public taxExportSettings?: TaxStatementExportRequest;
  public availableTaxYears?: number[];
}
