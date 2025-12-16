package grafioschtrader.gtnet.model.msg;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for revoking data exchange agreements (GT_NET_*_REVOKE_SEL_C messages).
 *
 * Used when a domain decides to terminate an existing data sharing arrangement. The revocation
 * takes effect at the specified future time, giving the other party time to adjust their data
 * sourcing strategy.
 *
 * Different revocation message codes exist for different exchange types:
 * <ul>
 *   <li>GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C - Stop sharing server list</li>
 *   <li>GT_NET_LASTPRICE_REVOKE_SEL_C - Stop intraday price sharing</li>
 *   <li>GT_NET_ENTITY_REVOKE_SEL_C - Stop entity data sharing</li>
 *   <li>GT_NET_BOTH_REVOKE_SEL_C - Stop all data sharing</li>
 * </ul>
 */
@Schema(description = """
    Payload for revoking data exchange agreements. Specifies when the revocation takes effect, giving the other
    party notice to adjust their data sourcing. Used with various revocation message codes (serverlist, lastprice,
    entity, or both).""")
public class RevokeMsg {

  @Schema(description = """
      UTC timestamp when the revocation takes effect. Must be in the future to give the affected party time to
      find alternative data sources. After this time, requests from the revoked party will be rejected.""")
  @NotNull
  @Future
  public LocalDateTime fromDateTime;
}
