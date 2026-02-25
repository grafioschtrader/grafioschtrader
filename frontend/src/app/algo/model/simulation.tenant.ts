/**
 * Response DTO for listing simulation tenants.
 */
export interface SimulationTenantInfo {
  idTenant: number;
  tenantName: string;
  idAlgoTop: number;
  algoTopName: string;
  hasTransactions: boolean;
}

/**
 * Request DTO for creating a simulation tenant from an AlgoTop strategy.
 */
export interface SimulationTenantCreateDTO {
  idAlgoTop: number;
  tenantName: string;
  copyTransactions: boolean;
  cashBalances?: { [idCashAccount: number]: number };
}
