import { SecurityPositionGroupSummary } from './security.position.group.summary';

export class SecurityPositionGrandSummary {
  /**
   * Figures of the rebalancing comparison describing the book as a whole. Net equity, the cash actually held and
   * gross exposure answer different questions and are deliberately not one total.
   */
  grandNetEquityMC: number;
  grandActualCashMC: number;
  grandGrossExposureMC: number;
  grandInvestmentBudgetMC: number;
  grandUnusedTacticalBudgetMC: number;
  toleranceThreshold: number;
  exposureBreach: boolean;
  valuationDate: string;

  grandAccountValueSecurityMC: number;
  currency: string;

  grandGainLossSecurityMC: number;
  grandGainLossCurrencyMC: number;
  grandTaxCostMC: number;
  grandTransactionCostMC: number;
  securityPositionGroupSummaryList: SecurityPositionGroupSummary[];
}
