package grafiosch.gtnet.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.GTNetGrantService;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.IExchangeSyncTrigger;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetConfigJpaRepositoryBase;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepositoryBase;
import grafiosch.repository.GlobalparametersJpaRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Abstract base class providing common functionality for GTNet message handlers.
 *
 * Provides utility methods for:
 * <ul>
 * <li>Storing incoming messages</li>
 * <li>Building response envelopes</li>
 * <li>Converting between POJOs and parameter maps</li>
 * </ul>
 *
 * Subclasses should typically extend the more specialized {@link AbstractRequestHandler} or
 * {@link AbstractAnnouncementHandler} instead of this class directly.
 */
public abstract class AbstractGTNetMessageHandler implements GTNetMessageHandler {

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  protected GTNetConfigJpaRepositoryBase gtNetConfigJpaRepository;

  @Autowired
  protected GTNetMessageJpaRepositoryBase gtNetMessageJpaRepository;

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  protected GTNetGrantService grantService;

  /**
   * Present only when the application has something to synchronise after an exchange has been agreed. A second handler
   * bean for the same message code would be refused by the handler registry, so this seam is a bean rather than an
   * overridable method.
   */
  @Autowired(required = false)
  protected IExchangeSyncTrigger exchangeSyncTrigger;

  /**
   * Stores the incoming message in the database, or returns the row a previous delivery of the same message already
   * created.
   *
   * <p>
   * A redelivery is a normal event rather than an edge case: both delivery tasks re-send the same persisted
   * {@code GTNetMessage} and the envelope carries its id unchanged, so a retry after a lost response is byte-identical.
   * {@code uk_gt_net_message_source} makes a second insert a constraint violation, so the lookup comes first. Most
   * duplicates never reach a handler at all — they are answered from the stored outcome before dispatch — but a query
   * handler that is safe to re-run does reach this point, and must not insert again.
   * </p>
   *
   * <p>
   * A message that names a local one as what it answers is threaded under it. That is what makes the visibility of the
   * conversation apply to it: an inbound admin message used to be stored with {@code replyTo == null} whatever the peer
   * said it was answering, so a reply into an {@code ADMIN_ONLY} thread was stored with whatever visibility byte the
   * peer had chosen. The id is not taken on trust — the envelope validator has already resolved it against a local
   * message of this same peer, so it cannot thread under another peer's conversation.
   * </p>
   *
   * @param context the message context
   * @return the persisted GTNetMessage entity, existing or new
   */
  protected GTNetMessage storeIncomingMessage(GTNetMessageContext context) {
    Integer idGtNet = context.getRemoteGTNet() != null ? context.getRemoteGTNet().getIdGtNet() : null;

    Optional<GTNetMessage> alreadyStored = findPreviousDelivery(idGtNet, context.getIdSourceGtNetMessage());
    if (alreadyStored.isPresent()) {
      return alreadyStored.get();
    }

    GTNetMessage message = new GTNetMessage(idGtNet, context.getTimestamp(), SendReceivedType.RECEIVED.getValue(),
        context.getReplyToSourceId(), context.getMessageCodeValue(), context.getMessage(), context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());
    // The visibility the peer proposes. saveMsg overrides it when the thread it joins is ADMIN_ONLY.
    message.setVisibilityValue(context.getVisibility());

    return gtNetMessageJpaRepository.saveMsg(message);
  }

  /**
   * The row an earlier delivery of the same message created, if there is one.
   *
   * @param idGtNet              the peer the message came from, null when the peer is not known yet
   * @param idSourceGtNetMessage the message id the sender assigned
   * @return the earlier row, empty when this is the first delivery or the identity is incomplete
   */
  protected Optional<GTNetMessage> findPreviousDelivery(Integer idGtNet, Integer idSourceGtNetMessage) {
    if (idGtNet == null || idSourceGtNetMessage == null) {
      return Optional.empty();
    }
    return gtNetMessageJpaRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessage(idGtNet,
        SendReceivedType.RECEIVED.getValue(), idSourceGtNetMessage);
  }

