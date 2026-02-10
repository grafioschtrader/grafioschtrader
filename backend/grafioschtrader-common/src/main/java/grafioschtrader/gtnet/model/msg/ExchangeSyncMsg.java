package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for GTNetExchange sync messages (GT_NET_EXCHANGE_SYNC_SEL_RR_C, GT_NET_EXCHANGE_SYNC_RESPONSE_S).
 *
 * Contains a list of instruments with their send flags, identified by ISIN+currency (securities) or
 * fromCurrency+toCurrency (currency pairs).
 *
 * In a request:
 * - Contains instruments that have been modified locally since the last sync timestamp
 * - Only includes instruments with at least one send flag enabled
 *
 * In a response:
 * - Contains the remote server's changed instruments for bidirectional sync
 */
@Schema(description = """
    Payload for exchange configuration sync between GTNet peers. Contains instruments with their
    willingness to share intraday (lastpriceSend) and historical (historicalSend) price data.""")
public class ExchangeSyncMsg {

  @Schema(description = "Timestamp since when changes are included (for incremental sync)")
  public Date sinceTimestamp;

  @Schema(description = "List of exchange configuration entries")
  public List<ExchangeSyncItem> items = new ArrayList<>();

  public ExchangeSyncMsg() {
  }

  /**
   * Creates a request/response payload with the given items.
   *
   * @param sinceTimestamp the timestamp since when changes are included
   * @param items the list of exchange sync items
   * @return the constructed ExchangeSyncMsg
   */
  public static ExchangeSyncMsg forRequest(Date sinceTimestamp, List<ExchangeSyncItem> items) {
    ExchangeSyncMsg msg = new ExchangeSyncMsg();
    msg.sinceTimestamp = sinceTimestamp;
    msg.items = items != null ? items : new ArrayList<>();
    return msg;
  }

  /**
   * Checks if this payload is empty.
   *
   * @return true if no items are present
   */
  @JsonIgnore
  public boolean isEmpty() {
    return items == null || items.isEmpty();
  }

  /**
   * Returns the total count of items.
   *
   * @return the number of items in this payload
   */
  @JsonIgnore
  public int getItemCount() {
    return items != null ? items.size() : 0;
  }

  /**
   * Single instrument's exchange configuration for sync.
   *
   * For securities, isin and currency are set, toCurrency is null.
   * For currency pairs, isin is null, currency is fromCurrency, and toCurrency is set.
   */
  @Schema(description = "Exchange configuration for a single instrument")
  public static class ExchangeSyncItem {

    @Schema(description = "ISIN for securities, null for currency pairs")
    public String isin;

    @Schema(description = "Currency code for securities, or fromCurrency for currency pairs")
    public String currency;

    @Schema(description = "toCurrency for currency pairs, null for securities")
    public String toCurrency;

    @Schema(description = "Whether this server offers intraday prices for this instrument")
    public boolean lastpriceSend;

    @Schema(description = "Whether this server offers historical prices for this instrument")
    public boolean historicalSend;

    // History settings (populated when historicalSend=true)
    @Schema(description = "Retry counter for historical price data downloads")
    public Short retryHistoryLoad;

    @Schema(description = "Earliest available history date (yyyy-MM-dd)")
    public String historyMinDate;

    @Schema(description = "Latest available history date (yyyy-MM-dd)")
    public String historyMaxDate;

    @Schema(description = "Percentage of quotes with valid OHL values (0-100)")
    public Double ohlPercentage;

    // Intra settings (populated when lastpriceSend=true)
    @Schema(description = "Retry counter for intraday price data downloads")
    public Short retryIntraLoad;

    @Schema(description = "Timestamp of last intraday price update")
    public Date sTimestamp;

    public ExchangeSyncItem() {
    }

    /**
     * Constructs an exchange sync item.
     *
     * @param isin the ISIN for securities, null for currency pairs
     * @param currency the currency for securities, or fromCurrency for currency pairs
     * @param toCurrency the toCurrency for currency pairs, null for securities
     * @param lastpriceSend whether intraday prices are offered
     * @param historicalSend whether historical prices are offered
     */
    public ExchangeSyncItem(String isin, String currency, String toCurrency,
        boolean lastpriceSend, boolean historicalSend) {
      this.isin = isin;
      this.currency = currency;
      this.toCurrency = toCurrency;
      this.lastpriceSend = lastpriceSend;
      this.historicalSend = historicalSend;
    }

    /**
     * Creates an item for a security.
     */
    public static ExchangeSyncItem forSecurity(String isin, String currency,
        boolean lastpriceSend, boolean historicalSend) {
      return new ExchangeSyncItem(isin, currency, null, lastpriceSend, historicalSend);
    }

    /**
     * Creates an item for a currency pair.
     */
    public static ExchangeSyncItem forCurrencypair(String fromCurrency, String toCurrency,
        boolean lastpriceSend, boolean historicalSend) {
      return new ExchangeSyncItem(null, fromCurrency, toCurrency, lastpriceSend, historicalSend);
    }

    /**
     * Checks if this item represents a security.
     *
     * @return true if this is a security (has ISIN)
     */
    @JsonIgnore
    public boolean isSecurity() {
      return isin != null;
    }

    /**
     * Checks if this item represents a currency pair.
     *
     * @return true if this is a currency pair (no ISIN, has toCurrency)
     */
    @JsonIgnore
    public boolean isCurrencypair() {
      return isin == null && toCurrency != null;
    }

    /**
     * Returns a unique key for this item.
     *
     * @return "ISIN:currency" for securities, "fromCurrency:toCurrency" for currency pairs
     */
    @JsonIgnore
    public String getKey() {
      if (isSecurity()) {
        return isin + ":" + currency;
      } else {
        return currency + ":" + toCurrency;
      }
    }

    /**
     * Checks if any send flag is enabled.
     *
     * @return true if lastpriceSend or historicalSend is true
     */
    @JsonIgnore
    public boolean hasAnySendEnabled() {
      return lastpriceSend || historicalSend;
    }
  }
}
