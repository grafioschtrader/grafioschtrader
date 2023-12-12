import {Currencypair} from '../currencypair';
import {ISecuritycurrencyIdDateClose} from '../projection/i.securitycurrency.id.date.close';

export class CurrencypairWithHistoryquote {
  constructor(public currencypair: Currencypair, public historyquote: ISecuritycurrencyIdDateClose) {
  }
}
