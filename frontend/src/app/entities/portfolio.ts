import { Cashaccount } from './cashaccount';
import { Securityaccount } from './securityaccount';
import { Securitycashaccount } from './securitycashaccount';
import { TenantBaseId } from '../lib/entities/tenant.base.id';

export class Portfolio extends TenantBaseId {
  name: string | null = null;
  currency: string | null = null;
  closedUntil: string | null = null;
  idPortfolio?: number;

  securitycashaccountList?: Securitycashaccount;

  securityaccountList?: Securityaccount[];
  cashaccountList?: Cashaccount[];

  public getId(): number | undefined {
    return this.idPortfolio;
  }
}
