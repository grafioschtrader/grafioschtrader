import { Cashaccount } from '../cashaccount';

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
}
