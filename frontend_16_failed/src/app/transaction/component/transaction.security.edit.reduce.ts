import {ITransactionEditType} from './i.transaction.edit.type';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';
import {TransactionCallParam} from './transaction.call.parm';

/**
 * Transaction to reduce the holdings, paying dividends or interest.
 */
export class TransactionSecurityEditDividendReduce implements ITransactionEditType {

  constructor(private transactionCallParam: TransactionCallParam) {
  }

  calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint - taxCost - transactionCost + accruedInterest);
  }

  /**
   * Security can not be changed when reducing a position
   */
  securityOnlyParentSelected(): boolean {
    return true;
  }

  /**
   * For reduce only security account with holdings is accepted.
   */
  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[],
                        isSellBuyMarginInstrument: boolean): boolean {
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

export class TransactionSecurityEditFinanceCost extends TransactionSecurityEditDividendReduce
  implements ITransactionEditType {
  calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint - taxCost - transactionCost + accruedInterest) * -1;
  }

}
