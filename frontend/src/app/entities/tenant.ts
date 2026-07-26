import {Portfolio} from './portfolio';
import {TenantBase} from '../lib/entities/tenant.base';


export class Tenant extends TenantBase {
  currency: string = null;
  excludeDivTax: boolean = null;
  closedUntil: string = null;
  country: string = null;
  portfolioList: Portfolio[];
  tenantKindType: TenantKindType | string;
  idWatchlistPerformance: number = null;
  /** Reference to the tenant's Grafioschtrader import platform holding the GT authored import templates. */
  idGtImportPlatform: number = null;

  public override getId(): number {
    return this.idTenant;
  }
}

export enum TenantKindType {
  MAIN = 0,
  SIMULATION = 1,
}

