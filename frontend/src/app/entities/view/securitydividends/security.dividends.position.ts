import { Security } from '../../security';
import { Cashaccount } from '../../cashaccount';

export interface SecurityDividendsPosition extends AccountDividendPosition {
  countPaidTransactions: number;
  security: Security;
  unitsAtEndOfYear: number;
  positionOpenedInYear?: boolean;
  redeemedAtMaturityInYear?: boolean;
  zeroUnitsAtStartAndEndOfYear?: boolean;
  financeCostMC: number;
  ictaxTaxValuePerUnitChf?: number;
  ictaxTotalTaxValueChf?: number;
  ictaxTotalPaymentValueChf?: number;
  excludedFromTax?: boolean;
  /** TAXABLE_AMOUNT | DIRECT_VALUE | COMMENT_ONLY; absent when no correction record exists for this year */
  taxYearCorrectionType?: string;
  /** True when a correction record exists for this security in this or the previous tax year */
  taxYearCorrectionNearby?: boolean;
  /** Note of the correction record applying to this tax year */
  taxYearCorrectionNote?: string;
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
