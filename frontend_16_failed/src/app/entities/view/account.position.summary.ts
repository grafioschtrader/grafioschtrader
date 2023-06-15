import {Cashaccount} from '../cashaccount';

export class AccountPositionSummary {

  closePrice: number;
  balanceCalculated: number;
  balanceCurrencyTransaction: number;
  tragetCurrencyTransaction: number;
  externalCashTransferMC: number;
  accountFeesMainCurrency: number;
  accountInterestMainCurrency: number;
  gainLossCurrencyMC: number;
  cashaccount: Cashaccount;
  hasTransaction: boolean;
}
