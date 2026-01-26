package grafiosch.gtnet.handler;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.model.msg.DataRequestMsg;

/**
 * Abstract base class for handling data exchange request messages.
 *
 * Provides common functionality for parsing entity kinds from DataRequestMsg payloads and updating
 * GTNetEntity/GTNetConfigEntity states. Uses the ExchangeKindTypeRegistry for application-agnostic
 * kind resolution.
 *
 * Subclasses should implement request validation and can override entity update behavior.
 */
public abstract class AbstractDataRequestHandler extends AbstractRequestHandler {

  @Autowired
  protected ExchangeKindTypeRegistry exchangeKindRegistry;

  /**
   * Extracts the entity kinds from the DataRequestMsg payload.
   *
   * Uses the registry to convert serialized byte values back to registered IExchangeKindType instances.
   *
   * @param context the message context
   * @return set of requested entity kinds, or empty set if payload is missing or has no entity kinds
   */
  protected Set<IExchangeKindType> getRequestedEntityKinds(GTNetMessageContext context) {
    if (!context.hasPayload()) {
      return Set.of();
    }
    try {
      DataRequestMsg dataRequest = context.getPayloadAs(DataRequestMsg.class);
      if (dataRequest.entityKinds == null || dataRequest.entityKinds.isEmpty()) {
        return Set.of();
      }
      return dataRequest.entityKinds.stream()
          .map(kind -> exchangeKindRegistry.getByValue(kind.getValue()))
          .filter(kind -> kind != null)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      return Set.of();
    }
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
   * Gets or creates a GTNetEntity for the specified kind.
   *
   * @param gtNet the GTNet to search/create entity in
   * @param kind  the exchange kind
   * @return existing or newly created GTNetEntity
   */
  protected GTNetEntity getOrCreateEntity(GTNet gtNet, IExchangeKindType kind) {
    return gtNet.getGtNetEntities().stream()
        .filter(e -> e.getEntityKindValue() == kind.getValue())
        .findFirst()
        .orElseGet(() -> {
          GTNetEntity newEntity = new GTNetEntity();
          newEntity.setIdGtNet(gtNet.getIdGtNet());
          newEntity.setEntityKindValue(kind.getValue());
          newEntity.setServerState(GTNetServerStateTypes.SS_OPEN);
          gtNet.getGtNetEntities().add(newEntity);
          return newEntity;
        });
  }

  /**
   * Updates an entity for accept: sets state to OPEN and creates config entity.
   *
   * @param gtNet the GTNet containing the entity
   * @param kind  the exchange kind to update
   */
  protected void updateEntityForAccept(GTNet gtNet, IExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(gtNet, kind);
    entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
  }

  /**
   * Updates an entity for reject: sets state to CLOSED.
   *
   * @param gtNet the GTNet containing the entity
   * @param kind  the exchange kind to update
   */
  protected void updateEntityForReject(GTNet gtNet, IExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(gtNet, kind);
    entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
    entity.setServerState(GTNetServerStateTypes.SS_CLOSED);
  }

  /**
   * Creates GTNetConfigEntity for entities that were accepted.
   * Called after initial entity creation to ensure IDs are available.
   *
   * @param gtNet the GTNet with entities to update
   * @param kinds the entity kinds to create config entities for
   */
  protected void createConfigEntitiesForAcceptedKinds(GTNet gtNet, Set<IExchangeKindType> kinds) {
    for (IExchangeKindType kind : kinds) {
      gtNet.getEntityByKind(kind.getValue()).ifPresent(entity -> {
        if (entity.getGtNetConfigEntity() == null) {
          GTNetConfigEntity configEntity = new GTNetConfigEntity();
          configEntity.setIdGtNetEntity(entity.getIdGtNetEntity());
          entity.setGtNetConfigEntity(configEntity);
        }
      });
    }
  }

  /**
   * Updates myGTNet's entity to reflect that this server offers the specified entity kind.
   * Only sets acceptRequest to AC_OPEN if currently closed - preserves AC_PUSH_OPEN if already set.
   *
   * @param myGTNet the local GTNet
   * @param kind    the exchange kind
   */
  protected void updateMyEntityForAccept(GTNet myGTNet, IExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(myGTNet, kind);
    if (!entity.isAccepting()) {
      entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    }
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
  }
}
