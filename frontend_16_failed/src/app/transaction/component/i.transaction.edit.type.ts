import {Securityaccount} from '../../entities/securityaccount';
import {Cashaccount} from '../../entities/cashaccount';
import {SecurityaccountOpenPositionUnits} from '../../entities/view/securityaccount.open.position.units';

export interface ITransactionEditType {
  /**
   * Calc the total amount and/or security risk of the transaction.
   */
  calcPosTotal(quotation: number, units: number, taxcost: number, transactioncost: number,
               accruedInterest: number, valuePerPoint: number): number;

  /**
   * Returns if uses can change the security
   */
  securityOnlyParentSelected(): boolean;

  acceptSecurityaccount(securitycashaccount: Securityaccount | Cashaccount,
                        securityaccountOpenPositionUnits: SecurityaccountOpenPositionUnits[],
                        isSellBuyMarginInstrument: boolean): boolean;
}
