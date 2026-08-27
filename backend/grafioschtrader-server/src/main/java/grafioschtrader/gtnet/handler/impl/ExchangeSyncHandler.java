package grafioschtrader.gtnet.handler.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg.ExchangeSyncItem;
import grafioschtrader.service.GTNetExchangeSyncService;

/**
 * Handler for GT_NET_EXCHANGE_SYNC_SEL_RR_C requests from remote instances.
 *
 * Processes exchange configuration sync requests for bidirectional sharing of GTNetExchange settings. When a remote
 * server sends its changed GTNetExchange configurations, this handler:
 * <ol>
 * <li>Updates local GTNetSupplierDetail entries based on received items</li>
 * <li>Returns this server's changed items for bidirectional sync</li>
 * </ol>
 *
 * @see GTNetMessageCodeType#GT_NET_EXCHANGE_SYNC_SEL_RR_C
 * @see GTNetExchangeSyncService for the core sync logic
 */
@Component
public class ExchangeSyncHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(ExchangeSyncHandler.class);

  @Autowired
  private GTNetExchangeSyncService exchangeSyncService;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) throws Exception {
    // Validate request
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // An exchange sync both serves our whole exchange inventory and writes the peer's supplier details, yet it used to
    // check nothing beyond the existence of a local entry. One sync covers every kind at once, so a grant for any
    // syncable kind admits it - refusing a peer that holds one of two grants would end an exchange both sides agreed
    // to.
    if (!hasAnyExchangeGrant(context)) {
      log.debug("No accepted data exchange with this peer, refusing exchange sync");
      return noGrantResult(null);
    }

    // Store incoming message for logging/audit trail
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createEmptyResponse(context, storedRequest);
    }

    ExchangeSyncMsg request = context.getPayloadAs(ExchangeSyncMsg.class);

    // The item list writes one supplier-detail row per element and was unbounded. It is capped against the same
    // maxLimit the price handlers use, taken from whichever syncable entity is configured with the smallest bound.
    Short maxLimit = smallestSyncableMaxLimit(myGTNet);
    if (maxLimit != null && request.getItemCount() > maxLimit) {
      log.warn("Exchange sync from {} exceeded max_limit: {} items sent, limit is {}",
          context.getRemoteGTNet() != null ? context.getRemoteGTNet().getIdGtNet() : null, request.getItemCount(),
          maxLimit);
      recordLimitViolation(context);
      return new HandlerResult.ProcessingError<>("BATCH_LIMIT_EXCEEDED", String
          .format("Exchange sync exceeded max_limit: %d items sent, limit is %d", request.getItemCount(), maxLimit));
    }

    // Process received items - update local GTNetSupplierDetail
    if (!request.isEmpty() && context.getRemoteGTNet() != null) {
      exchangeSyncService.updateSupplierDetails(context.getRemoteGTNet(), request.items);
      log.info("Updated supplier details from {}: {} items", context.getRemoteGTNet().getDomainRemoteName(),
          request.getItemCount());
    }

    // Build response with our changed items since the requester's timestamp
    LocalDateTime sinceTimestamp = request.sinceTimestamp != null ? request.sinceTimestamp
        : LocalDateTime.of(1970, 1, 1, 0, 0);
    List<ExchangeSyncItem> ourItems = exchangeSyncService.getChangedExchangeItems(sinceTimestamp);
    ExchangeSyncMsg response = ExchangeSyncMsg.forRequest(sinceTimestamp, ourItems);

    log.debug("Responding with {} exchange items to {}", ourItems.size(),
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Store and return response
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_RESPONSE_S, null,
        null, storedRequest);
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, response);
    return new HandlerResult.ImmediateResponse<>(envelope);
  }

  /**
   * The tightest bound this instance publishes for any syncable kind.
   *
   * <p>
   * A sync request is not per kind, so there is no single entity to read the limit from. Taking the smallest keeps the
   * bound consistent with what a peer is told it may send for the most restricted kind.
   * </p>
   *
   * @param myGTNet the local GTNet entry
   * @return the smallest configured maxLimit, or null when no syncable entity is configured
   */
  private Short smallestSyncableMaxLimit(GTNet myGTNet) {
    return Arrays.stream(GTNetExchangeKindType.values()).filter(GTNetExchangeKindType::isSyncable)
        .map(kind -> myGTNet.getEntityByKind(kind.getValue()).map(GTNetEntity::getMaxLimit).orElse(null))
        .filter(limit -> limit != null).min(Short::compareTo).orElse(null);
  }

  /**
   * Creates an empty response when the request has no payload.
   */
  private HandlerResult<GTNetMessage, MessageEnvelope> createEmptyResponse(GTNetMessageContext context,
      GTNetMessage storedRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_RESPONSE_S, null,
        null, storedRequest);
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, new ExchangeSyncMsg());
    return new HandlerResult.ImmediateResponse<>(envelope);
  }
}
