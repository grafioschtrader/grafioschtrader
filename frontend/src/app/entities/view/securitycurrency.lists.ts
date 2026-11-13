import { Currencypair } from '../currencypair';
import { Security } from '../security';
import { AdvancedSearchValue } from '../search/advanced.search';

export class SecuritycurrencyLists {
  public securityList: Security[] = [];
  public currencypairList: Currencypair[] = [];
  /**
   * Only for a search with advanced criteria: per security id and field key the value that satisfied the criterion.
   * Absent for other requests.
   */
  public advancedValues?: Record<number, Record<string, AdvancedSearchValue>>;
  /** Only for a search with indicator criteria: the candidates for which an indicator could not be evaluated. */
  public advancedNotEvaluated?: number;

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
