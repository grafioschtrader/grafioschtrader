import {Securitycurrency} from '../securitycurrency';

export class SecuritycurrencyPosition<T extends Securitycurrency> {
  public securitycurrency: T;
  public ytdChangePercentage: number;
  public timeFrameChangePercentage: number;
  public units: number;
  public historicalUrl: string;
  public intradayUrl: string;
  public isUsedElsewhere: boolean;
  public watchlistSecurityHasEverTransactionTenant: boolean;
  public youngestHistoryDate: Date;
}
