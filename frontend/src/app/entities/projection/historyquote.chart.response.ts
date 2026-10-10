import { HistoryquoteDateClose } from './historyquote.date.close';
import { HistoryquoteOHLC } from './historyquote.ohlc';

/**
 * Response wrapper for chart data containing either simple date/close data or full OHLC data.
 * The frontend uses this response to determine whether candlestick/OHLC charts can be displayed.
 */
export interface HistoryquoteChartResponse {
  /**
   * Indicates whether OHLC data is available for this security.
   * When true, ohlcList contains the data. When false, dateCloseList contains the data.
   */
  ohlcAvailable: boolean;
  /**
   * Indicates whether volume data is available for this security.
   * When true, volume values in ohlcList are populated and the volume subchart can be displayed.
   */
  volumeAvailable?: boolean;
  /**
   * List of simple date and close price pairs.
   * Populated when ohlcAvailable is false.
   */
  dateCloseList?: HistoryquoteDateClose[];
  /**
   * List of full OHLC data points.
   * Populated when ohlcAvailable is true.
   */
  ohlcList?: HistoryquoteOHLC[];
  /**
   * True when dividends were added to the prices by the total return rule. False for a currency pair, an index,
   * a derived instrument, or when dividends were not requested.
   */
  totalReturn?: boolean;
  /**
   * Steps of the total return adjustment ordered by ex-date. A real trade price dated before an ex-date is multiplied
   * by the factor of the first younger ex-date to place it on the adjusted line.
   */
  totalReturnFactors?: TotalReturnFactor[];
  /**
   * True when the security should distribute but no dividend lies within the series, so that its total return equals
   * its price return.
   */
  dividendDataMissing?: boolean;
}

/**
 * One step of the total return adjustment of a price series.
 */
export interface TotalReturnFactor {
  /** Ex-date of a dividend */
  exDate: string;
  /** Factor applied to every price dated before this ex-date and after the next older one */
  factor: number;
}
