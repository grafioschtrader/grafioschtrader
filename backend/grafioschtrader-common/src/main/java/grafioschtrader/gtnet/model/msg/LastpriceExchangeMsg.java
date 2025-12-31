package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for intraday price exchange messages (GT_NET_LASTPRICE_EXCHANGE_SEL_C, GT_NET_LASTPRICE_PUSH_SEL_C).
 *
 * In a request:
 * - Contains instruments with their current local timestamps
 * - Timestamps can be null (meaning no local data, requesting any available price)
 *
 * In a response:
 * - Contains only instruments where the provider has more recent prices than the request
 * - Includes full OHLCV price data
 */
@Schema(description = """
    Payload for intraday price exchange between GTNet peers. In requests, contains instruments with current local
    timestamps. In responses, contains only instruments where the provider has newer data than requested.""")
public class LastpriceExchangeMsg {

  @Schema(description = "List of security price data (identified by ISIN + currency)")
  public List<InstrumentPriceDTO> securities = new ArrayList<>();

  @Schema(description = "List of currency pair price data (identified by fromCurrency + toCurrency)")
  public List<InstrumentPriceDTO> currencypairs = new ArrayList<>();

  @Schema(description = """
      Count of price updates the server accepted from the request (used in push acknowledgments).
      Only populated in GT_NET_LASTPRICE_PUSH_ACK_S responses.""")
  public Integer acceptedCount;

  public LastpriceExchangeMsg() {
  }

  /**
   * Creates a request payload with instruments to query.
   */
  public static LastpriceExchangeMsg forRequest(List<InstrumentPriceDTO> securities,
      List<InstrumentPriceDTO> currencypairs) {
    LastpriceExchangeMsg msg = new LastpriceExchangeMsg();
    msg.securities = securities != null ? securities : new ArrayList<>();
    msg.currencypairs = currencypairs != null ? currencypairs : new ArrayList<>();
    return msg;
  }

  /**
   * Creates a push acknowledgment response with the count of accepted updates.
   */
  public static LastpriceExchangeMsg forPushAck(int acceptedCount) {
    LastpriceExchangeMsg msg = new LastpriceExchangeMsg();
    msg.acceptedCount = acceptedCount;
    return msg;
  }

  /**
   * Returns the total number of instruments in this payload.
   */
  public int getTotalCount() {
    return (securities != null ? securities.size() : 0) + (currencypairs != null ? currencypairs.size() : 0);
  }

  /**
   * Checks if this payload is empty.
   */
  @JsonIgnore
  public boolean isEmpty() {
    return getTotalCount() == 0;
  }
}
