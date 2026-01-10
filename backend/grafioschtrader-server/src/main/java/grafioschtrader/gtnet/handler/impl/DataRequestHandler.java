package grafioschtrader.gtnet.handler.impl;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfigEntity;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractRequestHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.ValidationResult;
import grafioschtrader.gtnet.model.msg.DataRequestMsg;
import grafioschtrader.repository.GTNetEntityJpaRepository;

/**
 * Unified handler for GT_NET_DATA_REQUEST_SEL_C messages.
 *
 * Processes requests for any combination of data types via the DataRequestMsg payload. The payload contains an
 * entityKinds set specifying which data types (LAST_PRICE, HISTORICAL_PRICES) the requester wants to exchange.
 */
@Component
public class DataRequestHandler extends AbstractRequestHandler {

  @Autowired
  private GTNetEntityJpaRepository gtNetEntityJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_RR_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote (completed handshake)
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Data request from unknown domain - handshake required first");
    }

    // Validate entityKinds in payload exists
    Set<GTNetExchangeKindType> requestedKinds = getRequestedEntityKinds(context);
    if (requestedKinds.isEmpty()) {
      return ValidationResult.invalid("MISSING_ENTITY_KINDS",
          "Data request must specify at least one entity kind in the payload");
    }

    return ValidationResult.ok();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects on request receipt - side effects applied after response determination
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCodeType responseCode,
      GTNetMessage storedRequest) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    Set<GTNetExchangeKindType> requestedKinds = getRequestedEntityKinds(context);

    if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S) {
      for (GTNetExchangeKindType kind : requestedKinds) {
        updateEntityForAccept(remoteGTNet, kind);
      }
      saveRemoteGTNet(remoteGTNet);

      // Also update myGTNet to reflect that this server now offers these entity kinds.
      // This ensures the serverState is correctly communicated to other servers.
      GTNet myGTNet = context.getMyGTNet();
      if (myGTNet != null) {
        for (GTNetExchangeKindType kind : requestedKinds) {
          updateMyEntityForAccept(myGTNet, kind);
        }
        saveRemoteGTNet(myGTNet);
      }
    } else if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_REJECTED_S) {
      for (GTNetExchangeKindType kind : requestedKinds) {
        updateEntityForReject(remoteGTNet, kind);
      }
      saveRemoteGTNet(remoteGTNet);
    }
  }

  /**
   * Updates a GTNetEntity to accept state for the specified entity kind.
   * When we accept their request, we will SEND data to them.
   */
  private void updateEntityForAccept(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(remoteGTNet, kind);
    entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    GTNetConfigEntity configEntity = entity.getOrCreateConfigEntity();
    configEntity.setExchange(configEntity.getExchange().withSend());
  }

  /**
   * Updates myGTNet's entity to reflect that this server offers the specified entity kind.
   * This ensures the serverState is correctly communicated to remote servers via MessageEnvelope.
   */
  private void updateMyEntityForAccept(GTNet myGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(myGTNet, kind);
    entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
  }

  /**
   * Updates a GTNetEntity to reject state for the specified entity kind.
   */
  private void updateEntityForReject(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(remoteGTNet, kind);
    entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
    entity.setServerState(GTNetServerStateTypes.SS_CLOSED);
  }

  /**
   * Gets or creates a GTNetEntity for the specified kind.
   */
  private GTNetEntity getOrCreateEntity(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    return remoteGTNet.getGtNetEntities().stream()
        .filter(e -> e.getEntityKind() == kind)
        .findFirst()
        .orElseGet(() -> {
          GTNetEntity newEntity = new GTNetEntity();
          newEntity.setIdGtNet(remoteGTNet.getIdGtNet());
          newEntity.setEntityKind(kind);
          newEntity.setServerState(GTNetServerStateTypes.SS_OPEN);
          remoteGTNet.getGtNetEntities().add(newEntity);
          return newEntity;
        });
  }

  /**
   * Extracts the entity kinds from the DataRequestMsg payload.
   *
   * @param context the message context
   * @return set of requested entity kinds, or empty set if payload is missing or has no entity kinds
   */
  private Set<GTNetExchangeKindType> getRequestedEntityKinds(GTNetMessageContext context) {
    if (!context.hasPayload()) {
      return Set.of();
    }
    try {
      DataRequestMsg dataRequest = context.getPayloadAs(DataRequestMsg.class);
      return dataRequest.entityKinds != null ? dataRequest.entityKinds : Set.of();
    } catch (Exception e) {
      return Set.of();
    }
  }
}
