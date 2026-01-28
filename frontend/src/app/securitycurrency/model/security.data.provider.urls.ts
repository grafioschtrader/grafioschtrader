/**
 * Data Transfer Object containing URLs for external data provider access.
 * These URLs provide access to external data sources for historical prices,
 * intraday prices, dividends, and stock splits.
 */
export interface SecurityDataProviderUrls {
  /** URL for intraday price data from the configured data provider */
  intradayUrl: string | null;

  /** URL for historical price data from the configured data provider */
  historicalUrl: string | null;

  /** URL for dividend data from the configured data provider (securities only) */
  dividendUrl: string | null;

  /** URL for stock split data from the configured data provider (securities only) */
  splitUrl: string | null;
}
