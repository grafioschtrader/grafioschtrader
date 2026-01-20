import {HistoryquoteDateClose} from './historyquote.date.close';
import {HistoryquoteOHLC} from './historyquote.ohlc';

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
   * List of simple date and close price pairs.
   * Populated when ohlcAvailable is false.
   */
  dateCloseList?: HistoryquoteDateClose[];
  /**
   * List of full OHLC data points.
   * Populated when ohlcAvailable is true.
   */
  ohlcList?: HistoryquoteOHLC[];
}
