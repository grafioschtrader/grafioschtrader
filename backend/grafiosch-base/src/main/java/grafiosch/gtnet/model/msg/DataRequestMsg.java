package grafiosch.gtnet.model.msg;

import java.util.Set;

import grafiosch.gtnet.IExchangeKindType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for requesting entity/historical data exchange (GT_NET_DATA_REQUEST_SEL_RR_C).
 *
 * Entity exchange covers historical price data (EOD quotes), security master data, and other persistent entities that
 * do not change during the trading day. This is distinct from intraday/last price sharing, which has its own message
 * types (GT_NET_LASTPRICE_*).
 *
 * <p>
 * The request names the kinds of data it asks for and nothing else. There are no terms to negotiate in it: the
 * responder accepts or rejects the set as a whole, with {@code GT_NET_DATA_REQUEST_ACCEPT_S} or
 * {@code GT_NET_DATA_REQUEST_REJECTED_S}. Rate limits and the direction of an exchange are not part of this message —
 * they belong to the {@code GTNetEntity} rows of each side and are published through
 * {@code GT_NET_SETTINGS_UPDATED_ALL_C}.
 * </p>
 *
 * <p>
 * The entityKinds field uses the {@link IExchangeKindType} interface, allowing different applications to define their
 * own exchange kind types while sharing this common message structure.
 * </p>
 */
@Schema(description = """
    Payload for requesting entity/historical data exchange. Covers EOD quotes, security master data, and other
    persistent entities. Names the kinds of data requested; the responder accepts or rejects the set as a whole.
    Distinct from intraday price sharing (GT_NET_LASTPRICE_* messages).""")
public class DataRequestMsg {

  @Schema(description = """
      Specifies which data types the user wants to exchange. Must contain at least one entity kind.
      Available kinds depend on the application implementation of IExchangeKindType.""")
  public Set<? extends IExchangeKindType> entityKinds;

}
