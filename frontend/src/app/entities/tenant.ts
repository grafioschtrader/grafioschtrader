import { Portfolio } from './portfolio';
import { TenantBase } from '../lib/entities/tenant.base';

export class Tenant extends TenantBase {
  currency: string = null;
  excludeDivTax: boolean = null;
  closedUntil: string = null;
  simulationStartDate: string = null;
  simulationInitializationMode: string = null;
  country: string = null;
  portfolioList: Portfolio[];
  tenantKindType: TenantKindType | string;
  idWatchlistPerformance: number = null;
  /** Monitoring hierarchy for a main tenant, shared replay strategy for a simulation tenant. */
  idAlgoTop: number = null;
  /** Opt-in of this tenant to the GT authored import templates; which platform carries them is set instance wide. */
  useGtImportTemplates = false;

  /** Convert account costs and account interest with the rate of the cut-off date instead of the booking day. */
  feeInterestFxAtCutOffDate = false;

  public override getId(): number {
    return this.idTenant;
  }
}

export enum TenantKindType {
  MAIN = 0,
  SIMULATION = 1
}
