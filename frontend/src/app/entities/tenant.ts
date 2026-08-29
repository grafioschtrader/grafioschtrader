import { Portfolio } from './portfolio';
import { TenantBase } from '../lib/entities/tenant.base';

export class Tenant extends TenantBase {
  currency: string = null;
  excludeDivTax: boolean = null;
  closedUntil: string = null;
  country: string = null;
  portfolioList: Portfolio[];
  tenantKindType: TenantKindType | string;
  idWatchlistPerformance: number = null;
  /** Opt-in of this tenant to the GT authored import templates; which platform carries them is set instance wide. */
  useGtImportTemplates = false;

  public override getId(): number {
    return this.idTenant;
  }
}

export enum TenantKindType {
  MAIN = 0,
  SIMULATION = 1
}
