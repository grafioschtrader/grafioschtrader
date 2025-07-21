import {ITransactionEditType} from './i.transaction.edit.type';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {TransactionCallParam} from './transaction.call.parm';

/**
 * Transaction implementation for accumulating (buying) units in a security account. This class handles the business
 * logic for purchase transactions, including position calculation, account validation, and security selection rules.
 * It implements the accumulation strategy where new units are added to existing positions.
 */
export class TransactionSecurityEditAccumulate implements ITransactionEditType {

  /**
   * Creates a new accumulation transaction handler.
   * @param transactionCallParam The transaction parameters containing security, account, and transaction context
   */
  constructor(private transactionCallParam: TransactionCallParam) {
  }

  /**
   * Calculates the total position value for an accumulation transaction. Returns negative value representing the cost
   * of purchasing the securities, including all fees and costs.
   * @param quotation The price per unit of the security
   * @param units The number of units being purchased
   * @param taxCost The tax cost associated with the transaction
   * @param transactionCost The transaction fee or commission
   * @param accruedInterest Any accrued interest on fixed income securities
   * @param valuePerPoint The value per point for margin instruments
   * @returns The total cost as a negative number (representing outflow of cash)
   */
  calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint + taxCost + transactionCost + accruedInterest) * -1;
  }

  /**
   * Determines if the security selection should be restricted to parent-selected securities only.
   * @returns false, indicating that any available security can be selected for accumulation transactions
   */
  securityOnlyParentSelected(): boolean {
    return false;
  }

  /**
   * Validates whether a given security or cash account is acceptable for this accumulation transaction.
   * Cash accounts are always accepted, while security accounts are validated based on transaction context.
   * @param securitycashaccount The security account or cash account to validate
   * @param securityaccountOpenPositionUnits Array of open position units for security accounts
   * @param isSellBuyMarginInstrument Whether this is a margin instrument buy/sell transaction
   * @param closeMarginIdSecurityaccount The security account ID for closing margin positions
   * @returns true if the account is acceptable for this transaction type
   */
  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[],
                        isSellBuyMarginInstrument: boolean, closeMarginIdSecurityaccount: number): boolean {
    if (securitycashaccount.hasOwnProperty('currency')) {
      // it is a Cashaccount
      return true;
    } else if(closeMarginIdSecurityaccount) {
      return securitycashaccount.idSecuritycashAccount === closeMarginIdSecurityaccount;
    } else {
      // it is a Securityaccount
      return this.transactionCallParam.securityaccount === null
        || this.transactionCallParam.securityaccount.idSecuritycashAccount === securitycashaccount.idSecuritycashAccount;
    }
  }
}
