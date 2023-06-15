import {SecurityPositionGroupSummary} from './security.position.group.summary';

export class SecurityPositionGrandSummary {

  grandAccountValueSecurityMC: number;
  currency: string;


  grandGainLossSecurityMC: number;
  grandTaxCostMC: number;
  grandTransactionCostMC: number;
  securityPositionGroupSummaryList: SecurityPositionGroupSummary[];

}
