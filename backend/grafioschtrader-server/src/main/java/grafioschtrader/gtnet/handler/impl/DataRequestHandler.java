package grafioschtrader.gtnet.handler.impl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfigEntity;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetExchangeStatusTypes;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractRequestHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.ValidationResult;
import grafioschtrader.repository.GTNetEntityJpaRepository;

/**
 * Unified handler for GT_NET_DATA_REQUEST_SEL_C messages.
 *
 * Processes requests for any combination of data types via the entityKinds parameter.
 *
 * The entityKinds parameter should be a comma-separated list of entity kind values
 * (either numeric: "0,1" or names: "LAST_PRICE,HISTORICAL_PRICES").
 */
@Component
public class DataRequestHandler extends AbstractRequestHandler {

  @Autowired
  private GTNetEntityJpaRepository gtNetEntityJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    // Must be from a known remote (completed handshake)
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE",
          "Data request from unknown domain - handshake required first");
    }

    // Validate entityKinds parameter exists
    Set<GTNetExchangeKindType> requestedKinds = getRequestedEntityKinds(context);
    if (requestedKinds.isEmpty()) {
      return ValidationResult.invalid("MISSING_ENTITY_KINDS",
          "Data request must specify at least one entity kind in the entityKinds parameter");
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
    } else if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_IN_PROCESS_S) {
      // In process - no state change yet
    } else if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_REJECTED_S) {
      for (GTNetExchangeKindType kind : requestedKinds) {
        updateEntityForReject(remoteGTNet, kind);
      }
      saveRemoteGTNet(remoteGTNet);
    }
  }

  /**
   * Updates a GTNetEntity to accept state for the specified entity kind.
   */
  private void updateEntityForAccept(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(remoteGTNet, kind);
    entity.setAcceptRequest(true);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);

    // Update the corresponding GTNetConfigEntity exchange status
    GTNetConfigEntity configEntity = entity.getGtNetConfigEntity();
    if (configEntity != null) {
      configEntity.setExchange(GTNetExchangeStatusTypes.ES_BOTH);
    }
  }

  /**
   * Updates a GTNetEntity to reject state for the specified entity kind.
   */
  private void updateEntityForReject(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = getOrCreateEntity(remoteGTNet, kind);
    entity.setAcceptRequest(false);
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
   * Parses the entityKinds parameter from the message context.
   *
   * @param context the message context
   * @return set of requested entity kinds, or empty set if parameter is missing
   */
  private Set<GTNetExchangeKindType> getRequestedEntityKinds(GTNetMessageContext context) {
    String entityKindsParam = context.getParamValue("entityKinds");
    if (entityKindsParam == null || entityKindsParam.isBlank()) {
      return Set.of();
    }

    return Arrays.stream(entityKindsParam.split(","))
        .map(String::trim)
        .map(this::parseEntityKind)
        .filter(kind -> kind != null)
        .collect(Collectors.toSet());
  }

  /**
   * Parses a single entity kind value (either numeric or name).
   */
  private GTNetExchangeKindType parseEntityKind(String value) {
    try {
      // Try parsing as numeric value first
      byte numericValue = Byte.parseByte(value);
      return GTNetExchangeKindType.getGTNetExchangeKindType(numericValue);
    } catch (NumberFormatException e) {
      // Try parsing as enum name
      try {
        return GTNetExchangeKindType.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }
}
