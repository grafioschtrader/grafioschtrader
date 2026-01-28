package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;

import grafioschtrader.entities.Securitycurrency;
import io.swagger.v3.oas.annotations.media.Schema;

public class SecuritycurrencyPosition<T extends Securitycurrency<T>> implements ISecurityDataProviderUrls {

  public SecuritycurrencyPosition(T securitycurrency) {
    super();
    this.securitycurrency = securitycurrency;
  }

  public T securitycurrency;

  @Schema(description = "The return since the beginning of the year.")
  public Double ytdChangePercentage;

  @Schema(description = "The return on investment in the specified period.")
  public Double timeFrameChangePercentage;
  @Schema(description = "The annualized return for the specified time frame.")
  public Double timeFrameAnnualChangePercentage;

  // units after transaction
  public Double units;
  public Double positionGainLossPercentage;
  public Double valueSecurity;

  @Schema(description = "Intra day data html access produced from data connector of security")
  public String intradayUrl;

  @Schema(description = "Historical data html access produced from data connector of security")
  public String historicalUrl;

  @Schema(description = "Divdend data html access produced from data connector of security")
  public String dividendUrl;

  @Schema(description = "Split data html access produced from data connector of security")
  public String splitUrl;

  public boolean isUsedElsewhere = true;

  @Schema(description = """
      Depend on the watchlist it is true when security has transaction or security has split or dividend.
      This can be useful to indicate that an instrument has a transaction, split or dividend without already loading it.""")
  public boolean watchlistSecurityHasEver;

  @Schema(description = "Youngest historical data")
  public Date youngestHistoryDate;

  // ISecurityDataProviderUrls implementation

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
