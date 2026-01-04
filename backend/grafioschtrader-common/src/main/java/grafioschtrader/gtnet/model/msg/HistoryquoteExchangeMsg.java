package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for historical price exchange messages (GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C, GT_NET_HISTORYQUOTE_PUSH_SEL_C).
 *
 * In a request:
 * - Contains instruments with date ranges to query
 * - fromDate/toDate specify the requested historical data range
 * - records list is empty (only requesting data)
 *
 * In a response:
 * - Contains instruments with their historical price records
 * - Each instrument's records list contains the actual OHLCV data
 * - Only dates within the requested range that the provider has are included
 */
@Schema(description = """
    Payload for historical price exchange between GTNet peers. In requests, contains instruments with date ranges
    to query. In responses, contains instruments with their historical price records.""")
public class HistoryquoteExchangeMsg {

  @Schema(description = "List of security historical data (identified by ISIN + currency)")
  public List<InstrumentHistoryquoteDTO> securities = new ArrayList<>();

  @Schema(description = "List of currency pair historical data (identified by fromCurrency + toCurrency)")
  public List<InstrumentHistoryquoteDTO> currencypairs = new ArrayList<>();

  @Schema(description = """
      Count of price records the server accepted from the request (used in push acknowledgments).
      Only populated in GT_NET_HISTORYQUOTE_PUSH_ACK_S responses.""")
  public Integer acceptedCount;

  public HistoryquoteExchangeMsg() {
  }

  /**
   * Creates a request payload with instruments to query.
   */
  public static HistoryquoteExchangeMsg forRequest(List<InstrumentHistoryquoteDTO> securities,
      List<InstrumentHistoryquoteDTO> currencypairs) {
    HistoryquoteExchangeMsg msg = new HistoryquoteExchangeMsg();
    msg.securities = securities != null ? securities : new ArrayList<>();
    msg.currencypairs = currencypairs != null ? currencypairs : new ArrayList<>();
    return msg;
  }

  /**
   * Creates a push acknowledgment response with the count of accepted records.
   */
  public static HistoryquoteExchangeMsg forPushAck(int acceptedCount) {
    HistoryquoteExchangeMsg msg = new HistoryquoteExchangeMsg();
    msg.acceptedCount = acceptedCount;
    return msg;
  }

  /**
   * Returns the total number of instruments in this payload.
   */
  @JsonIgnore
  public int getTotalInstrumentCount() {
    return (securities != null ? securities.size() : 0) + (currencypairs != null ? currencypairs.size() : 0);
  }

  /**
   * Returns the total number of records across all instruments.
   */
  @JsonIgnore
  public int getTotalRecordCount() {
    int count = 0;
    if (securities != null) {
      for (InstrumentHistoryquoteDTO dto : securities) {
        count += dto.getRecordCount();
      }
    }
    if (currencypairs != null) {
      for (InstrumentHistoryquoteDTO dto : currencypairs) {
        count += dto.getRecordCount();
      }
    }
    return count;
  }

  /**
   * Checks if this payload is empty.
   */
  @JsonIgnore
  public boolean isEmpty() {
    return getTotalInstrumentCount() == 0;
  }
}
