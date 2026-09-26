import { Security } from '../security';
import { LastpriceOrigin } from '../types/lastprice.origin';

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
