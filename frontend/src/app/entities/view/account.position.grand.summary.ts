import type { AccountPositionGroupSummary } from './account.position.group.summary';
import type { MissingExchangeRate } from './missing.exchange.rate';

/** Portfolio totals returned by the cash-account report. */
export interface AccountPositionGrandSummary {
  mainCurrency: string;
  grandCashBalanceMC: number;
  grandValueMC: number;
  grandCashTransferMC: number;
  grandCashAccountTransactionFeeMC: number;
  grandExternalCashTransferMC: number;
  grandValueSecuritiesMC: number;
  grandGainLossSecuritiesMC: number;
  grandGainLossCurrencyMC: number;
  grandAccountFeesMC: number;
  grandAccountInterestMC: number;
  grandExcludedDivTaxMC: number;
  grandDisposalCostMC?: number;
  grandValueAfterDisposalMC?: number;
  grandDisposalComplete?: boolean;

  /** While this is not empty every grand total above excludes the listed currencies and is therefore incomplete. */
  missingExchangeRates: MissingExchangeRate[];

  accountPositionGroupSummaryList: AccountPositionGroupSummary[];
}
