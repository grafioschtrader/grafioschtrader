import {ITransactionEditType} from './i.transaction.edit.type';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';
import {Cashaccount} from '../../entities/cashaccount';
import {Securityaccount} from '../../entities/securityaccount';
import {TransactionCallParam} from './transaction.call.parm';

/**
 * Transaction to add some units to a security account.
 */
export class TransactionSecurityEditAccumulate implements ITransactionEditType {

  constructor(private transactionCallParam: TransactionCallParam) {
  }

  calcPosTotal(quotation: number, units: number, taxCost: number, transactionCost: number, accruedInterest: number,
               valuePerPoint: number): number {
    return (quotation * units * valuePerPoint + taxCost + transactionCost + accruedInterest) * -1;
  }

  securityOnlyParentSelected(): boolean {
    return false;
  }

  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[], isSellBuyMarginInstrument: boolean): boolean {
    if (securitycashaccount.hasOwnProperty('currency')) {
      // it is a Cashaccount
      return true;
    } else {
      // it is a Securityaccount
      return this.transactionCallParam.securityaccount === null
        || this.transactionCallParam.securityaccount.idSecuritycashAccount === securitycashaccount.idSecuritycashAccount;
    }
  }
}
