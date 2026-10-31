import type { SecurityPositionGroupSummary } from './security.position.group.summary';

/** Security-report totals, including nullable metrics of the rebalancing comparison. */
export interface SecurityPositionGrandSummary<S extends SecurityPositionGroupSummary = SecurityPositionGroupSummary> {
  /**
   * Figures of the rebalancing comparison describing the book as a whole. Net equity, the cash actually held and
   * gross exposure answer different questions and are deliberately not one total.
   */
  grandNetEquityMC: number | null;
  grandActualCashMC: number | null;
  grandGrossExposureMC: number | null;
  grandInvestmentBudgetMC: number | null;
  grandUnusedTacticalBudgetMC: number | null;
  toleranceThreshold: number | null;
  /** Composition mismatch of security exposures, excluding cash; null without positive targets. */
  overallAllocationMismatchPercentage: number | null;
  /** AlgoTop ceiling in percentage points of net equity. */
  topTargetPercentage: number | null;
  /** Actual gross exposure in percentage points of net equity; null when net equity is zero. */
  topActualPercentage: number | null;
  /** Actual minus ceiling; positive means the gross exposure is above the ceiling. */
  topDeviationPercentage: number | null;
  exposureBreach: boolean;
  valuationDate: string | null;
  /** The valuation day is a periodic checkpoint, so deviations beyond the tolerance are traded. */
  periodicDue: boolean | null;
  /** Last checkpoint of the monitored hierarchy; null when none is remembered, which makes every day a checkpoint. */
  lastCheckpointDate: string | null;
  /** First valuation day on which the next checkpoint is due; null without a last checkpoint. */
  nextCheckpointDate: string | null;

  grandAccountValueSecurityMC: number;
  /** 100, or null when the report total is not positive. */
  grandShareOfTotalPercentage: number | null;
  currency: string;

  grandGainLossSecurityMC: number;
  grandGainLossCurrencyMC: number;
  /** Disposal cost estimate of the report, only present when it is switched on. */
  grandDisposalCostMC?: number;
  grandValueAfterDisposalMC?: number;
  grandDisposalComplete?: boolean;
  grandTaxCostMC: number;
  securityPositionGroupSummaryList: S[];
}
