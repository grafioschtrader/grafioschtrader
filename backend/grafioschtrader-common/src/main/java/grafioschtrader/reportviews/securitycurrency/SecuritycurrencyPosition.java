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

  public Double ytdChangePercentage;

  public Double timeFrameChangePercentage;
  public Double timeFrameAnualChangePercentage;

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

  public boolean watchlistSecurityHasEverTransactionTenant;

 
  @Schema(description = "Youngest historical data")
  public Date youngestHistoryDate;

}
