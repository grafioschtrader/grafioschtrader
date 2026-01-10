package grafioschtrader.gtnet.handler.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
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
      log.warn("No remote GTNet found for accept response from {}", context.getSourceDomain());
      return;
    }

    // Get entity kinds from the original request (which this is a response to)
    Set<GTNetExchangeKindType> acceptedKinds = getAcceptedEntityKinds(context);

    if (acceptedKinds.isEmpty()) {
      log.warn("No entity kinds found in original request for accept from {}", context.getSourceDomain());
      return;
    }

    for (GTNetExchangeKindType kind : acceptedKinds) {
      updateEntityForReceive(remoteGTNet, kind);
    }

    saveRemoteGTNet(remoteGTNet);
    log.info("Created GTNetConfigEntity with RECEIVE capability for {} entity kinds from {}",
        acceptedKinds.size(), context.getSourceDomain());
  }

  /**
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind.
   * When they accept our request, we will RECEIVE data from them.
   */
  private void updateEntityForReceive(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntity(kind);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    entity.getOrCreateConfigEntity(); // Creates config entity with exchange=true
  }

  /**
   * Extracts the entity kinds from the ORIGINAL request message that we sent.
   * The accept message is a response to our GT_NET_DATA_REQUEST_SEL_RR_C which contained the entityKinds.
   * We use replyToSourceId to find our original request.
   */
  private Set<GTNetExchangeKindType> getAcceptedEntityKinds(GTNetMessageContext context) {
    // The replyToSourceId points to our original request message
    Integer originalRequestId = context.getReplyToSourceId();
    if (originalRequestId == null) {
      log.warn("No replyToSourceId in accept response, cannot find original request");
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    // Look up our original request to get the entityKinds we requested
    GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(originalRequestId).orElse(null);
    if (originalRequest == null) {
      log.warn("Original request message {} not found", originalRequestId);
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    return parseEntityKindsFromParams(originalRequest.getGtNetMessageParamMap());
  }

  /**
   * Parses entity kinds from a parameter map.
   */
  private Set<GTNetExchangeKindType> parseEntityKindsFromParams(Map<String, GTNetMessageParam> paramMap) {
    if (paramMap == null) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }
    GTNetMessageParam param = paramMap.get("entityKinds");
    if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    return Arrays.stream(param.getParamValue().split(","))
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
