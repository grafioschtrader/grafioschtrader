package grafioschtrader.types;

/**
 * Group of a watchlist indicator column in the expanded row of the indicator tab. It is derived from the data the
 * expression reads and never persisted: an expression reading fundamental variables or dividends is fundamental, every
 * other one, including one reading only the current price fields, is technical.
 */
public enum IndicatorCategory {
  TECHNICAL, FUNDAMENTAL
}
