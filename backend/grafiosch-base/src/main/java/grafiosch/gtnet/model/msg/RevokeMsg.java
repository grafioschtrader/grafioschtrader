package grafiosch.gtnet.model.msg;

import java.util.Set;

import grafiosch.gtnet.IExchangeKindType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for revoking a data exchange (GT_NET_DATA_REVOKE_SEL_C).
 *
 * <p>
 * The revoke names the kinds of data whose exchange ends, and it takes effect as soon as it is handled: the sender
 * switches off the exchange of its grant for those kinds, the receiver closes the kinds on its entry of the sender. A
 * revoke that names no readable kind changes nothing on either side, so the sender refuses to send one.
 * </p>
 *
 * <p>
 * The entityKinds field uses the {@link IExchangeKindType} interface, like {@link DataRequestMsg}, so every application
 * revokes its own exchange kinds through this one message.
 * </p>
 */
@Schema(description = """
    Payload for revoking a data exchange. Names the kinds of data whose exchange ends; the revoke takes effect as soon
    as it is handled by both sides.""")
public class RevokeMsg {

  @Schema(description = """
      The kinds of data whose exchange ends. Must contain at least one entity kind with an active exchange.
      Available kinds depend on the application implementation of IExchangeKindType.""")
  @NotNull
  public Set<? extends IExchangeKindType> entityKinds;
}
