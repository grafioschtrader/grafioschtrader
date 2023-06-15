import {Security} from '../security';
import {Currencypair} from '../currencypair';
import {SecuritycurrencyPosition} from './securitycurrency.position';

export class SecuritycurrencyGroup {

  constructor(public securityPositionList: SecuritycurrencyPosition<Security>[],
              public currencypairPositionList: SecuritycurrencyPosition<Currencypair>[],
              public lastTimestamp: string,
              public idWatchlist: number) {
  }
}
