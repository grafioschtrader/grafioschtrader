/**
 * Enum defining the available chart types for time series visualization.
 * Used by TimeSeriesChartComponent to render different chart visualizations.
 */
export enum ChartType {
  /** Standard line chart showing close prices over time */
  LINE = 'LINE',
  /** Candlestick chart showing OHLC data with colored body representing open-close range */
  CANDLESTICK = 'CANDLESTICK',
  /** OHLC (Open-High-Low-Close) bar chart showing price range and direction */
  OHLC = 'OHLC'
}
