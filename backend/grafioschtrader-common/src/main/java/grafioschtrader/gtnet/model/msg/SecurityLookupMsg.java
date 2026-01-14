package grafioschtrader.gtnet.model.msg;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for security metadata lookup request (GT_NET_SECURITY_LOOKUP_SEL_C).
 *
 * Used in M2M communication to request security information from a remote GTNet peer.
 * At least one of isin or tickerSymbol should be provided along with currency.
 */
@Schema(description = """
    Payload for requesting security metadata from a remote GTNet peer. Contains search criteria
    including ISIN, currency, and/or ticker symbol. The remote peer will search its database
    and return matching security metadata.""")
public class SecurityLookupMsg {

  @Schema(description = "ISIN (International Securities Identification Number) to search for")
  public String isin;

  @Schema(description = "ISO 4217 currency code of the security")
  public String currency;

  @Schema(description = "Ticker symbol to search for (alternative or additional to ISIN)")
  public String tickerSymbol;

  public SecurityLookupMsg() {
  }

  public SecurityLookupMsg(String isin, String currency, String tickerSymbol) {
    this.isin = isin;
    this.currency = currency;
    this.tickerSymbol = tickerSymbol;
  }
}
