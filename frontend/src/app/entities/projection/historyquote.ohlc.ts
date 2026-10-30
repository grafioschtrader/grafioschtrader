/**
 * Interface for OHLC (Open-High-Low-Close) historical quote data.
 * Used for candlestick and OHLC chart rendering.
 */
export interface HistoryquoteOHLC {
  /** Trading date in YYYY-MM-DD format */
  date: string;
  /** Opening price of the day */
  open?: number | null;
  /** Highest price of the day */
  high?: number | null;
  /** Lowest price of the day */
  low?: number | null;
  /** Closing price of the day */
  close?: number | null;
  /** Trading volume for the day */
  volume?: number | null;
}
