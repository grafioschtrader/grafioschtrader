package grafiosch.gtnet.handler.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.common.DataHelper;
import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.TaskDataChange;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetDomainService;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.handler.AbstractRequestHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.handler.ValidationResult;
import grafiosch.gtnet.m2m.model.GTNetEntityPublicDTO;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.msg.FirstHandshakeMsg;
import grafiosch.repository.GTNetConfigJpaRepository;
import grafiosch.repository.GTNetMessageAttemptJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Handler for GT_NET_FIRST_HANDSHAKE_SEL_RR_S messages (incoming handshake requests).
 *
 * Processes the first handshake from a remote server that wants to establish a connection. The flow:
 * <ol>
 * <li>Validate that payload and token are present</li>
 * <li>Pre-process: reject if server not in list and allowServerCreation is false</li>
 * <li>Store incoming message</li>
 * <li>Create/update remote GTNet entry and update stored message link</li>
 * <li>Evaluate GTNetMessageAnswer rules (if configured)</li>
 * <li>Default to ACCEPT if no rules are defined</li>
 * <li>On ACCEPT: generate our token, store in GTNetConfig, queue pending messages</li>
 * </ol>
 *
 * When no GTNetMessageAnswer rules are configured for handshake requests, the default behavior is to accept the
 * connection. Admins can configure rejection rules via the GTNetMessageAnswer configuration dialog to control which
 * peers are allowed to connect.
 */
