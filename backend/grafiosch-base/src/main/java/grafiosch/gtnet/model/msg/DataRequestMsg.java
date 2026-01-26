package grafiosch.gtnet.model.msg;

import java.util.Set;

import grafiosch.gtnet.IExchangeKindType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for requesting entity/historical data exchange (GT_NET_DATA_REQUEST_SEL_RR_C).
 *
 * Entity exchange covers historical price data (EOD quotes), security master data, and other persistent
 * entities that don't change during the trading day. This is distinct from intraday/last price sharing,
 * which has its own message types (GT_NET_LASTPRICE_*).
 *
 * The request proposes terms for the exchange, which can be accepted, modified, or rejected by the
 * responder. Default values may come from the sender's GTNet configuration but can be overridden
 * per-request.
 *
 * <p>The entityKinds field uses the {@link IExchangeKindType} interface, allowing different applications
 * to define their own exchange kind types while sharing this common message structure.</p>
 */
@Schema(description = """
    Payload for requesting entity/historical data exchange. Covers EOD quotes, security master data, and other
    persistent entities. Proposes exchange terms including bidirectionality and rate limits, which the responder
    can accept, modify, or reject. Distinct from intraday price sharing (GT_NET_LASTPRICE_* messages).""")
public class DataRequestMsg {

  @Schema(description = """
      Specifies which data types the user wants to exchange. Must contain at least one entity kind.
      Available kinds depend on the application implementation of IExchangeKindType.""")
  public Set<? extends IExchangeKindType> entityKinds;

}