  /**
   * Stores a response message in the database.
   *
   * @param context        the message context
   * @param responseCode   the response message code
   * @param message        optional text message
   * @param params         response parameters
   * @param replyToMessage the original request message this responds to
   * @return the persisted response GTNetMessage entity
   */
  protected GTNetMessage storeResponseMessage(GTNetMessageContext context, GTNetMessageCode responseCode,
      String message, Map<String, GTNetMessageParam> params, GTNetMessage replyToMessage) {
    Integer idGtNet = context.getRemoteGTNet() != null ? context.getRemoteGTNet().getIdGtNet() : null;

    GTNetMessage responseMsg = new GTNetMessage(idGtNet, GTNetTime.now(), SendReceivedType.SEND.getValue(),
        replyToMessage != null ? replyToMessage.getIdGtNetMessage() : null, responseCode.getValue(), message, params);

    return gtNetMessageJpaRepository.saveMsg(responseMsg);
  }

  /**
   * Creates a response envelope for sending back to the caller.
   *
   * @param context     the message context
   * @param responseMsg the response message entity
   * @return the MessageEnvelope ready for transmission
   */
  protected MessageEnvelope createResponseEnvelope(GTNetMessageContext context, GTNetMessage responseMsg) {
    return new MessageEnvelope(context.getMyGTNet(), responseMsg);
  }

  /**
   * Creates a response envelope with a JSON payload.
   *
   * @param context     the message context
   * @param responseMsg the response message entity
   * @param payload     object to serialize as JSON payload
   * @return the MessageEnvelope with payload
   */
  protected MessageEnvelope createResponseEnvelopeWithPayload(GTNetMessageContext context, GTNetMessage responseMsg,
      Object payload) {
    MessageEnvelope envelope = createResponseEnvelope(context, responseMsg);
    envelope.payload = objectMapper.convertValue(payload, JsonNode.class);
    return envelope;
  }

  /**
   * Converts a POJO to a parameter map for message storage.
   *
   * @param pojo the object to convert
   * @return map of parameter names to GTNetMessageParam values
   */
  protected Map<String, GTNetMessageParam> convertPojoToParamMap(Object pojo) {
    Map<String, String> stringMap = objectMapper.convertValue(pojo, new TypeReference<Map<String, String>>() {
    });
    Map<String, GTNetMessageParam> paramMap = new HashMap<>();
    for (Map.Entry<String, String> entry : stringMap.entrySet()) {
      paramMap.put(entry.getKey(), new GTNetMessageParam(entry.getValue()));
    }
    return paramMap;
  }

  /**
   * Updates the GTNet entity for the remote server.
   *
   * @param remoteGTNet the remote GTNet entity to update
   * @return the updated entity
   */
  protected GTNet saveRemoteGTNet(GTNet remoteGTNet) {
    return gtNetJpaRepository.save(remoteGTNet);
  }

  /**
   * Updates the GTNetConfig entity for the remote server.
   *
   * @param gtNetConfig the GTNetConfig entity to update
   * @return the updated entity
   */
  protected GTNetConfig saveGTNetConfig(GTNetConfig gtNetConfig) {
    return gtNetConfigJpaRepository.save(gtNetConfig);
  }

  /**
   * Schedules the exchange configuration synchronisation after a data exchange has been agreed.
   *
   * <p>
   * Called from both sides of the negotiation, so a freshly accepted pair holds its supplier-detail rows without
   * waiting for the next daily run. Does nothing when the application publishes no {@link IExchangeSyncTrigger}, which
   * is the case for the library stack.
   * </p>
   *
   * @param context the message context of the accepted exchange
   */
  protected void triggerExchangeSyncTask(GTNetMessageContext context) {
    if (exchangeSyncTrigger != null) {
      exchangeSyncTrigger.scheduleExchangeSync(context == null ? null : context.getRemoteGTNet());
    }
  }

