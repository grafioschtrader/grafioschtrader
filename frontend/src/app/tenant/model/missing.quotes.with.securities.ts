import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';

export interface MissingQuotesWithSecurities {
  year: number;
  firstEverTradingDay: Date;
  securities: Security[];
  /**
   * Currency pairs whose exchange rate is missing on some days. A holding can only be converted into the currency of
   * the tenant when its currency pair carries a rate for the day, so such a gap invalidates a reporting day just like
   * a missing security quote does.
   */
  currencypairs: Currencypair[];
  dateSecurityMissingMap: { [key: string]: number[] };
  countIdSecurityMissingsMap: { [key: number]: number };
}
