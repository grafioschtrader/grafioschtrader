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
  /** Composition mismatch of security exposures, excluding cash; null without positive targets. */
  overallAllocationMismatchPercentage: number | null;
  exposureBreach: boolean;
  valuationDate: string;
  /** The valuation day is a periodic checkpoint, so deviations beyond the tolerance are traded. */
  periodicDue: boolean;
  /** Last checkpoint of the monitored hierarchy; null when none is remembered, which makes every day a checkpoint. */
  lastCheckpointDate: string | null;
  /** First valuation day on which the next checkpoint is due; null without a last checkpoint. */
  nextCheckpointDate: string | null;

  grandAccountValueSecurityMC: number;
  currency: string;

  grandGainLossSecurityMC: number;
  grandGainLossCurrencyMC: number;
  grandTaxCostMC: number;
  grandTransactionCostMC: number;
  securityPositionGroupSummaryList: SecurityPositionGroupSummary[];
}
