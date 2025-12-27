package grafioschtrader.gtnet.handler.impl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfigEntity;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractResponseHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REQUEST_ACCEPT_S messages.
 *
 * Processes acceptance responses to our data exchange requests. The remote server has accepted our request
 * to exchange data (last prices, historical data, etc.). Updates the local GTNetConfigEntity exchange status
 * to add RECEIVE capability for the accepted entity kinds.
 */
@Component
public class DataRequestAcceptHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(DataRequestAcceptHandler.class);

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S;
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    log.info("Data request accepted by {} - message stored with id {}",
        context.getSourceDomain(), storedMessage.getIdGtNetMessage());

    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Get entity kinds from the original request (which this is a response to)
    Set<GTNetExchangeKindType> acceptedKinds = getAcceptedEntityKinds(context);

    for (GTNetExchangeKindType kind : acceptedKinds) {
      updateEntityForReceive(remoteGTNet, kind);
    }

    saveRemoteGTNet(remoteGTNet);
  }

  /**
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind.
   * When they accept our request, we will RECEIVE data from them.
   */
  private void updateEntityForReceive(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntity(kind);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);

    GTNetConfigEntity configEntity = entity.getGtNetConfigEntity();
    if (configEntity == null) {
      configEntity = new GTNetConfigEntity();
      configEntity.setIdGtNetEntity(entity.getIdGtNetEntity());
      entity.setGtNetConfigEntity(configEntity);
    }
    configEntity.setExchange(configEntity.getExchange().withReceive());
  }

  /**
   * Extracts the entity kinds from the original request message parameters.
   * The accept message is a response to a GT_NET_DATA_REQUEST_SEL_RR_C which contains entityKinds.
   */
  private Set<GTNetExchangeKindType> getAcceptedEntityKinds(GTNetMessageContext context) {
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
      byte numericValue = Byte.parseByte(value);
      return GTNetExchangeKindType.getGTNetExchangeKindType(numericValue);
    } catch (NumberFormatException e) {
      try {
        return GTNetExchangeKindType.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }
}
