import { Portfolio } from './portfolio';
import { TenantBase } from '../lib/entities/tenant.base';

export class Tenant extends TenantBase {
  currency: string | null = null;
  excludeDivTax: boolean | null = null;
  closedUntil: string | null = null;
  simulationStartDate: string | null = null;
  simulationInitializationMode: string | null = null;
  country: string | null = null;
  portfolioList: Portfolio[];
  tenantKindType: TenantKindType | string;
  idWatchlistPerformance: number | null = null;
  /** Monitoring hierarchy for a main tenant, shared replay strategy for a simulation tenant. */
  idAlgoTop: number | null = null;
  /** Opt-in of this tenant to the GT authored import templates; which platform carries them is set instance wide. */
  useGtImportTemplates = false;

  /** Convert account costs and account interest with the rate of the cut-off date instead of the booking day. */
  feeInterestFxAtCutOffDate = false;

  /** Keeps the disposal cost estimate on for this tenant; effective only while the instance switch is on too. */
  disposalCostEstimate = true;

  public override getId(): number {
    return this.idTenant;
  }
}

export enum TenantKindType {
  MAIN = 0,
  SIMULATION = 1
}
