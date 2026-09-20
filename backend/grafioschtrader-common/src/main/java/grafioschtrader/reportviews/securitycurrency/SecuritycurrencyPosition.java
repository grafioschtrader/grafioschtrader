package grafioschtrader.reportviews.securitycurrency;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.LastpriceOrigin;
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
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate youngestHistoryDate;

  @Schema(description = """
      Oldest historical data. Compared with the activeFromDate of the security it tells whether the historical data
      source ever delivered the beginning of the history. Null when no historical price is stored at all.""")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate oldestHistoryDate;

  @Schema(description = """
      Number of completed trading sessions of the exchange of this instrument that went by without the displayed price
      being renewed. Zero means the price is current; a weekend or a holiday of that exchange never raises it, because
      sessions and not calendar days are counted. Capped, so a long dead instrument does not report an ever growing
      number. Null when the age cannot be determined.""")
  public Integer staleTradingSessions;

  @Schema(description = """
      Where the displayed last price comes from. Only INTRADAY is a price of the current session; the two history
      values mean the intraday feed no longer delivers and the newest historical closing price took its place.""")
  public LastpriceOrigin lastpriceOrigin = LastpriceOrigin.INTRADAY;

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
