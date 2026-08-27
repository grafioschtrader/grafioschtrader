import { Currencypair } from '../currencypair';
import { Security } from '../security';

export class SecuritycurrencyLists {
  public securityList: Security[] = [];
  public currencypairList: Currencypair[] = [];

  public static fromSecuritycurrency(securitycurrency: Security | Currencypair): SecuritycurrencyLists {
    const securitycurrencyLists = new SecuritycurrencyLists();
    // HttpClient returns plain objects, so instanceof cannot distinguish a newly created currency pair here.
    if ('fromCurrency' in securitycurrency && 'toCurrency' in securitycurrency) {
      securitycurrencyLists.currencypairList = [securitycurrency];
    } else {
      securitycurrencyLists.securityList = [securitycurrency];
    }
    return securitycurrencyLists;
  }

  get length() {
    return this.securityList.length + this.currencypairList.length;
  }
}
