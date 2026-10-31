import type { SecurityDividendsYearGroup } from './security.dividends.year.group';
import type { Portfolio } from '../../portfolio';
import type { TaxStatementExportRequest } from '../../../taxdata/service/tax-data.service';

export interface SecurityDividendsGrandTotal {
  mainCurrency: string;
  securityDividendsYearGroup: SecurityDividendsYearGroup[];
  portfolioList: Portfolio[];
  numberOfSecurityAccounts: number;
  numberOfCashAccounts: number;
  grandFinanceCostMC?: number;
  hasIctaxData?: boolean;
  hasMarginData?: boolean;
  tenantCountry?: string;
  taxExportSettings?: TaxStatementExportRequest;
  availableTaxYears?: number[];
}
