package grafioschtrader.gtnet.model.msg;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for requesting entity/historical data exchange (GT_NET_ENTITY_REQUEST_SEL_C).
 *
 * Entity exchange covers historical price data (EOD quotes), security master data, and other persistent
 * entities that don't change during the trading day. This is distinct from intraday/last price sharing,
 * which has its own message types (GT_NET_LASTPRICE_*).
 *
 * The request proposes terms for the exchange, which can be accepted, modified, or rejected by the
 * responder. Default values may come from the sender's GTNet configuration but can be overridden
 * per-request.
 *
 * @see grafioschtrader.entities.GTNet#acceptEntityRequest for bidirectional exchange flag
 * @see grafioschtrader.entities.GTNetExchange for tracking individual entity exchanges
 */
@Schema(description = """
    Payload for requesting entity/historical data exchange. Covers EOD quotes, security master data, and other
    persistent entities. Proposes exchange terms including bidirectionality and rate limits, which the responder
    can accept, modify, or reject. Distinct from intraday price sharing (GT_NET_LASTPRICE_* messages).""")
public class EntityExchangeRequestMsg {

  @Schema(description = """
      Whether this domain's entity list may be shared with third parties. If true, the responder can include
      this domain when responding to GT_NET_UPDATE_SERVERLIST requests from others.""")
  public boolean spreadCapability;

  @Schema(description = """
      Requests bidirectional exchange. If true, the requester is offering to accept entity data requests from
      the responder as well, enabling mutual data sharing rather than one-way consumption.""")
  public boolean acceptEntityRequest;

  @Schema(description = """
      Proposed daily request limit for the responder to grant. The responder may accept this value or counter
      with a different limit in their response. Null suggests no specific limit preference.""")
  public Integer dailyRequestLimit;
}