@Component
public class FirstHandshakeRequestHandler extends AbstractRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(FirstHandshakeRequestHandler.class);

  /** Message codes for future-oriented messages that need delivery to new partners */
  private static final List<Byte> ANNOUNCEMENT_MESSAGE_CODES = List.of(
      GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue());

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepositoryFull;

  @Autowired
  private GTNetDomainService domainService;

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepositoryFull;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    if (!context.hasPayload()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_PAYLOAD", "First handshake requires GTNet payload");
    }
    if (context.getParams() == null || context.getParams().isEmpty()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_TOKEN", "First handshake requires tokenThis parameter");
    }

    // The handshake is the one code that arrives without a validated token, so the only identity it has is the domain
    // it names. The envelope and the payload must agree on it, or a caller could hand us one domain to be measured
    // against and act on another.
    GTNetPublicDTO remotePublic;
    try {
      remotePublic = context.getPayloadAs(GTNetPublicDTO.class);
    } catch (IllegalArgumentException e) {
      return ValidationResult.invalid("ENVELOPE_INVALID", "The handshake payload cannot be read");
    }
    String canonicalDomain = domainService.canonicalize(remotePublic.getDomainRemoteName());
    if (canonicalDomain == null || !domainService.isAcceptablePeerDomain(canonicalDomain)) {
      return ValidationResult.invalid("ENVELOPE_INVALID", "The announced domain is not an acceptable peer address");
    }
    if (!domainService.isSameDomain(context.getSourceDomain(), canonicalDomain)) {
      return ValidationResult.invalid("DOMAIN_MISMATCH",
          "The domain in the envelope and the domain in the payload are not the same peer");
    }

    // An unauthenticated message may create a relationship but never replace one. Once we have issued a token to this
    // domain, the way to rotate it is the authenticated token refresh, and the way to start over is to delete the peer.
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(canonicalDomain);
    if (existing != null && existing.getGtNetConfig() != null && existing.getGtNetConfig().getTokenThis() != null) {
      return ValidationResult.invalid("HANDSHAKE_ALREADY_ESTABLISHED",
          "A handshake with this domain is already established");
    }
    return ValidationResult.ok();
  }

  /**
   * Records that this peer tried to start over, so the administrator on this side can see it and act.
   *
   * <p>
   * A peer whose own credentials are gone is in a corner: its first contact is refused here, and it cannot ask for
   * help either, because an administrative message requires the very handshake it is being refused. Until this stamp
   * existed, the refusal left nothing but a line in the server log, and nobody was told. The GTNet setup table turns
   * the stamp into a marker on the peer's row, from which the administrator allows a new handshake.
   * </p>
   *
   * <p>
   * Only the newest attempt is kept, and only one write per hour: a caller that retries in a loop is answered exactly
   * as before and costs a single row update at most once an hour. The stamp is cleared as soon as the peer has
   * handshaked again, or the administrator has allowed it to.
   * </p>
   */
  @Override
  protected void onValidationFailed(GTNetMessageContext context, ValidationResult validation) {
    if (!"HANDSHAKE_ALREADY_ESTABLISHED".equals(validation.errorCode())) {
      return;
    }
    try {
      String canonicalDomain = domainService
          .canonicalize(context.getPayloadAs(GTNetPublicDTO.class).getDomainRemoteName());
      GTNet existing = gtNetJpaRepository.findByDomainRemoteName(canonicalDomain);
      if (existing == null || existing.getGtNetConfig() == null) {
        return;
      }
      GTNetConfig gtNetConfig = existing.getGtNetConfig();
      LocalDateTime now = GTNetTime.now();
      LocalDateTime last = gtNetConfig.getReconnectRequestedTime();
      if (last != null && last.isAfter(now.minusHours(1))) {
        return;
      }
      gtNetConfig.setReconnectRequestedTime(now);
      gtNetConfigJpaRepositoryFull.save(gtNetConfig);
      log.info("Peer {} asked to handshake again but a handshake is still on record; marked for the administrator",
          existing.getIdGtNet());
    } catch (RuntimeException e) {
      // The request is being refused anyway. Failing to note it down must not turn that into a failed request.
      log.warn("Could not record the reconnect request of a refused handshake", e);
    }
  }

  @Override
  protected Optional<HandlerResult<GTNetMessage, MessageEnvelope>> preProcess(GTNetMessageContext context)
      throws Exception {
    String canonicalDomain = domainService
        .canonicalize(context.getPayloadAs(GTNetPublicDTO.class).getDomainRemoteName());
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(canonicalDomain);

    if (existing == null && !context.getMyGTNet().isAllowServerCreation()) {
      return Optional.of(createNotInListRejectionResponse(context));
    }
    return Optional.empty();
  }

  /**
   * Builds the incoming message but writes nothing yet. The caller of a first handshake is unauthenticated, so until
   * the answer is known it must leave no trace: the peer row, its configuration and its handshake timestamp are created
   * in {@link #applyResponseSideEffects} and only for an accept. Persisting first would enrol a refused caller in
   * {@code GNetFutureMessageDeliveryTask.createAttemptsForNewPartners} and leave it holding a token of ours.
   */
  @Override
  protected GTNetMessage storeIncomingMessage(GTNetMessageContext context) {
    GTNetMessage message = new GTNetMessage(null, context.getTimestamp(),
        grafiosch.gtnet.SendReceivedType.RECEIVED.getValue(), null, context.getMessageCodeValue(), context.getMessage(),
        context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());
    message.setVisibilityValue(context.getVisibility());
    return message;
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // Remote GTNet creation is now handled in storeIncomingMessage() above.
    // The processedRemoteGTNet is already stored in context handler data.
  }

  /**
   * Everything the handshake writes happens here, and only for an accept: the peer row, the token it authenticates to
   * us with, the token we authenticate to it with, the stored request, and the delivery attempts for announcements it
   * has not seen yet. Any other answer leaves the database untouched.
   */
  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCode responseCode,
      GTNetMessage storedRequest) {
    if (responseCode.getValue() != GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
      return;
    }
    GTNetPublicDTO remotePublic = context.getPayloadAs(GTNetPublicDTO.class);
    String canonicalDomain = domainService.canonicalize(remotePublic.getDomainRemoteName());
    String theirTokenForUs = context.getParamsAs(FirstHandshakeMsg.class).tokenThis;

    GTNet processedRemoteGTNet = addOrUpdateRemoteGTNet(gtNetJpaRepository.findByDomainRemoteName(canonicalDomain),
        remotePublic, canonicalDomain, theirTokenForUs);
    context.setHandlerData("processedRemoteGTNet", processedRemoteGTNet);

    storedRequest.setIdGtNet(processedRemoteGTNet.getIdGtNet());
    gtNetMessageJpaRepository.saveMsg(storedRequest);

    String ourTokenForThem = DataHelper.generateGUID();
    GTNetConfig gtNetConfig = processedRemoteGTNet.getGtNetConfig();
    if (gtNetConfig == null) {
      throw new IllegalStateException(
          "GTNetConfig should exist after addOrUpdateRemoteGTNet for GTNet ID: " + processedRemoteGTNet.getIdGtNet());
    }
    gtNetConfig.setTokenThis(ourTokenForThem);
    gtNetConfigJpaRepositoryFull.save(gtNetConfig);

    // Store the generated token for buildResponse to include in the response
    context.setHandlerData("ourTokenForThem", ourTokenForThem);

    // Queue pending future-oriented messages for the new partner
    queuePendingMessagesForNewPartner(context.getMyGTNet(), processedRemoteGTNet);
  }

  /**
   * Overrides response message storage to use the processedRemoteGTNet from handler data, since
   * context.getRemoteGTNet() is null for first handshake.
   */
  /**
   * Stores the answer only when the handshake was accepted, because only then does a peer row exist to hang it on. A
   * refusal is answered from a transient message, so a caller that was turned away leaves nothing behind.
   */
  @Override
  protected GTNetMessage storeResponseMessage(GTNetMessageContext context, GTNetMessageCode responseCode,
      String message, Map<String, GTNetMessageParam> params, GTNetMessage replyToMessage) {
    GTNet processedRemoteGTNet = context.getHandlerData("processedRemoteGTNet", GTNet.class);

    GTNetMessage responseMsg = new GTNetMessage(processedRemoteGTNet != null ? processedRemoteGTNet.getIdGtNet() : null,
        GTNetTime.now(), grafiosch.gtnet.SendReceivedType.SEND.getValue(),
        replyToMessage != null ? replyToMessage.getIdGtNetMessage() : null, responseCode.getValue(), message, params);

    return processedRemoteGTNet == null ? responseMsg : gtNetMessageJpaRepository.saveMsg(responseMsg);
  }

  @Override
  protected MessageEnvelope buildResponse(GTNetMessageContext context, GTNetMessageCode responseCode, String message,
      GTNetMessage originalRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
      String ourTokenForThem = context.getHandlerData("ourTokenForThem", String.class);
      Map<String, GTNetMessageParam> responseParams = convertPojoToParamMap(
          new FirstHandshakeMsg(ourTokenForThem != null ? ourTokenForThem : ""));
      GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, responseParams, originalRequest);
      return createResponseEnvelopeWithPayload(context, responseMsg, new GTNetPublicDTO(context.getMyGTNet()));
    }
    // For rejection responses, use standard envelope without payload
    GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message,
        buildResponseParams(context, responseCode), originalRequest);
    return createResponseEnvelope(context, responseMsg);
  }

  @Override
  protected Optional<? extends GTNetMessageCode> getDefaultResponseCode(GTNetMessageContext context) {
    return Optional.of(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S);
  }

  /**
   * Creates a rejection response when the requesting server is not in the GTNet list and allowServerCreation is false.
   * Note: We don't persist this message since there's no valid GTNet entry to associate it with.
   */
  private HandlerResult<GTNetMessage, MessageEnvelope> createNotInListRejectionResponse(GTNetMessageContext context)
      throws Exception {
    GTNetMessage rejectMsg = new GTNetMessage(null, GTNetTime.now(), grafiosch.gtnet.SendReceivedType.ANSWER.getValue(),
        null, GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S.getValue(),
        "You are not in my server list and we do not have automatic admission enabled.", null);

    MessageEnvelope response = createResponseEnvelopeWithPayload(context, rejectMsg,
        new GTNetPublicDTO(context.getMyGTNet()));
    return new HandlerResult.ImmediateResponse<>(response);
  }

  /**
   * Creates or updates the peer row from the fields the peer publishes about itself, field by field rather than by
   * adopting a deserialized entity. The distinction matters: the wire object is written by the caller, so taking it
   * whole would let a peer set {@code allowServerCreation}, {@code closeStartDate} or {@code lastModifiedTime} on its
   * own row. The domain is stored in its canonical form, so later lookups by envelope domain resolve.
   *
   * @param existing        the peer row when one already exists for the canonical domain, otherwise null
   * @param remotePublic    what the peer publishes about itself
   * @param canonicalDomain the canonical form of the peer's domain
   * @param theirTokenForUs the token this peer expects us to authenticate with
   * @return the persisted peer row, with its configuration attached
   */
  private GTNet addOrUpdateRemoteGTNet(GTNet existing, GTNetPublicDTO remotePublic, String canonicalDomain,
      String theirTokenForUs) {
    GTNet peer = existing != null ? existing : new GTNet();
    peer.setDomainRemoteName(canonicalDomain);
    peer.setTimeZone(remotePublic.getTimeZone());
    peer.setSpreadCapability(remotePublic.isSpreadCapability());
    peer.setDailyRequestLimit(remotePublic.getDailyRequestLimit());
    peer.setServerBusy(remotePublic.isServerBusy());
    applyPublishedEntities(peer, remotePublic);
    peer = gtNetJpaRepository.save(peer);

    GTNetConfig gtNetConfig = peer.getGtNetConfig();
    if (gtNetConfig == null) {
      gtNetConfig = new GTNetConfig();
      gtNetConfig.setIdGtNet(peer.getIdGtNet());
    }
    gtNetConfig.setTokenRemote(theirTokenForUs);
    gtNetConfig.setHandshakeTimestamp(GTNetTime.now());
    // The peer is back; whatever earlier attempt was noted for the administrator has been answered by this one.
    gtNetConfig.setReconnectRequestedTime(null);
    gtNetConfig = gtNetConfigJpaRepositoryFull.save(gtNetConfig);
    peer.setGtNetConfig(gtNetConfig);
    return peer;
  }

  /**
   * Copies the exchange entities the peer publishes onto its row, one kind at a time. Duplicated kinds collapse onto
   * the same local entity, and the local exchange configuration of an entity is never touched, because whether we
   * exchange with a peer is our decision and not something it announces.
   *
   * @param peer         the peer row being written
   * @param remotePublic what the peer publishes about itself
   */
  private void applyPublishedEntities(GTNet peer, GTNetPublicDTO remotePublic) {
    if (remotePublic.getGtNetEntities() == null) {
      return;
    }
    Set<Byte> seenKinds = new java.util.HashSet<>();
    for (GTNetEntityPublicDTO published : remotePublic.getGtNetEntities()) {
      if (!seenKinds.add(published.getEntityKind())) {
        continue;
      }
      GTNetEntity entity = peer.getOrCreateEntityByKind(published.getEntityKind());
      entity.setAcceptRequest(published.getAcceptRequest());
      entity.setServerState(published.getServerState());
      entity.setMaxLimit(published.getMaxLimit());
    }
  }

  /**
   * Queues pending future-oriented messages for a newly connected partner. Finds maintenance and discontinuation
   * messages whose effect dates are still in the future and creates GTNetMessageAttempt entries for the new remote.
   */
  private void queuePendingMessagesForNewPartner(GTNet myGTNet, GTNet newRemoteGTNet) {
    List<GTNetMessage> futureMessages = gtNetMessageJpaRepositoryFull
        .findBySendRecvAndMessageCodeIn(grafiosch.gtnet.SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    int attemptsCreated = 0;
    for (GTNetMessage message : futureMessages) {
      if (isMessageExpired(message)) {
        continue;
      }
      if (!message.getIdGtNet().equals(myGTNet.getIdGtNet())) {
        continue;
      }
      if (gtNetMessageAttemptJpaRepository
          .findByIdGtNetMessageAndIdGtNet(message.getIdGtNetMessage(), newRemoteGTNet.getIdGtNet()).isEmpty()) {
        GTNetMessageAttempt attempt = new GTNetMessageAttempt(newRemoteGTNet.getIdGtNet(), message.getIdGtNetMessage());
        gtNetMessageAttemptJpaRepository.save(attempt);
        attemptsCreated++;
      }
    }

    if (attemptsCreated > 0) {
      log.info("Created {} GTNetMessageAttempt entries for new partner {} after handshake", attemptsCreated,
          newRemoteGTNet.getDomainRemoteName());
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeBase.GTNET_FUTURE_MESSAGE_DELIVERY, TaskDataExecPriority.PRIO_NORMAL));
    }
  }

  private boolean isMessageExpired(GTNetMessage message) {
    return MessageParamDateParser.isAnnouncementExpired(message);
  }
}
