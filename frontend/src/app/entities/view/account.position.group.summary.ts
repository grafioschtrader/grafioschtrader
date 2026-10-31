import type { AccountPositionSummary } from './account.position.summary';
import type { MissingExchangeRate } from './missing.exchange.rate';

/** Cash-account totals and positions for one portfolio or currency group. */
export interface AccountPositionGroupSummary {
  groupAccountFeesMC: number;
  groupAccountInterestMC: number;

  groupExternalCashTransferMC: number;
  groupCashTransferMC: number;
  groupCashAccountTransactionFeeMC: number;
  groupValueMC: number;
  groupCashBalanceMC: number;
  groupValueSecuritiesMC: number;
  groupGainLossSecuritiesMC: number;
  groupGainLossCurrencyMC: number;
  groupExcludedDivTaxMC: number;
  groupDisposalCostMC?: number;
  groupValueAfterDisposalMC?: number;
  groupDisposalComplete?: boolean;
  excludeDivTax: boolean;
  groupName: string;
  currency: string;
  /** While this is not empty every group total above excludes the listed currencies and is therefore incomplete. */
  missingExchangeRates: MissingExchangeRate[];
  accountPositionSummaryList: AccountPositionSummary[];
}
