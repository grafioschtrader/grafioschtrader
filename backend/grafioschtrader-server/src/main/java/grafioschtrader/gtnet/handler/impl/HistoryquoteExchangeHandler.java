package grafioschtrader.gtnet.handler.impl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.handler.impl.historyquote.HistoryquoteQueryStrategy;
import grafioschtrader.gtnet.handler.impl.historyquote.OpenHistoryquoteQueryStrategy;
import grafioschtrader.gtnet.handler.impl.historyquote.PushOpenHistoryquoteQueryStrategy;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GTNetExchangeLogService;

/**
 * Handler for GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C requests from remote instances.
 *
 * Processes historical price data requests and returns quotes within the requested date ranges.
 * Delegates to strategy classes based on the local server's acceptRequest mode:
 * <ul>
 *   <li>{@link PushOpenHistoryquoteQueryStrategy} for AC_PUSH_OPEN: Queries GTNetHistoryquote for foreign
 *       instruments and local historyquote for local instruments</li>
 *   <li>{@link OpenHistoryquoteQueryStrategy} for AC_OPEN: Queries local historyquote table only</li>
 * </ul>
 *
 * @see GTNetMessageCodeType#GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C
 */
@Component
public class HistoryquoteExchangeHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(HistoryquoteExchangeHandler.class);

  @Autowired
  private PushOpenHistoryquoteQueryStrategy pushOpenStrategy;

  @Autowired
  private OpenHistoryquoteQueryStrategy openStrategy;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, grafiosch.gtnet.m2m.model.MessageEnvelope> handle(GTNetMessageContext context) throws Exception {
    // Validate request
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts historyquote requests
    Optional<GTNetEntity> historyEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.HISTORICAL_PRICES.getValue());
    if (historyEntity.isEmpty() || !historyEntity.get().isAccepting()) {
      log.debug("Server not accepting historyquote requests");
      return new HandlerResult.ProcessingError<>("NOT_ACCEPTING", "This server is not accepting historyquote requests");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createEmptyResponse(context, storedRequest);
    }

    HistoryquoteExchangeMsg request = context.getPayloadAs(HistoryquoteExchangeMsg.class);
    if (request.isEmpty()) {
      return createEmptyResponse(context, storedRequest);
    }

    int totalInstruments = request.getTotalInstrumentCount();

    log.debug("Received historyquote request for {} securities and {} currencypairs (total: {})",
        request.securities != null ? request.securities.size() : 0,
        request.currencypairs != null ? request.currencypairs.size() : 0,
        totalInstruments);

    // Check if request exceeds max_limit
    Short maxLimit = historyEntity.get().getMaxLimit();
    if (maxLimit != null && totalInstruments > maxLimit) {
      return handleMaxLimitExceeded(context, storedRequest, totalInstruments, maxLimit);
    }

    // Get IDs of instruments we're allowed to send (combine from both repositories)
    Set<Integer> sendableIds = new HashSet<>(securityJpaRepository.findIdsWithGtNetHistoricalSend());
    sendableIds.addAll(currencypairJpaRepository.findIdsWithGtNetHistoricalSend());

    // Select strategy based on accept mode
    AcceptRequestTypes acceptMode = historyEntity.get().getAcceptRequest();
    HistoryquoteQueryStrategy strategy = selectStrategy(acceptMode);

    // Execute queries using the selected strategy
    HistoryquoteExchangeMsg response = new HistoryquoteExchangeMsg();
    response.securities = strategy.querySecurities(request.securities, sendableIds);
    response.currencypairs = strategy.queryCurrencypairs(request.currencypairs, sendableIds);

    int responseRecordCount = response.getTotalRecordCount();
    log.info("Responding with {} records for {} securities and {} currencypairs to {} (mode: {})",
        responseRecordCount,
        response.securities.size(), response.currencypairs.size(),
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown",
        acceptMode);

    // Log exchange statistics as supplier
    if (context.getRemoteGTNet() != null) {
      gtNetExchangeLogService.logAsSupplier(context.getRemoteGTNet(), GTNetExchangeKindType.HISTORICAL_PRICES,
          totalInstruments, response.getTotalInstrumentCount(), responseRecordCount);
    }

    // Store response message
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);

    // Create response envelope with payload
    return new HandlerResult.ImmediateResponse<>(createResponseEnvelopeWithPayload(context, responseMsg, response));
  }

  /**
   * Selects the appropriate query strategy based on the accept mode.
   */
  private HistoryquoteQueryStrategy selectStrategy(AcceptRequestTypes acceptMode) {
    if (acceptMode == AcceptRequestTypes.AC_PUSH_OPEN) {
      return pushOpenStrategy;
    }
    // AC_OPEN or any other mode uses the open strategy
    return openStrategy;
  }

  /**
   * Creates an empty response when no instruments were requested.
   */
  private HandlerResult<GTNetMessage, grafiosch.gtnet.m2m.model.MessageEnvelope> createEmptyResponse(GTNetMessageContext context, GTNetMessage storedRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);
    return new HandlerResult.ImmediateResponse<>(
        createResponseEnvelopeWithPayload(context, responseMsg, new HistoryquoteExchangeMsg()));
  }

  /**
   * Handles requests that exceed the configured max_limit.
   * Increments the violation counter for the remote domain and returns an error response.
   */
  private HandlerResult<GTNetMessage, grafiosch.gtnet.m2m.model.MessageEnvelope> handleMaxLimitExceeded(GTNetMessageContext context, GTNetMessage storedRequest,
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
        GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S, message, null, storedRequest);

    return new HandlerResult.ImmediateResponse<>(createResponseEnvelope(context, responseMsg));
  }
}
