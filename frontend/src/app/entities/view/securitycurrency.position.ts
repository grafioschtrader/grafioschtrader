import type { Securitycurrency } from '../securitycurrency';
import type { LastpriceOrigin } from '../types/lastprice.origin';

export interface SecuritycurrencyPosition<T extends Securitycurrency> {
  /** Values of the configured watchlist indicator columns, added to display rows. */
  [indicatorField: `ind_${number}`]: number | boolean | null;
  securitycurrency: T;
  ytdChangePercentage: number | null;
  timeFrameChangePercentage: number | null;
  timeFrameAnnualChangePercentage: number | null;
  units: number | null;
  /** Gain or loss of the open position in the currency of the security. */
  positionGainLoss: number | null;
  positionGainLossPercentage: number | null;
  /** Total of the open position in the currency of the security. */
  valueSecurity: number | null;
  /** Total of the open position converted into the currency of the tenant at the current exchange rate. */
  valueSecurityMC: number | null;
  /** Gain or loss of the open position in the currency of the tenant, including the currency effect. */
  positionGainLossMC: number | null;
  /** positionGainLossMC relative to the cost of the open units in the currency of the tenant. */
  positionGainLossPercentageMC: number | null;
  historicalUrl: string | null;
  intradayUrl: string | null;
  dividendUrl: string | null;
  splitUrl: string | null;
  isUsedElsewhere: boolean;
  watchlistSecurityHasEver: boolean;
  youngestHistoryDate: string | null;
  /** Oldest stored historical price date, meaningful when held against activeFromDate of the security. */
  oldestHistoryDate: string | null;
  /** Completed trading sessions of the exchange that went by without the shown price being renewed. */
  staleTradingSessions: number | null;
  /**
   * Trading sessions after youngestHistoryDate whose closing price should already be stored, because the last end of
   * day load of the exchange ran after them. Null when the instrument has no historical data source.
   */
  missingEodSessions: number | null;
  /** Where the shown last price comes from. */
  lastpriceOrigin: LastpriceOrigin;
  /** Dividends were requested, but this distributing security has none in the period: total return = price return. */
  dividendDataMissing: boolean;
}
