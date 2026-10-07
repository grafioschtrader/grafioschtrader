import { Cashaccount } from '../cashaccount';
import { DisposalCostDetail } from './disposal.cost.detail';

export class AccountPositionSummary {
  closePrice: number;
  /** True when no exchange rate was available, so this account is excluded from every main currency total. */
  priceMissing: boolean;
  balanceCalculated: number;
  balanceCurrencyTransaction: number;
  tragetCurrencyTransaction: number;
  externalCashTransferMC: number;
  accountFeesMainCurrency: number;
  accountInterestMainCurrency: number;
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
