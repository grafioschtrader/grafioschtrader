import { Securitycurrency } from '../securitycurrency';
import { LastpriceOrigin } from '../types/lastprice.origin';

export class SecuritycurrencyPosition<T extends Securitycurrency> {
  public securitycurrency: T;
  public ytdChangePercentage: number;
  public timeFrameChangePercentage: number;
  public units: number;
  public historicalUrl: string;
  public intradayUrl: string;
  public dividendUrl: string;
  public splitUrl: String;
  public isUsedElsewhere: boolean;
  public watchlistSecurityHasEver: boolean;
  public youngestHistoryDate: Date;
  /** Oldest stored historical price date, meaningful when held against activeFromDate of the security. */
  public oldestHistoryDate: Date;
  /** Completed trading sessions of the exchange that went by without the shown price being renewed. */
  public staleTradingSessions: number;
  /** Where the shown last price comes from. */
  public lastpriceOrigin: LastpriceOrigin;
}
