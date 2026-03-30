package grafiosch.gtnet.handler.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.common.DataHelper;
import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.TaskDataChange;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractRequestHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.handler.ValidationResult;
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
 *   <li>Validate that payload and token are present</li>
 *   <li>Pre-process: reject if server not in list and allowServerCreation is false</li>
 *   <li>Store incoming message</li>
 *   <li>Create/update remote GTNet entry and update stored message link</li>
 *   <li>Evaluate GTNetMessageAnswer rules (if configured)</li>
 *   <li>Default to ACCEPT if no rules are defined</li>
 *   <li>On ACCEPT: generate our token, store in GTNetConfig, queue pending messages</li>
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
    return ValidationResult.ok();
  }

  @Override
  protected Optional<HandlerResult<GTNetMessage, MessageEnvelope>> preProcess(GTNetMessageContext context)
      throws Exception {
    GTNet remoteGTNet = context.getPayloadAs(GTNet.class);
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(remoteGTNet.getDomainRemoteName());

    if (existing == null && !context.getMyGTNet().isAllowServerCreation()) {
      return Optional.of(createNotInListRejectionResponse(context));
    }
    return Optional.empty();
  }

  /**
   * Overrides default storage to first create/find the remote GTNet entry so that idGtNet is never null.
   * For first handshake, context.getRemoteGTNet() is null because the sender is unknown, but the DB column
   * id_gt_net has a NOT NULL constraint.
   */
  @Override
  protected GTNetMessage storeIncomingMessage(GTNetMessageContext context) {
    // Extract the token they sent us and their GTNet entity from payload
    FirstHandshakeMsg handshakeMsg = context.getParamsAs(FirstHandshakeMsg.class);
    String theirTokenForUs = handshakeMsg.tokenThis;
    GTNet remoteGTNet = context.getPayloadAs(GTNet.class);

    // Find or create the remote GTNet entry BEFORE storing the message
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(remoteGTNet.getDomainRemoteName());
    GTNet processedRemoteGTNet = addOrUpdateRemoteGTNet(existing, remoteGTNet, theirTokenForUs);

    // Store the processed remote GTNet in context for use by applyResponseSideEffects and buildResponse
    context.setHandlerData("processedRemoteGTNet", processedRemoteGTNet);

    // Now store the message with the correct idGtNet
    GTNetMessage message = new GTNetMessage(processedRemoteGTNet.getIdGtNet(), context.getTimestamp(),
        grafiosch.gtnet.SendReceivedType.RECEIVED.getValue(), null, context.getMessageCodeValue(),
        context.getMessage(), context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());
    message.setVisibilityValue(context.getVisibility());

    return gtNetMessageJpaRepository.saveMsg(message);
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // Remote GTNet creation is now handled in storeIncomingMessage() above.
    // The processedRemoteGTNet is already stored in context handler data.
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCode responseCode,
      GTNetMessage storedRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
      GTNet processedRemoteGTNet = context.getHandlerData("processedRemoteGTNet", GTNet.class);
      if (processedRemoteGTNet == null) {
        return;
      }

      // Generate our token for them and store in GTNetConfig
      String ourTokenForThem = DataHelper.generateGUID();
      GTNetConfig gtNetConfig = gtNetConfigJpaRepositoryFull.findById(processedRemoteGTNet.getIdGtNet()).orElse(null);
      if (gtNetConfig == null) {
        throw new IllegalStateException("GTNetConfig should exist after addOrUpdateRemoteGTNet for GTNet ID: "
            + processedRemoteGTNet.getIdGtNet());
      }
      gtNetConfig.setTokenThis(ourTokenForThem);
      gtNetConfigJpaRepositoryFull.save(gtNetConfig);

      // Store the generated token for buildResponse to include in the response
      context.setHandlerData("ourTokenForThem", ourTokenForThem);

      // Queue pending future-oriented messages for the new partner
      queuePendingMessagesForNewPartner(context.getMyGTNet(), processedRemoteGTNet);
    }
  }

  /**
   * Overrides response message storage to use the processedRemoteGTNet from handler data, since
   * context.getRemoteGTNet() is null for first handshake.
   */
  @Override
  protected GTNetMessage storeResponseMessage(GTNetMessageContext context, GTNetMessageCode responseCode,
      String message, Map<String, GTNetMessageParam> params, GTNetMessage replyToMessage) {
    GTNet processedRemoteGTNet = context.getHandlerData("processedRemoteGTNet", GTNet.class);
    Integer idGtNet = processedRemoteGTNet != null ? processedRemoteGTNet.getIdGtNet() : null;

    GTNetMessage responseMsg = new GTNetMessage(idGtNet, LocalDateTime.now(),
        grafiosch.gtnet.SendReceivedType.SEND.getValue(),
        replyToMessage != null ? replyToMessage.getIdGtNetMessage() : null, responseCode.getValue(), message, params);

    return gtNetMessageJpaRepository.saveMsg(responseMsg);
  }

  @Override
  protected MessageEnvelope buildResponse(GTNetMessageContext context, GTNetMessageCode responseCode, String message,
      GTNetMessage originalRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
      String ourTokenForThem = context.getHandlerData("ourTokenForThem", String.class);
      Map<String, GTNetMessageParam> responseParams = convertPojoToParamMap(
          new FirstHandshakeMsg(ourTokenForThem != null ? ourTokenForThem : ""));
      GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, responseParams, originalRequest);
      return createResponseEnvelopeWithPayload(context, responseMsg, context.getMyGTNet());
    }
    // For rejection responses, use standard envelope without payload
    GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, buildResponseParams(context,
        responseCode), originalRequest);
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
    GTNetMessage rejectMsg = new GTNetMessage(null, LocalDateTime.now(), grafiosch.gtnet.SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S.getValue(),
        "You are not in my server list and we do not have automatic admission enabled.", null);

    MessageEnvelope response = createResponseEnvelopeWithPayload(context, rejectMsg, context.getMyGTNet());
    return new HandlerResult.ImmediateResponse<>(response);
  }

  private GTNet addOrUpdateRemoteGTNet(GTNet existing, GTNet remoteGTNet, String theirTokenForUs) {
    if (existing == null) {
      existing = remoteGTNet;
      existing.setIdGtNet(null);
      existing.setGtNetConfig(null);
      existing.getGtNetEntities().forEach(e -> {
        e.setGtNetConfigEntity(null);
        e.setIdGtNet(null);
        e.setIdGtNetEntity(null);
      });
    }
    existing = gtNetJpaRepository.save(existing);

    GTNetConfig gtNetConfig = existing.getGtNetConfig();
    if (gtNetConfig == null) {
      gtNetConfig = new GTNetConfig();
      gtNetConfig.setIdGtNet(existing.getIdGtNet());
    }
    gtNetConfig.setTokenRemote(theirTokenForUs);
    gtNetConfig.setHandshakeTimestamp(LocalDateTime.now());
    gtNetConfig = gtNetConfigJpaRepositoryFull.save(gtNetConfig);
    existing.setGtNetConfig(gtNetConfig);
    return existing;
  }

  /**
   * Queues pending future-oriented messages for a newly connected partner. Finds maintenance and discontinuation
   * messages whose effect dates are still in the future and creates GTNetMessageAttempt entries for the new remote.
   */
  private void queuePendingMessagesForNewPartner(GTNet myGTNet, GTNet newRemoteGTNet) {
    List<GTNetMessage> futureMessages = gtNetMessageJpaRepositoryFull.findBySendRecvAndMessageCodeIn(
        grafiosch.gtnet.SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    int attemptsCreated = 0;
    for (GTNetMessage message : futureMessages) {
      if (isMessageExpired(message)) {
        continue;
      }
      if (!message.getIdGtNet().equals(myGTNet.getIdGtNet())) {
        continue;
      }
      if (gtNetMessageAttemptJpaRepository.findByIdGtNetMessageAndIdGtNet(
          message.getIdGtNetMessage(), newRemoteGTNet.getIdGtNet()).isEmpty()) {
        GTNetMessageAttempt attempt = new GTNetMessageAttempt(newRemoteGTNet.getIdGtNet(),
            message.getIdGtNetMessage());
        gtNetMessageAttemptJpaRepository.save(attempt);
        attemptsCreated++;
      }
    }

    if (attemptsCreated > 0) {
      log.info("Created {} GTNetMessageAttempt entries for new partner {} after handshake",
          attemptsCreated, newRemoteGTNet.getDomainRemoteName());
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeBase.GTNET_FUTURE_MESSAGE_DELIVERY,
          TaskDataExecPriority.PRIO_NORMAL));
    }
  }

  private boolean isMessageExpired(GTNetMessage message) {
    GTNetMessageCode codeType = GNetCoreMessageCode.getMessageCodeByValue(message.getMessageCodeValue());

    if (codeType == GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C) {
      GTNetMessageParam toDateTimeParam = message.getGtNetMessageParamMap().get("toDateTime");
      if (toDateTimeParam != null && toDateTimeParam.getParamValue() != null) {
        try {
          LocalDateTime toDateTime = LocalDateTime.parse(toDateTimeParam.getParamValue());
          return toDateTime.isBefore(LocalDateTime.now());
        } catch (Exception e) {
          log.warn("Failed to parse toDateTime for message {}: {}", message.getIdGtNetMessage(), e.getMessage());
        }
      }
    } else if (codeType == GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      GTNetMessageParam closeStartDateParam = message.getGtNetMessageParamMap().get("closeStartDate");
      if (closeStartDateParam != null && closeStartDateParam.getParamValue() != null) {
        try {
          LocalDate closeStartDate = LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(closeStartDateParam.getParamValue()));
          return closeStartDate.isBefore(LocalDate.now());
        } catch (Exception e) {
          log.warn("Failed to parse closeStartDate for message {}: {}", message.getIdGtNetMessage(), e.getMessage());
        }
      }
    }
    return false;
  }
}
