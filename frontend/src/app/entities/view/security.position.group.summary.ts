import { SecurityPositionSummary } from './security.position.summary';

export class SecurityPositionGroupSummary {
  public groupAccountValueSecurityMC: number;
  public groupGainLossSecurityMC: number;
  public groupGainLossCurrencyMC: number;
  public securityPositionSummaryList: SecurityPositionSummary[];
}
