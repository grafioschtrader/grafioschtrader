package grafioschtrader.gtnet.handler.impl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.handler.impl.lastprice.LastpriceQueryStrategy;
import grafioschtrader.gtnet.handler.impl.lastprice.OpenLastpriceQueryStrategy;
import grafioschtrader.gtnet.handler.impl.lastprice.PushOpenLastpriceQueryStrategy;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GTNetExchangeLogService;

/**
 * Handler for GT_NET_LASTPRICE_EXCHANGE_SEL_C requests from remote instances.
 *
 * Processes intraday price data requests and returns prices that are newer than the requester's timestamps.
 * Delegates to strategy classes based on the local server's acceptRequest mode:
 * <ul>
 *   <li>{@link PushOpenLastpriceQueryStrategy} for AC_PUSH_OPEN: Queries GTNetLastprice* tables (shared price pool)</li>
 *   <li>{@link OpenLastpriceQueryStrategy} for AC_OPEN: Queries and updates local Security/Currencypair entities</li>
 * </ul>
 *
 * @see GTNetMessageCodeType#GT_NET_LASTPRICE_EXCHANGE_SEL_C
 */
@Component
public class LastpriceExchangeHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(LastpriceExchangeHandler.class);

  @Autowired
  private PushOpenLastpriceQueryStrategy pushOpenStrategy;

  @Autowired
  private OpenLastpriceQueryStrategy openStrategy;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) throws Exception {
    // Validate request
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts lastprice requests
    Optional<GTNetEntity> lastpriceEntity = myGTNet.getEntity(GTNetExchangeKindType.LAST_PRICE);
    if (lastpriceEntity.isEmpty() || !lastpriceEntity.get().isAccepting()) {
      log.debug("Server not accepting lastprice requests");
      return new HandlerResult.ProcessingError("NOT_ACCEPTING", "This server is not accepting lastprice requests");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createEmptyResponse(context, storedRequest);
    }

    LastpriceExchangeMsg request = context.getPayloadAs(LastpriceExchangeMsg.class);
    if (request.isEmpty()) {
      return createEmptyResponse(context, storedRequest);
    }

    int totalInstruments = (request.securities != null ? request.securities.size() : 0)
        + (request.currencypairs != null ? request.currencypairs.size() : 0);

    log.debug("Received lastprice request for {} securities and {} currencypairs (total: {})",
        request.securities != null ? request.securities.size() : 0,
        request.currencypairs != null ? request.currencypairs.size() : 0,
        totalInstruments);

    // Check if request exceeds max_limit
    Short maxLimit = lastpriceEntity.get().getMaxLimit();
    if (maxLimit != null && totalInstruments > maxLimit) {
      return handleMaxLimitExceeded(context, storedRequest, totalInstruments, maxLimit);
    }

    // Get IDs of instruments we're allowed to send (combine from both repositories)
    Set<Integer> sendableIds = new HashSet<>(securityJpaRepository.findIdsWithGtNetLastpriceSend());
    sendableIds.addAll(currencypairJpaRepository.findIdsWithGtNetLastpriceSend());

    // Select strategy based on accept mode
    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    LastpriceQueryStrategy strategy = selectStrategy(acceptMode);

    // Execute queries using the selected strategy with freshness threshold
    LastpriceExchangeMsg response = new LastpriceExchangeMsg();
    response.securities = strategy.querySecurities(request.securities, sendableIds, request.minAcceptableTimestamp);
    response.currencypairs = strategy.queryCurrencypairs(request.currencypairs, sendableIds, request.minAcceptableTimestamp);

    int responseCount = response.securities.size() + response.currencypairs.size();
    log.info("Responding with {} securities and {} currencypairs to {} (mode: {})",
        response.securities.size(), response.currencypairs.size(),
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown",
        acceptMode);

    // Log exchange statistics as supplier
    if (context.getRemoteGTNet() != null) {
      gtNetExchangeLogService.logAsSupplier(context.getRemoteGTNet(), GTNetExchangeKindType.LAST_PRICE,
          totalInstruments, responseCount, responseCount);
    }

    // Store response message
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);

    // Create response envelope with payload
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, response);
    return new HandlerResult.ImmediateResponse(envelope);
  }

  /**
   * Selects the appropriate query strategy based on the accept mode.
   */
  private LastpriceQueryStrategy selectStrategy(AcceptRequestTypes acceptMode) {
    if (acceptMode == AcceptRequestTypes.AC_PUSH_OPEN) {
      return pushOpenStrategy;
    }
    // AC_OPEN or any other mode uses the open strategy
    return openStrategy;
  }

  /**
   * Creates an empty response when no instruments were requested.
   */
  private HandlerResult createEmptyResponse(GTNetMessageContext context, GTNetMessage storedRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, new LastpriceExchangeMsg());
    return new HandlerResult.ImmediateResponse(envelope);
  }

  /**
   * Handles requests that exceed the configured max_limit.
   * Increments the violation counter for the remote domain and returns an error response.
   */
  private HandlerResult handleMaxLimitExceeded(GTNetMessageContext context, GTNetMessage storedRequest,
      int requestedCount, short maxLimit) {
    log.warn("Request from {} exceeded max_limit: {} instruments requested, limit is {}",
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown",
        requestedCount, maxLimit);

    // Increment violation counter for the remote domain
    if (context.getRemoteGTNet() != null && context.getRemoteGTNet().getGtNetConfig() != null) {
      context.getRemoteGTNet().getGtNetConfig().incrementRequestViolationCount();
      saveGTNetConfig(context.getRemoteGTNet().getGtNetConfig());
    }

    // Store violation response message
    String message = String.format("Request exceeded max_limit: %d instruments requested, limit is %d",
        requestedCount, maxLimit);
    GTNetMessage responseMsg = storeResponseMessage(context,
        GTNetMessageCodeType.GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S, message, null, storedRequest);

    MessageEnvelope envelope = createResponseEnvelope(context, responseMsg);
    return new HandlerResult.ImmediateResponse(envelope);
  }
}
