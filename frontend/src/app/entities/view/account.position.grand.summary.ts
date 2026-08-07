import {AccountPositionGroupSummary} from './account.position.group.summary';
import {MissingExchangeRate} from './missing.exchange.rate';

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
  grandExcludedDivTaxMC: number;

  /** While this is not empty every grand total above excludes the listed currencies and is therefore incomplete. */
  missingExchangeRates: MissingExchangeRate[];

  accountPositionGroupSummaryList: AccountPositionGroupSummary[];
}
