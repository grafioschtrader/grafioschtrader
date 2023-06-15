import {Portfolio} from './portfolio';

export class Tenant {
  idTenant: number;
  tenantName: string = null;
  currency: string = null;
  excludeDivTax: boolean = null;
  portfolioList: Portfolio[];
  tenantKindType: TenantKindType | string;
  createIdUser: number;
  idWatchlistPerformance: number = null;
}

export enum TenantKindType {
  MAIN = 0,
  SIMULATION = 1,
}
