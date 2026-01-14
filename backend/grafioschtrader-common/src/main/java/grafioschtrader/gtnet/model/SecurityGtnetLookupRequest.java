package grafioschtrader.gtnet.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for querying security metadata from GTNet peers.
 * At least one of isin or tickerSymbol must be provided along with currency.
 */
@Schema(description = """
    Request DTO for querying security metadata from GTNet peers. Contains search criteria to find
    matching securities. At least one of ISIN or ticker symbol must be provided along with currency.""")
public class SecurityGtnetLookupRequest {

  @Schema(description = "ISIN (International Securities Identification Number) to search for")
  private String isin;

  @Schema(description = "ISO 4217 currency code of the security")
  private String currency;

  @Schema(description = "Ticker symbol to search for (alternative to ISIN)")
  private String tickerSymbol;

  public SecurityGtnetLookupRequest() {
  }

  public SecurityGtnetLookupRequest(String isin, String currency, String tickerSymbol) {
    this.isin = isin;
    this.currency = currency;
    this.tickerSymbol = tickerSymbol;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getTickerSymbol() {
    return tickerSymbol;
  }

  public void setTickerSymbol(String tickerSymbol) {
    this.tickerSymbol = tickerSymbol;
  }

  /**
   * Validates that the request has sufficient search criteria.
   * @return true if at least one identifier (ISIN or ticker) and currency are provided
   */
  public boolean isValid() {
    boolean hasIdentifier = (isin != null && !isin.isBlank()) ||
                            (tickerSymbol != null && !tickerSymbol.isBlank());
    boolean hasCurrency = currency != null && !currency.isBlank();
    return hasIdentifier && hasCurrency;
  }
}
