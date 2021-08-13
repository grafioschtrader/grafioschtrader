import {Security} from '../../entities/security';

export interface MissingQuotesWithSecurities {
  year: number;
  firstEverTradingDay: Date;
  securities: Security[];
  dateSecurityMissingMap: { [key: string]: number[] };
  countIdSecurityMissingsMap: { [key: number]: number };
}

