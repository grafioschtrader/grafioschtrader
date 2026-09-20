/**
 * Response DTO for listing simulation tenants.
 */
export interface SimulationTenantInfo {
  idTenant: number;
  tenantName: string;
  idAlgoTop: number;
  algoTopName: string;
  hasTransactions: boolean;
  simulationStartDate?: string;
  initializationMode?: SimulationInitializationMode;
  requiresRecreation: boolean;
  /** True while a replay is queued or executing; the environment can then neither be entered nor deleted. */
  active: boolean;
}

/**
 * Request DTO for creating a simulation tenant from an AlgoTop strategy.
 */
export interface SimulationTenantCreateDTO {
  idAlgoTop: number;
  tenantName: string;
  simulationStartDate: string;
  initializationMode: SimulationInitializationMode;
  liquidationAssignments?: { [positionKey: string]: number };
  cashBalances?: { [idCashAccount: number]: number };
}

/**
 * Opening ledger policy shared with the backend enum.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/SimulationInitializationMode.java
 */
export enum SimulationInitializationMode {
  COPY_PORTFOLIO = 'COPY_PORTFOLIO',
  MANUAL_CASH = 'MANUAL_CASH',
  LIQUIDATE_TO_CASH = 'LIQUIDATE_TO_CASH'
}

/**
 * What limits the opening date of an environment. All of it is informational: an earlier opening date stays allowed,
 * because opening with cash alone before any price data is a legitimate starting point.
 */
export interface SimulationDateBounds {
  universeFromDate?: string;
  instrumentsWithoutHistory: string[];
  firstTransactionDate?: string;
  unpricedAtOpeningDate: string[];
}

/** Read-only valuation response; creation repeats the server calculation. */
export interface SimulationPreviewDto {
  accounts: { idCashaccount: number; name: string; currency: string; balance: number }[];
  unresolvedPositions: {
    positionKey: string;
    securityName: string;
    securityaccountName: string;
    currency: string;
    units: number;
    proceeds: number;
  }[];
  errors: string[];
}
