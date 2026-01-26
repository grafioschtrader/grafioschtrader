package grafiosch.gtnet.handler;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.IExchangeKindType;

/**
 * Abstract base class for handling data request accept response messages.
 *
 * Provides common functionality for parsing accepted entity kinds from the original request
 * message and updating GTNetEntity states. Uses the ExchangeKindTypeRegistry for
 * application-agnostic kind resolution.
 */
public abstract class AbstractDataRequestAcceptHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(AbstractDataRequestAcceptHandler.class);

  @Autowired
  protected ExchangeKindTypeRegistry exchangeKindRegistry;

  /**
   * Extracts the entity kinds from the ORIGINAL request message that we sent.
   * The accept message is a response to our request which contained the entityKinds.
   * We use replyToSourceId to find our original request.
   *
   * @param context the message context
   * @return set of accepted entity kinds, or default syncable kinds if not found
   */
  protected Set<IExchangeKindType> getAcceptedEntityKinds(GTNetMessageContext context) {
    Integer originalRequestId = context.getReplyToSourceId();
    if (originalRequestId == null) {
      log.warn("No replyToSourceId in accept response, cannot find original request");
      return exchangeKindRegistry.getSyncableKinds();
    }

    GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(originalRequestId).orElse(null);
    if (originalRequest == null) {
      log.warn("Original request message {} not found", originalRequestId);
      return exchangeKindRegistry.getSyncableKinds();
    }

    return parseEntityKindsFromParams(originalRequest.getGtNetMessageParamMap());
  }

  /**
   * Parses entity kinds from a parameter map.
   *
   * @param paramMap the parameter map from a message
   * @return set of parsed entity kinds, or default syncable kinds if not found
   */
  protected Set<IExchangeKindType> parseEntityKindsFromParams(Map<String, GTNetMessageParam> paramMap) {
    if (paramMap == null) {
      return exchangeKindRegistry.getSyncableKinds();
    }
    GTNetMessageParam param = paramMap.get("entityKinds");
    if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
      return exchangeKindRegistry.getSyncableKinds();
    }

    Set<IExchangeKindType> kinds = Arrays.stream(param.getParamValue().split(","))
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
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind.
   * When they accept our request, we will RECEIVE data from them.
   *
   * @param remoteGTNet the remote GTNet
   * @param kind        the entity kind to update
   */
  protected void updateEntityForReceive(GTNet remoteGTNet, IExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntityByKind(kind.getValue());
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    entity.getOrCreateConfigEntity();
  }
}
