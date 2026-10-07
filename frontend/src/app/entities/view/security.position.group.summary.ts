import { SecurityPositionSummary } from './security.position.summary';

export class SecurityPositionGroupSummary {
  /** Allocation comparison of the whole group; only set on the rebalancing report. */
  public groupTargetPercentage: number;
  public groupParentDeviation: number;
  public groupSecurityDeviationPercentage: number;
  public groupMaxTradedSecuritiesPerAssetclass: number;
  public groupRequestedAdjustment: number;
  public groupResidual: number;
  public groupActualPercentage: number;
  public groupDeviationPercentage: number;
  public groupRecommendedAction: string;
  public groupRecommendedAmount: number;
  public groupRecommendationReason: string;

  public groupAccountValueSecurityMC: number;
  /** Share of the group in the report total; null when the total is not positive. */
  public groupShareOfTotalPercentage: number;
  public groupGainLossSecurityMC: number;
  public groupGainLossCurrencyMC: number;
  /** Disposal cost estimate of the group, only present when it is switched on. */
  public groupDisposalCostMC?: number;
  public groupValueAfterDisposalMC?: number;
  public groupDisposalComplete?: boolean;
  public securityPositionSummaryList: SecurityPositionSummary[];
}
