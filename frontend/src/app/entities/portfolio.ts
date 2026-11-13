import { Cashaccount } from './cashaccount';
import { Securityaccount } from './securityaccount';
import { Securitycashaccount } from './securitycashaccount';
import { TenantBaseId } from '../lib/entities/tenant.base.id';

export class Portfolio extends TenantBaseId {
  name: string | null = null;
  currency: string | null = null;
  closedUntil: string | null = null;
  /** Name of the cost basis method overriding the one of the tenant; null follows the tenant. */
  costBasisMethod: string | null = null;
  /** Name of the withholding for the German tax help; null while the portfolio is not assigned. */
  taxWithholding: string | null = null;
  idPortfolio?: number;

  securitycashaccountList?: Securitycashaccount;

  securityaccountList?: Securityaccount[];
  cashaccountList?: Cashaccount[];

  public getId(): number | undefined {
    return this.idPortfolio;
  }
}
