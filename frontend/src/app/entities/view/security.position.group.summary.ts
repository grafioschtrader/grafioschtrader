import type { SecurityPositionSummary } from './security.position.summary';

/** Shared totals of a security-position group, including nullable rebalancing metrics. */
export interface SecurityPositionGroupSummary {
  /** Allocation comparison of the whole group; only set on the rebalancing report. */
  groupTargetPercentage: number | null;
  groupParentDeviation: number | null;
  groupSecurityDeviationPercentage: number | null;
  groupMaxTradedSecuritiesPerAssetclass: number | null;
  groupRequestedAdjustment: number | null;
  groupResidual: number | null;
  groupActualPercentage: number | null;
  groupDeviationPercentage: number | null;
  groupRecommendedAction: string | null;
  groupRecommendedAmount: number | null;
  groupRecommendationReason: string | null;

  groupAccountValueSecurityMC: number;
  /** Share of the group in the report total; null when the total is not positive. */
  groupShareOfTotalPercentage: number | null;
  groupGainLossSecurityMC: number;
  groupGainLossCurrencyMC: number;
  /** Disposal cost estimate of the group, only present when it is switched on. */
  groupDisposalCostMC?: number;
  groupValueAfterDisposalMC?: number;
  groupDisposalComplete?: boolean;
  securityPositionSummaryList: SecurityPositionSummary[];
}
