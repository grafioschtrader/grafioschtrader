import { Security } from '../security';
import { Currencypair } from '../currencypair';
import { SecuritycurrencyPosition } from './securitycurrency.position';
import { UDFFillInfo } from '../../udfmetasecurity/model/udf.metadata.security';

export interface SecuritycurrencyGroup {
  securityPositionList: SecuritycurrencyPosition<Security>[];
  currencypairPositionList: SecuritycurrencyPosition<Currencypair>[];
  lastTimestamp: string;
  idWatchlist: number;
  /** Currency of the tenant, the currency of the position values with the suffix MC. */
  mainCurrency?: string;
}

export interface SecuritycurrencyUDFGroup extends SecuritycurrencyGroup {
  udfEntityValues: { [idSecuritycurrency: number]: string };

  /**
   * Fill marks of the automatically filled and the locked cells, kept apart from the values. Outer key is the
   * security, inner key the field key ('f' followed by the field ID).
   */
  udfFillInfo?: { [idSecuritycurrency: number]: { [fieldKey: string]: UDFFillInfo } };
}
