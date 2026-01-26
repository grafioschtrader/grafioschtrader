package grafiosch.gtnet.handler;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Provides common functionality for parsing revoked entity kinds and updating GTNetEntity states.
 * Uses the ExchangeKindTypeRegistry for application-agnostic kind resolution.
 *
 * If no entityKinds parameter is provided, all syncable entity kinds are revoked.
 */
public abstract class AbstractDataRevokeHandler extends AbstractAnnouncementHandler {

  @Autowired
  protected ExchangeKindTypeRegistry exchangeKindRegistry;

  /**
   * Parses the entityKinds parameter from the message context.
   * If no parameter is provided, returns all syncable entity kinds.
   *
   * @param context the message context
   * @return set of entity kinds to revoke
   */
  protected Set<IExchangeKindType> getRevokedEntityKinds(GTNetMessageContext context) {
    String entityKindsParam = context.getParamValue("entityKinds");
    if (entityKindsParam == null || entityKindsParam.isBlank()) {
      return exchangeKindRegistry.getSyncableKinds();
    }

    Set<IExchangeKindType> kinds = Arrays.stream(entityKindsParam.split(","))
        .map(String::trim)
        .map(exchangeKindRegistry::parse)
        .filter(kind -> kind != null)
        .collect(Collectors.toSet());

    return kinds.isEmpty() ? exchangeKindRegistry.getSyncableKinds() : kinds;
  }

  /**
   * Returns the default syncable entity kinds when no specific kinds are provided.
   *
   * @return set of syncable kinds from the registry
   */
  protected Set<IExchangeKindType> getDefaultSyncableKinds() {
    return exchangeKindRegistry.getSyncableKinds();
  }

  /**
   * Updates a GTNetEntity to revoked state for the specified entity kind.
   * When we receive a revoke from them, they are stopping their side of the exchange,
   * so we lose RECEIVE capability. The entity state is also set to CLOSED.
   *
   * @param remoteGTNet the remote GTNet
   * @param kind        the entity kind to revoke
   */
  protected void updateEntityForRevoke(GTNet remoteGTNet, IExchangeKindType kind) {
    remoteGTNet.getGtNetEntities().stream()
        .filter(e -> e.getEntityKindValue() == kind.getValue())
        .findFirst()
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
