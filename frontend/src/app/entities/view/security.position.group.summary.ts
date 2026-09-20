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
  public groupGainLossSecurityMC: number;
  public groupGainLossCurrencyMC: number;
  public securityPositionSummaryList: SecurityPositionSummary[];
}
