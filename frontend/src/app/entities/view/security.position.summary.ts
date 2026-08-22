import { Security } from '../security';

export class SecurityPositionSummary {
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
   * As getter defined
   */
  security: Security;
}
