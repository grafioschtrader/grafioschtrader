import { Currencypair } from '../currencypair';
import { Transaction } from '../transaction';
import { INameSecuritycurrency } from './iname.securitycurrency';
import { Securitycurrency } from '../securitycurrency';

/** JSON returned by the currency-pair transaction report, before the chart adapter adds its methods. */
export interface CurrencypairTransactionData {
  currencypair: Currencypair;
  transactionList: Transaction[];
  sumAmountFrom: number;
  sumAmountTo: number;
  gainTo: number;
  gainFrom: number;
  cwtReverse: CurrencypairTransactionData | null;
}

export class CurrencypairWithTransaction implements INameSecuritycurrency, CurrencypairTransactionData {
  public currencypair: Currencypair;
  public transactionList: Transaction[];
  public sumAmountFrom: number;
  public sumAmountTo: number;
  public gainTo: number;
  public gainFrom: number;
  public cwtReverse: CurrencypairTransactionData | null;

  constructor(notRealCwt: CurrencypairTransactionData) {
    Object.assign(this, notRealCwt);
    this.transactionList = notRealCwt.transactionList;
    this.sumAmountFrom = notRealCwt.sumAmountFrom;
    this.sumAmountTo = notRealCwt.sumAmountTo;
    this.gainTo = notRealCwt.gainTo;
    this.gainFrom = notRealCwt.gainFrom;
    this.currencypair = new Currencypair(notRealCwt.currencypair.fromCurrency, notRealCwt.currencypair.toCurrency);
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
