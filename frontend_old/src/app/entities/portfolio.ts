import {Cashaccount} from './cashaccount';
import {Securityaccount} from './securityaccount';
import {Securitycashaccount} from './securitycashaccount';
import {TenantBaseId} from './tenant.base.id';

export class Portfolio extends TenantBaseId {

  name: string = null;
  currency: string = null;
  idPortfolio?: number;

  securitycashaccountList?: Securitycashaccount;

  securityaccountList?: Securityaccount[];
  cashaccountList?: Cashaccount [];

  public getId(): number {
    return this.idPortfolio;
  }
}




