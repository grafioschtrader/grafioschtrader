package grafioschtrader.reportviews.securitycurrency;

import java.util.Date;

import grafioschtrader.entities.Securitycurrency;
import io.swagger.v3.oas.annotations.media.Schema;

public class SecuritycurrencyPosition<T extends Securitycurrency<T>> {

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

  public boolean isUsedElsewhere = true;

  @Schema(description = "Depend on the watchlist it is true when security has transaction or security has split or dividend")
  public boolean watchlistSecurityHasEver;

  @Schema(description = "Youngest historical data")
  public Date youngestHistoryDate;
  
}
