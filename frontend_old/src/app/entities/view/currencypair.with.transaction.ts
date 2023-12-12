import {Currencypair} from '../currencypair';
import {Transaction} from '../transaction';
import {INameSecuritycurrency} from './iname.securitycurrency';
import {Securitycurrency} from '../securitycurrency';

export class CurrencypairWithTransaction implements INameSecuritycurrency {

  public currencypair: Currencypair;
  public transactionList: Transaction[];
  public sumAmountFrom: number;
  public sumAmountTo: number;
  public gainTo: number;
  public gainFrom: number;
  public cwtReverse: CurrencypairWithTransaction;

  constructor(notRealCwt: CurrencypairWithTransaction) {
    Object.assign(this, notRealCwt);
    this.currencypair = new Currencypair(this.currencypair.fromCurrency, this.currencypair.toCurrency);
    this.currencypair.sLast = notRealCwt.currencypair.sLast;
    this.currencypair.sTimestamp = notRealCwt.currencypair.sTimestamp;
    this.currencypair.idSecuritycurrency = notRealCwt.currencypair.idSecuritycurrency;
    this.cwtReverse = notRealCwt.cwtReverse;
  }

  getName(): string {
    return this.currencypair.fromCurrency + '/' + this.currencypair.toCurrency;
  }

  getSecuritycurrency(): Securitycurrency {
    return this.currencypair;
  }

}
