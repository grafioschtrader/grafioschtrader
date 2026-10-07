import { Security } from '../security';
import { LastpriceOrigin } from '../types/lastprice.origin';
import { DisposalCostDetail } from './disposal.cost.detail';

export class SecurityPositionSummary {
  /** Target-only row without a portfolio position or transaction history in this report. */
  public comparisonOnly?: boolean;
  /**
   * Allocation comparison against the selected strategy. Only the rebalancing report fills these; every other report
   * leaves them undefined and simply does not show the corresponding columns.
   */
  public targetPercentage: number;
  public parentDeviation: number;
  public securityDeviationPercentage: number;
  public actualPercentage: number;
  public deviationPercentage: number;
  public recommendedAction: string;
  public recommendedAmount: number;
  public recommendedUnits: number;
  /** Locale independent reason token; translated for display like any other enum valued column. */
  public recommendationReason: string;

  public mainCurrency: string;
  public units: number;
  public splitFactorFromBaseTransaction: number;
  public transactionCost: number;
  public transactionCostMC: number;
  public taxCost: number;
  public taxCostMC: number;
  public gainLossSecurity: number;
  public gainLossSecurityMC: number;
  /** Share of the result caused by exchange rate movement; together with gainLossSecurityMC the total in main currency. */
  public gainLossCurrencyMC: number;
  public positionGainLoss: number;
  public positionGainLossPercentage: number;
  public valueSecurity: number;
  public valueSecurityMC: number;
  /** Share of the account value in the report total; negative for a loss making margin position, null on total <= 0. */
  public shareOfTotalPercentage: number;

  /**
   * Disposal cost estimate of the hypothetical sale, only present when gt.disposal.cost.estimate is switched on and the
   * position was estimated. Amounts without MC are in the currency of the security.
   */
  public disposalTransactionCost?: number;
  public disposalTaxCost?: number;
  public disposalFxCost?: number;
  public disposalCostMC?: number;
  public valueAfterDisposalMC?: number;
  /** False when a fee model, a tax model or a matching rule was missing for a part of the estimate. */
  public disposalComplete?: boolean;
  public disposalDetails?: DisposalCostDetail[];

  /**
   * Where the close price of this position comes from. An instrument without intraday data is valued with its newest
   * historical closing price, which may itself have been produced by filling gaps rather than traded.
   */
  public closePriceOrigin: LastpriceOrigin;

  /**
   * As getter defined
   */
  security: Security;
}
