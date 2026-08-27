package grafiosch.gtnet.handler;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.IExchangeKindType;

/**
 * Abstract base class for handling data revocation announcement messages.
 *
 * Provides common functionality for parsing revoked entity kinds and updating GTNetEntity states. Uses the
 * ExchangeKindTypeRegistry for application-agnostic kind resolution.
 *
 * A revoke that names no readable entity kind is a logged no-op rather than a revoke of everything.
 */
public abstract class AbstractDataRevokeHandler extends AbstractAnnouncementHandler {

  @Autowired
  protected ExchangeKindTypeRegistry exchangeKindRegistry;

  /**
   * Parses the entityKinds parameter from the message context.
   *
   * <p>
   * A missing, blank or entirely unparseable list yields no kinds, and the revoke is a logged no-op. Falling back to
   * all syncable kinds would turn a revoke this instance cannot read into the widest possible revoke, ending an
   * exchange the peer never asked to end.
   * </p>
   *
   * @param context the message context
   * @return the entity kinds to revoke, possibly empty
   */
  protected Set<IExchangeKindType> getRevokedEntityKinds(GTNetMessageContext context) {
    return exchangeKindRegistry.parseAll(context.getParamValue(ExchangeKindTypeRegistry.ENTITY_KINDS_PARAM));
  }

  /**
   * Updates a GTNetEntity to revoked state for the specified entity kind. When we receive a revoke from them, they are
   * stopping their side of the exchange, so we lose RECEIVE capability. The entity state is also set to CLOSED.
   *
   * @param remoteGTNet the remote GTNet
   * @param kind        the entity kind to revoke
   */
  protected void updateEntityForRevoke(GTNet remoteGTNet, IExchangeKindType kind) {
    remoteGTNet.getGtNetEntities().stream().filter(e -> e.getEntityKindValue() == kind.getValue()).findFirst()
        .ifPresent(entity -> {
          entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
          entity.setServerState(GTNetServerStateTypes.SS_CLOSED);

          GTNetConfigEntity configEntity = entity.getGtNetConfigEntity();
          if (configEntity != null) {
            configEntity.setExchange(false);
          }
        });
  }
}
