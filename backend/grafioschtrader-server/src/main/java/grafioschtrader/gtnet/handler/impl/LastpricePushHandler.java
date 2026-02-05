package grafioschtrader.gtnet.handler.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.service.GTNetExchangeLogService;
import grafioschtrader.service.GTNetLastpricePoolService;

/**
 * Handler for GT_NET_LASTPRICE_PUSH_SEL_C messages from remote instances.
 *
 * Receives pushed intraday price data from AC_OPEN servers that are sharing updated prices
 * after completing their price exchange. This handler is only active on AC_PUSH_OPEN servers
 * which maintain the shared push pool.
 *
 * Processing flow:
 * <ol>
 *   <li>Validates that the local server is AC_PUSH_OPEN for lastprice</li>
 *   <li>For each pushed instrument: finds or creates GTNetInstrument entry</li>
 *   <li>Updates GTNetLastprice entry only if pushed price is newer</li>
 *   <li>Returns ACK with count of accepted updates</li>
 * </ol>
 *
 * @see GTNetMessageCodeType#GT_NET_LASTPRICE_PUSH_SEL_C
 * @see GTNetMessageCodeType#GT_NET_LASTPRICE_PUSH_ACK_S
 */
@Component
public class LastpricePushHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(LastpricePushHandler.class);

  @Autowired
  private GTNetLastpricePoolService gtNetLastpricePoolService;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) throws Exception {
    // Validate local GTNet configuration
    if (context.getMyGTNet() == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server is AC_PUSH_OPEN for lastprice (only PUSH_OPEN accepts pushes)
    Optional<GTNetEntity> lastpriceEntity = context.getMyGTNet().getEntityByKind(GTNetExchangeKindType.LAST_PRICE.getValue());
    if (lastpriceEntity.isEmpty()) {
      log.debug("Server has no lastprice entity configured");
      return new HandlerResult.ProcessingError<>("NOT_CONFIGURED", "This server is not configured for lastprice exchange");
    }

    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    if (acceptMode != AcceptRequestTypes.AC_PUSH_OPEN) {
      log.debug("Server is not AC_PUSH_OPEN (mode={}), rejecting push", acceptMode);
      return new HandlerResult.ProcessingError<>("NOT_PUSH_OPEN",
          "This server is not accepting lastprice pushes (only AC_PUSH_OPEN mode accepts pushes)");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse push payload
    if (!context.hasPayload()) {
      return createAckResponse(context, storedRequest, 0);
    }

    LastpriceExchangeMsg pushPayload = context.getPayloadAs(LastpriceExchangeMsg.class);
    if (pushPayload == null || pushPayload.isEmpty()) {
      return createAckResponse(context, storedRequest, 0);
    }

    int totalCount = pushPayload.getTotalCount();
    log.info("Received lastprice push with {} securities, {} currencypairs from {}",
        pushPayload.securities != null ? pushPayload.securities.size() : 0,
        pushPayload.currencypairs != null ? pushPayload.currencypairs.size() : 0,
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Store received data using the pool service
    int acceptedCount = 0;

    // Store securities
    if (pushPayload.securities != null && !pushPayload.securities.isEmpty()) {
      acceptedCount += gtNetLastpricePoolService.updateSecurityLastpricesFromDTO(pushPayload.securities);
    }

    // Store currency pairs
    if (pushPayload.currencypairs != null && !pushPayload.currencypairs.isEmpty()) {
      acceptedCount += gtNetLastpricePoolService.updateCurrencypairLastpricesFromDTO(pushPayload.currencypairs);
    }

    log.info("Accepted {} of {} pushed lastprice updates from {}",
        acceptedCount, totalCount,
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Log exchange statistics as supplier (we're receiving data)
    if (context.getRemoteGTNet() != null) {
      gtNetExchangeLogService.logAsSupplier(context.getRemoteGTNet(), GTNetExchangeKindType.LAST_PRICE,
          totalCount, totalCount, acceptedCount);
    }

    return createAckResponse(context, storedRequest, acceptedCount);
  }

  /**
   * Creates an ACK response with the count of accepted updates.
   */
  private HandlerResult<GTNetMessage, MessageEnvelope> createAckResponse(GTNetMessageContext context,
      GTNetMessage storedRequest, int acceptedCount) {
    LastpriceExchangeMsg ackPayload = LastpriceExchangeMsg.forPushAck(acceptedCount);

    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S,
        null, null, storedRequest);

    return new HandlerResult.ImmediateResponse<>(createResponseEnvelopeWithPayload(context, responseMsg, ackPayload));
  }
}