  /**
   * Records that the peer sent more than the entity's {@code maxLimit} allows.
   *
   * <p>
   * The counter saturates at 99 and is what {@link #isBlockedByRequestViolations(GTNetMessageContext)} reads, so a peer
   * that keeps overshooting eventually stops being served until an administrator resets it. Call it once per refused
   * request, never per over-limit element.
   * </p>
   *
   * @param context the message context of the incoming request
   */
  protected void recordLimitViolation(GTNetMessageContext context) {
    if (context.getRemoteGTNet() == null || context.getRemoteGTNet().getGtNetConfig() == null) {
      return;
    }
    GTNetConfig gtNetConfig = context.getRemoteGTNet().getGtNetConfig();
    gtNetConfig.incrementRequestViolationCount();
    gtNetConfigJpaRepository.save(gtNetConfig);
  }

  /**
   * Whether the peer holds an accepted, unrevoked grant for this exchange kind.
   *
   * <p>
   * Callers apply this directly after their own accept-flag check. The accept flag says whether this instance serves
   * the kind at all; the grant says whether it serves it to <em>this</em> peer. Both are required, and a missing grant
   * is answered with the code's own refusal where it has one, otherwise as a {@code ProcessingError} carrying
   * {@link GTNetGrantService#NO_GRANT}, which reaches the peer as {@code GT_NET_ERROR_S}.
   * </p>
   *
   * @param context the message context of the incoming request
   * @param kind    the exchange kind being asked for
   * @return true when the peer may be served this kind
   */
  protected boolean hasExchangeGrant(GTNetMessageContext context, IExchangeKindType kind) {
    return grantService.hasGrant(context.getRemoteGTNet(), kind);
  }

  /**
   * Whether the peer holds a grant for at least one syncable kind, which is what a request covering every kind at once
   * needs.
   *
   * @param context the message context of the incoming request
   * @return true when at least one syncable kind is granted to this peer
   */
  protected boolean hasAnyExchangeGrant(GTNetMessageContext context) {
    return grantService.hasAnyGrant(context.getRemoteGTNet());
  }

  /**
   * The refusal for a peer without a grant, in the shape every handler without a dedicated refusal code uses.
   *
   * @param kind the exchange kind that was asked for, may be null for a request covering every kind
   * @return the processing error that becomes {@code GT_NET_ERROR_S} with {@link GTNetGrantService#NO_GRANT}
   */
  protected HandlerResult<GTNetMessage, MessageEnvelope> noGrantResult(IExchangeKindType kind) {
    return new HandlerResult.ProcessingError<>(GTNetGrantService.NO_GRANT,
        "No accepted data exchange for " + (kind == null ? "any syncable kind" : kind.name()));
  }

  /**
   * Reports whether the remote server has run up so many request violations that its requests are no longer served.
   *
   * A violation is recorded by {@link GTNetConfig#incrementRequestViolationCount()} whenever the remote exceeds the
   * max_limit configured for an exchange kind. The counter saturates at 99 and is compared against the global parameter
   * <code>g.max.limit.request.exceeded.count</code> (default 20). Once the threshold is reached the remote stays
   * refused until an administrator resets the counter through the GTNet configuration dialog, which is why
   * {@link GTNetConfig#requestViolationCount} is updatable.
   *
   * Callers apply this guard directly after their own accept check and answer with the limit-exceeded code of their
   * exchange kind, without incrementing the counter any further.
   *
   * @param context the message context of the incoming request
   * @return true when the remote is over its violation budget and the request must not be served
   */
  protected boolean isBlockedByRequestViolations(GTNetMessageContext context) {
    if (context.getRemoteGTNet() == null || context.getRemoteGTNet().getGtNetConfig() == null) {
      return false;
    }
    Byte violations = context.getRemoteGTNet().getGtNetConfig().getRequestViolationCount();
    return violations != null && violations >= globalparametersJpaRepository.getMaxLimitExceededCount();
  }
}
