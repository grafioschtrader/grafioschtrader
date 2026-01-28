package grafioschtrader.reportviews.securitycurrency;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object containing URLs for external data provider access. This DTO is used to transfer
 * data provider URLs to the frontend without coupling to the full SecuritycurrencyPosition class.
 *
 * URLs may be:
 * - Direct links to external data provider pages
 * - Backend redirect URLs when API keys need to be protected from exposure
 * - Null if no connector is configured for that data type
 */
@Schema(description = "Contains URLs for accessing external data providers for a security or currency pair")
public class SecurityDataProviderUrls implements ISecurityDataProviderUrls {

  @Schema(description = "URL for intraday price data from the configured data provider")
  private String intradayUrl;

  @Schema(description = "URL for historical price data from the configured data provider")
  private String historicalUrl;

  @Schema(description = "URL for dividend data from the configured data provider (securities only)")
  private String dividendUrl;

  @Schema(description = "URL for stock split data from the configured data provider (securities only)")
  private String splitUrl;

  public SecurityDataProviderUrls() {
  }

  @Override
  public String getIntradayUrl() {
    return intradayUrl;
  }

  @Override
  public void setIntradayUrl(String intradayUrl) {
    this.intradayUrl = intradayUrl;
  }

  @Override
  public String getHistoricalUrl() {
    return historicalUrl;
  }

  @Override
  public void setHistoricalUrl(String historicalUrl) {
    this.historicalUrl = historicalUrl;
  }

  @Override
  public String getDividendUrl() {
    return dividendUrl;
  }

  @Override
  public void setDividendUrl(String dividendUrl) {
    this.dividendUrl = dividendUrl;
  }

  @Override
  public String getSplitUrl() {
    return splitUrl;
  }

  @Override
  public void setSplitUrl(String splitUrl) {
    this.splitUrl = splitUrl;
  }
}
