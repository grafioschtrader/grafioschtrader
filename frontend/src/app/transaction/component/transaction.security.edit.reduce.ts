import {ITransactionEditType} from './i.transaction.edit.type';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';
import {TransactionCallParam} from './transaction.call.parm';

/**
 * Handles transaction operations for reducing security holdings, processing dividend payments, and interest calculations.
 * This class implements the business logic for sell transactions, dividend distributions, and interest payments on
 * securities. It ensures that only security accounts with existing positions can be used for reduction operations and
 * calculates the total transaction value including taxes, costs, and accrued interest.
 */
export class TransactionSecurityEditDividendReduce implements ITransactionEditType {

  /**
   * Creates a new dividend/reduce transaction handler.
   * @param transactionCallParam Configuration parameters containing transaction context and security details
   */
  constructor(private transactionCallParam: TransactionCallParam) {
  }

  /**
   * Calculates the total position value for dividend or security reduction transactions.
   * @param quotation The price per unit of the security
   * @param units Number of units being processed
   * @param taxCost Tax amount applied to the transaction
   * @param transactionCost Trading fees and transaction costs
   * @param accruedInterest Interest accumulated on the position
   * @param valuePerPoint Value multiplier per point for margin instruments
   * @returns The calculated total transaction amount
   */
  calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint - taxCost - transactionCost + accruedInterest);
  }

  /** Security can not be changed when reducing a position */
  securityOnlyParentSelected(): boolean {
    return true;
  }

  /**
   * Determines if a security account is acceptable for reduction transactions.
   * For reduce operations, only security accounts with existing holdings are accepted.
   * @param securitycashaccount The security or cash account to validate
   * @param securityaccountOpenPositionUnits List of accounts with open positions
   * @param isSellBuyMarginInstrument Whether this is a margin instrument transaction
   * @param closeMarginIdSecurityaccount ID of the security account for closing margin positions
   * @returns True if the account can be used for reduction transactions
   */
  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[],
                        isSellBuyMarginInstrument: boolean, closeMarginIdSecurityaccount: number): boolean {
    if (securitycashaccount.hasOwnProperty('currency')) {
      // it is a cash account
      return true;
    } else {
      if (isSellBuyMarginInstrument) {
        return this.transactionCallParam.securityaccount === null
          || this.transactionCallParam.securityaccount.idSecuritycashAccount === securitycashaccount.idSecuritycashAccount;
      } else {
        const founds: SecurityaccountOpenPositionUnits[] = securityaccountOpenPositionUnits
          .filter(pos => securitycashaccount.idSecuritycashAccount === pos.idSecurityaccount);
        return founds.length === 1 && (this.transactionCallParam.securityaccount === null
          || this.transactionCallParam.securityaccount.idSecuritycashAccount === securitycashaccount.idSecuritycashAccount);
      }
    }
  }
}

/**
 * Handles finance cost transactions for margin instruments. Extends the dividend/reduce transaction handler to
 * provide specialized calculation logic for financing costs on leveraged positions. The main difference is that
 * finance costs result in negative cash flow, representing the cost of maintaining leveraged positions.
 */
export class TransactionSecurityEditFinanceCost extends TransactionSecurityEditDividendReduce
  implements ITransactionEditType {

  /**
   * Calculates the total position value for finance cost transactions on margin instruments.
   * @param quotation The financing rate or cost per unit
   * @param units Number of days or units being charged
   * @param taxCost Tax amount applied to the finance cost
   * @param transactionCost Additional fees associated with the finance cost
   * @param accruedInterest Interest component of the finance cost
   * @param valuePerPoint Value multiplier per point for margin calculations
   * @returns The calculated finance cost amount (negative value representing cost)
   */
  override calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint - taxCost - transactionCost + accruedInterest) * -1;
  }

}
