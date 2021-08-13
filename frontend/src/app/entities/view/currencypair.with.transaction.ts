import {Currencypair} from '../currencypair';
import {Transaction} from '../transaction';
import {NameSecuritycurrency} from './name.securitycurrency';
import {Securitycurrency} from '../securitycurrency';

export class CurrencypairWithTransaction implements NameSecuritycurrency {

  public currencypair: Currencypair;
  public transactionList: Transaction[];
  public sumAmountFrom: number;
  public sumAmountTo: number;
  public gainTo: number;
  public gainFrom: number;

  constructor(notRealCwt: CurrencypairWithTransaction) {
    Object.assign(this, notRealCwt);
    this.currencypair = new Currencypair(this.currencypair.fromCurrency, this.currencypair.toCurrency);
    this.currencypair.sLast = notRealCwt.currencypair.sLast;
    this.currencypair.sTimestamp = notRealCwt.currencypair.sTimestamp;
    this.currencypair.idSecuritycurrency = notRealCwt.currencypair.idSecuritycurrency;
  }

  getName(): string {
    return this.currencypair.fromCurrency + '/' + this.currencypair.toCurrency;
  }

  getSecuritycurrency(): Securitycurrency {
    return this.currencypair;
  }

}
