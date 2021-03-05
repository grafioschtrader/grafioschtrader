import {AccountPositionSummary} from './account.position.summary';

export class AccountPositionGroupSummary {
  groupBalance: number;

  groupAccountFeesMC: number;
  groupAccountInterestMC: number;

  groupExternalCashTransferMC: number;
  groupValueMC: number;
  groupCashBalanceMC: number;
  groupValueSecuritiesMC: number;
  groupGainLossSecuritiesMC: number;
  groupGainLossCurrencyMC: number;
  groupName: string;
  currency: string;
  accountPositionSummaryList: AccountPositionSummary[];
}
