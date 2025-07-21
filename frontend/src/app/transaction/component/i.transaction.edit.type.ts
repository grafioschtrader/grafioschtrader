import {Securityaccount} from '../../entities/securityaccount';
import {Cashaccount} from '../../entities/cashaccount';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';

/**
 * Interface defining transaction editing behaviors for different transaction types. Provides methods for calculating
 * transaction totals, determining security selection constraints, and validating security account acceptance based
 * on transaction type and current holdings. Implementations handle specific transaction types like accumulate,
 * reduce, dividend, and finance cost transactions with their respective business rules and calculations.
 */
export interface ITransactionEditType {
  /**
   * Calculates the total amount and/or security risk of the transaction based on transaction parameters.
   * @param quotation The price per unit of the security
   * @param units The number of units being transacted
   * @param taxcost The tax cost associated with the transaction
   * @param transactioncost The transaction cost/fees
   * @param accruedInterest The accrued interest amount
   * @param valuePerPoint The value per point for margin instruments
   * @returns The calculated total amount (positive for credits, negative for debits)
   */
  calcPosTotal(quotation: number, units: number, taxcost: number, transactioncost: number,
               accruedInterest: number, valuePerPoint: number): number;

  /** Returns true if the user cannot change the security selection for this transaction type */
  securityOnlyParentSelected(): boolean;

  /**
   * Determines whether a security or cash account is acceptable for this transaction type.
   * @param securitycashaccount The security account or cash account to validate
   * @param securityaccountOpenPositionUnits Array of open position units for security accounts
   * @param isSellBuyMarginInstrument True if this is a margin instrument buy/sell transaction
   * @param closeMarginIdSecurityaccount The security account ID for closing margin positions
   * @returns True if the account is acceptable for this transaction type
   */
  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[],
                        isSellBuyMarginInstrument: boolean, closeMarginIdSecurityaccount: number): boolean;
}
