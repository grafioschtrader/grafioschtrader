import { AccountPositionSummary } from './account.position.summary';
import { MissingExchangeRate } from './missing.exchange.rate';

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
  groupExcludedDivTaxMC: number;
  excludeDivTax: boolean;
  groupName: string;
  currency: string;
  /** While this is not empty every group total above excludes the listed currencies and is therefore incomplete. */
  missingExchangeRates: MissingExchangeRate[];
  accountPositionSummaryList: AccountPositionSummary[];
}
