import type { Cashaccount } from '../cashaccount';
import type { DisposalCostDetail } from './disposal.cost.detail';
import type { LastpriceOrigin } from '../types/lastprice.origin';

/** Cash balance, holdings and currency results returned for one cash account. */
export interface AccountPositionSummary {
  closePrice: number | null;
  closeDate: string | null;
  closePriceOrigin: LastpriceOrigin;
  /** True when no exchange rate was available, so this account is excluded from every main currency total. */
  priceMissing: boolean;
  cashBalance: number;
  cashBalanceMC: number;
  balanceCurrencyTransaction: number;
  balanceCurrencyTransactionMC: number;
  externalCashTransferMC: number;
  cashTransferMC: number;
  cashAccountTransactionFeeMC: number;
  accountFeesMC: number;
  accountInterestMC: number;
  valueSecurities: number;
  valueSecuritiesMC: number;
  gainLossSecurities: number;
  gainLossSecuritiesMC: number;
  valueMC: number;
  gainLossCurrencyMC: number;
  excludedDivTaxMC: number;
  cashaccount: Cashaccount;
  hasTransaction: boolean;
  /** Disposal cost estimate of the account and its securities, only present when it is switched on. */
  disposalCostMC?: number;
  valueAfterDisposalMC?: number;
  disposalComplete?: boolean;
  /** Matched rule or reason of an unknown markup for the conversion into the main currency. */
  disposalDetails?: DisposalCostDetail[];
}
