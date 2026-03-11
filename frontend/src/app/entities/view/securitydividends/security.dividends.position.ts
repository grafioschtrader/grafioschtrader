import {Security} from '../../security';
import {Cashaccount} from '../../cashaccount';

export interface SecurityDividendsPosition extends AccountDividendPosition {
  countPaidTransactions: number;
  security: Security;
  unitsAtEndOfYear: number;
  financeCostMC: number;
  ictaxTaxValuePerUnitChf?: number;
  ictaxTotalTaxValueChf?: number;
  ictaxTotalPaymentValueChf?: number;
  excludedFromTax?: boolean;
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

export interface CashAccountPosition extends AccountDividendPosition {
  cashaccount: Cashaccount;
  marginEarningsMC: number;
  hypotheticalFinanceCostMC: number;
  cashBalancePlusMarginMC: number;
}
