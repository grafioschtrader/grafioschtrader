import {Security} from '../../security';
import {Cashaccount} from '../../cashaccount';

export interface SecurityDividendsPosition extends AccountDividendPosition {
  countPaidTransactions: number;
  security: Security;
  unitsAtEndOfYear: number;
}

export interface AccountDividendPosition {
  realReceivedDivInterestMC: number;
  autoPaidTax: number;
  autoPaidTaxMC: number;
  taxableAmount: number;
  taxableAmountMC: number;
  taxFreeIncome: number;
  valueAtEndOfYearMC: number;

  exchangeRateEndOfYear: number;

}

export interface CashAccountPosition {
  cashaccount: Cashaccount;

}
