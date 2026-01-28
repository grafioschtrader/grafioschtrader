package grafioschtrader.reportviews.securitycurrency;

/**
 * Interface for objects that hold data provider URLs for securities and currency pairs.
 * These URLs provide access to external data sources for historical prices, intraday prices,
 * dividends, and stock splits.
 *
 * Implementing classes include:
 * - {@link SecuritycurrencyPosition} - used in watchlist and portfolio reports
 * - {@link SecurityDataProviderUrls} - standalone DTO for API responses
 */
public interface ISecurityDataProviderUrls {

  /**
   * Gets the URL for intraday price data from the configured data provider.
   *
   * @return the intraday data URL, or null if no intraday connector is configured
   */
  String getIntradayUrl();

  /**
   * Sets the URL for intraday price data.
   *
   * @param intradayUrl the intraday data URL
   */
  void setIntradayUrl(String intradayUrl);

  /**
   * Gets the URL for historical price data from the configured data provider.
   *
   * @return the historical data URL, or null if no historical connector is configured
   */
  String getHistoricalUrl();

  /**
   * Sets the URL for historical price data.
   *
   * @param historicalUrl the historical data URL
   */
  void setHistoricalUrl(String historicalUrl);

  /**
   * Gets the URL for dividend data from the configured data provider.
   * Only applicable to securities, not currency pairs.
   *
   * @return the dividend data URL, or null if no dividend connector is configured
   */
  String getDividendUrl();

  /**
   * Sets the URL for dividend data.
   *
   * @param dividendUrl the dividend data URL
   */
  void setDividendUrl(String dividendUrl);

  /**
   * Gets the URL for stock split data from the configured data provider.
   * Only applicable to securities, not currency pairs.
   *
   * @return the split data URL, or null if no split connector is configured
   */
  String getSplitUrl();

  /**
   * Sets the URL for stock split data.
   *
   * @param splitUrl the split data URL
   */
  void setSplitUrl(String splitUrl);
}
