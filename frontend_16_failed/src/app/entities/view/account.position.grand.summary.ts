import {AccountPositionGroupSummary} from './account.position.group.summary';

export class AccountPositionGrandSummary {
  mainCurrency: string;
  grandBalance: number;
  grandValueMC: number;
  grandBalanceMainCurrency: number;
  grandExternalCashTransferMC: number;
  grandValueSecuritiesMC: number;
  grandGainLossSecuritiesMC: number;
  grandGainLossCurrencyMC: number;
  grandAccountFeesMC: number;
  grandAccountInterestMC: number;

  accountPositionGroupSummaryList: AccountPositionGroupSummary[];
}
