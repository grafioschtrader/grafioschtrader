import type { Security } from '../security';
import type { LastpriceOrigin } from '../types/lastprice.origin';
import type { DisposalCostDetail } from './disposal.cost.detail';

/** One security position returned by the securities reports. */
export interface SecurityPositionSummary {
  /** Target-only row without a portfolio position or transaction history in this report. */
  comparisonOnly?: boolean;
  /**
   * Allocation comparison against the selected strategy. Only the rebalancing report fills these; every other report
   * leaves them null and simply does not show the corresponding columns.
   */
  targetPercentage: number | null;
  parentDeviation: number | null;
  securityDeviationPercentage: number | null;
  actualPercentage: number | null;
  deviationPercentage: number | null;
  recommendedAction: string | null;
  recommendedAmount: number | null;
  recommendedUnits: number | null;
  /** Locale independent reason token; translated for display like any other enum valued column. */
  recommendationReason: string | null;

  mainCurrency: string;
  units: number;
  splitFactorFromBaseTransaction: number;
  transactionCost: number;
  transactionCostMC: number;
  taxCost: number;
  taxCostMC: number;
  gainLossSecurity: number;
  gainLossSecurityMC: number;
  /** Share of the result caused by exchange rate movement; together with gainLossSecurityMC the total in main currency. */
  gainLossCurrencyMC: number;
  valueSecurity: number;
  valueSecurityMC: number;
  /** Share of the account value in the report total; negative for a loss making margin position, null on total <= 0. */
  shareOfTotalPercentage: number | null;

  /**
   * Disposal cost estimate of the hypothetical sale, only present when gt.disposal.cost.estimate is switched on and the
   * position was estimated. Amounts without MC are in the currency of the security.
   */
  disposalTransactionCost?: number;
  disposalTaxCost?: number;
  disposalFxCost?: number;
  disposalCostMC?: number;
  valueAfterDisposalMC?: number;
  /** False when a fee model, a tax model or a matching rule was missing for a part of the estimate. */
  disposalComplete?: boolean;
  disposalDetails?: DisposalCostDetail[];

  /**
   * Where the close price of this position comes from. An instrument without intraday data is valued with its newest
   * historical closing price, which may itself have been produced by filling gaps rather than traded.
   */
  closePriceOrigin: LastpriceOrigin;

  /**
   * As getter defined
   */
  security: Security;
}
