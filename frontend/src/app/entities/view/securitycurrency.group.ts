import {Security} from '../security';
import {Currencypair} from '../currencypair';
import {SecuritycurrencyPosition} from './securitycurrency.position';

export interface SecuritycurrencyGroup {
  securityPositionList: SecuritycurrencyPosition<Security>[];
  currencypairPositionList: SecuritycurrencyPosition<Currencypair>[];
  lastTimestamp: string;
  idWatchlist: number;
}

export interface SecuritycurrencyUDFGroup extends SecuritycurrencyGroup {
  udfEntityValues: {[idSecuritycurrency: number]: string};
}


