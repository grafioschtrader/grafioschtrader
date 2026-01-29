package grafioschtrader.gtnet.handler.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupResponseMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;

/**
 * Handler for GT_NET_SECURITY_BATCH_LOOKUP_SEL_C requests from remote instances.
 *
 * Processes batch security metadata lookup requests by searching the local database
 * for each query in the batch and returning results grouped by query index.
 * This handler reuses the lookup logic from {@link SecurityLookupHandler} to maintain
 * consistency in search behavior and result formatting.
 *
 * @see GTNetMessageCodeType#GT_NET_SECURITY_BATCH_LOOKUP_SEL_C
 * @see SecurityLookupHandler for single security lookup
 */
@Component
public class SecurityBatchLookupHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SecurityBatchLookupHandler.class);

  @Autowired
  private SecurityLookupHandler securityLookupHandler;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) throws Exception {
    // Validate local GTNet configuration
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts security metadata requests
    Optional<GTNetEntity> metadataEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.SECURITY_METADATA.getValue());
    if (metadataEntity.isEmpty() || !metadataEntity.get().isAccepting()) {
      log.debug("Server not accepting security metadata requests");
      return createRejectedResponse("NOT_ACCEPTING", "This server is not accepting security metadata requests");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createEmptyBatchResponse(context, storedRequest);
    }

    SecurityBatchLookupMsg request = context.getPayloadAs(SecurityBatchLookupMsg.class);
    if (request == null || request.isEmpty()) {
      return createEmptyBatchResponse(context, storedRequest);
    }

    log.debug("Received batch security lookup request with {} queries", request.size());

    // Process each query in the batch
    Map<Integer, List<SecurityGtnetLookupDTO>> results = new HashMap<>();
    List<SecurityLookupMsg> queries = request.getQueries();

    for (int i = 0; i < queries.size(); i++) {
      SecurityLookupMsg query = queries.get(i);
      if (securityLookupHandler.isValidRequest(query)) {
        List<SecurityGtnetLookupDTO> queryResults = securityLookupHandler.findLocalSecurities(query, context.getRemoteGTNet());
        if (!queryResults.isEmpty()) {
          results.put(i, queryResults);
        }
      }
    }

    log.info("Batch lookup completed: {} queries, {} with results, {} total securities found",
        queries.size(), results.size(), results.values().stream().mapToInt(List::size).sum());

    // Build and return response
    return createSuccessResponse(context, storedRequest, results);
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createSuccessResponse(
      GTNetMessageContext context, GTNetMessage storedRequest, Map<Integer, List<SecurityGtnetLookupDTO>> results) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S,
        null, null, storedRequest);

    SecurityBatchLookupResponseMsg responsePayload = new SecurityBatchLookupResponseMsg(results);

    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, responsePayload);
    return new HandlerResult.ImmediateResponse<>(envelope);
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createEmptyBatchResponse(
      GTNetMessageContext context, GTNetMessage storedRequest) {
    return createSuccessResponse(context, storedRequest, new HashMap<>());
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createRejectedResponse(String errorCode, String message) {
    return new HandlerResult.ProcessingError<>(errorCode, message);
  }
}
