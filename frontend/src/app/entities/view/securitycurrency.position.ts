import { Securitycurrency } from '../securitycurrency';
import { LastpriceOrigin } from '../types/lastprice.origin';

export class SecuritycurrencyPosition<T extends Securitycurrency> {
  public securitycurrency: T;
  public ytdChangePercentage: number;
  public timeFrameChangePercentage: number;
  public units: number;
  /** Gain or loss of the open position in the currency of the security. */
  public positionGainLoss: number;
  public positionGainLossPercentage: number;
  /** Total of the open position in the currency of the security. */
  public valueSecurity: number;
  /** Total of the open position converted into the currency of the tenant at the current exchange rate. */
  public valueSecurityMC: number;
  /** Gain or loss of the open position in the currency of the tenant, including the currency effect. */
  public positionGainLossMC: number;
  /** positionGainLossMC relative to the cost of the open units in the currency of the tenant. */
  public positionGainLossPercentageMC: number;
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
  /** Dividends were requested, but this distributing security has none in the period: total return = price return. */
  public dividendDataMissing: boolean;
}
