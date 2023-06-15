import {Currencypair} from '../currencypair';
import {Security} from '../security';

export class SecuritycurrencyLists {
  public securityList: Security[] = [];
  public currencypairList: Currencypair[] = [];

  get length() {
    return this.securityList.length + this.currencypairList.length;
  }
}
