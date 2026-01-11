package grafioschtrader.gtnet.handler.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.common.DataHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAttempt;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.handler.ValidationResult;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.repository.GTNetConfigJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetMessageAttemptJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Handler for GT_NET_FIRST_HANDSHAKE_S messages (incoming handshake requests).
 *
 * Processes the first handshake from a remote server that wants to establish a connection. The flow:
 * <ol>
 *   <li>Receive handshake request with remote's GTNet entity and their token for us</li>
 *   <li>Store or update the remote GTNet entity</li>
 *   <li>Generate our token for them to use when calling us</li>
 *   <li>Send accept response with our token</li>
 * </ol>
 *
 * The handshake always auto-accepts (no GTNetMessageAnswer rules needed) to allow initial connectivity. Further access
 * control is handled by subsequent entity/lastprice request messages.
 *
 * Note: This handler extends AbstractGTNetMessageHandler directly (not AbstractRequestHandler) because handshake has
 * special logic that always auto-accepts without using GTNetMessageAnswer rules.
 */
@Component
public class FirstHandshakeRequestHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(FirstHandshakeRequestHandler.class);

  /** Message codes for future-oriented messages that need delivery to new partners */
  private static final List<Byte> ANNOUNCEMENT_MESSAGE_CODES = List.of(
      GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue());

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) throws Exception {
    // 1. Validate the request
    ValidationResult validation = validateRequest(context);
    if (!validation.valid()) {
      return new HandlerResult.ProcessingError(validation.errorCode(), validation.message());
    }

    // 2. Extract the token they sent us (what they will use to call us)
    FirstHandshakeMsg handshakeMsg = context.getParamsAs(FirstHandshakeMsg.class);
    String theirTokenForUs = handshakeMsg.tokenThis;

    // 3. Extract their GTNet entity from payload
    GTNet remoteGTNet = context.getPayloadAs(GTNet.class);

    // 4. Check if remote server exists in our GTNet table
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(remoteGTNet.getDomainRemoteName());

    // 5. If server doesn't exist and allowServerCreation is false, reject the handshake
    if (existing == null && !context.getMyGTNet().isAllowServerCreation()) {
      return createNotInListRejectionResponse(context, remoteGTNet);
    }

    // 6. Store or update the remote GTNet entry and its config
    GTNet processedRemoteGTNet = addOrUpdateRemoteGTNet(existing, remoteGTNet, theirTokenForUs);

    // 7. Generate our token for them and store in GTNetConfig
    // Fetch GTNetConfig directly from repository to avoid JPA session state issues with the
    // read-only @OneToOne relationship (insertable=false, updatable=false)
    String ourTokenForThem = DataHelper.generateGUID();
    GTNetConfig gtNetConfig = gtNetConfigJpaRepository.findById(processedRemoteGTNet.getIdGtNet()).orElse(null);
    if (gtNetConfig == null) {
      throw new IllegalStateException("GTNetConfig should exist after addOrUpdateRemoteGTNet for GTNet ID: "
          + processedRemoteGTNet.getIdGtNet());
    }
    gtNetConfig.setTokenThis(ourTokenForThem);
    gtNetConfigJpaRepository.save(gtNetConfig);

    // 8. Store the incoming handshake message
    GTNetMessage storedRequest = storeHandshakeRequest(context, processedRemoteGTNet);

    // 9. Build and store the response message
    Map<String, GTNetMessageParam> responseParams = convertPojoToParamMap(new FirstHandshakeMsg(ourTokenForThem));
    GTNetMessage responseMsg = new GTNetMessage(processedRemoteGTNet.getIdGtNet(), new java.util.Date(),
        SendReceivedType.SEND.getValue(), storedRequest.getIdGtNetMessage(),
        GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue(), null, responseParams);
    responseMsg = gtNetMessageJpaRepository.saveMsg(responseMsg);

    // 10. Queue pending future-oriented messages for the new partner
    queuePendingMessagesForNewPartner(context.getMyGTNet(), processedRemoteGTNet);

    // 11. Return response with our GTNet info in payload
    MessageEnvelope response = createResponseEnvelopeWithPayload(context, responseMsg, context.getMyGTNet());
    return new HandlerResult.ImmediateResponse(response);
  }

  /**
   * Queues pending future-oriented messages for a newly connected partner.
   * Finds maintenance and discontinuation messages whose effect dates are still in the future
   * and creates GTNetMessageAttempt entries for the new remote.
   *
   * @param myGTNet       the local GTNet entry
   * @param newRemoteGTNet the newly connected remote GTNet entry
   */
  private void queuePendingMessagesForNewPartner(GTNet myGTNet, GTNet newRemoteGTNet) {
    // Find all future-oriented announcement messages we sent that are still valid
    List<GTNetMessage> futureMessages = gtNetMessageJpaRepository.findBySendRecvAndMessageCodeIn(
        SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    int attemptsCreated = 0;
    for (GTNetMessage message : futureMessages) {
      // Skip if message dates are in the past
      if (isMessageExpired(message)) {
        continue;
      }

      // Skip if message was not sent by us (messages under sender's own GTNet ID)
      if (!message.getIdGtNet().equals(myGTNet.getIdGtNet())) {
        continue;
      }

      // Check if attempt already exists for this target
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

      // Schedule immediate delivery task
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.GTNET_FUTURE_MESSAGE_DELIVERY,
          TaskDataExecPriority.PRIO_NORMAL));
    }
  }

  /**
   * Checks if a message is expired (its effective date has passed).
   */
  private boolean isMessageExpired(GTNetMessage message) {
    GTNetMessageCodeType codeType = message.getMessageCode();

    if (codeType == GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C) {
      // Check toDateTime
      GTNetMessageParam toDateTimeParam = message.getGtNetMessageParamMap().get("toDateTime");
      if (toDateTimeParam != null && toDateTimeParam.getParamValue() != null) {
        try {
          LocalDateTime toDateTime = LocalDateTime.parse(toDateTimeParam.getParamValue());
          return toDateTime.isBefore(LocalDateTime.now());
        } catch (Exception e) {
          log.warn("Failed to parse toDateTime for message {}: {}", message.getIdGtNetMessage(), e.getMessage());
        }
      }
    } else if (codeType == GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      // Check closeStartDate
      GTNetMessageParam closeStartDateParam = message.getGtNetMessageParamMap().get("closeStartDate");
      if (closeStartDateParam != null && closeStartDateParam.getParamValue() != null) {
        try {
          LocalDate closeStartDate = LocalDate.parse(closeStartDateParam.getParamValue());
          return closeStartDate.isBefore(LocalDate.now());
        } catch (Exception e) {
          log.warn("Failed to parse closeStartDate for message {}: {}", message.getIdGtNetMessage(), e.getMessage());
        }
      }
    }

    return false;
  }

  /**
   * Creates a rejection response when the requesting server is not in the GTNet list and allowServerCreation is false.
   * Note: We don't persist this message since there's no valid GTNet entry to associate it with.
   */
  private HandlerResult createNotInListRejectionResponse(GTNetMessageContext context, GTNet remoteGTNet)
      throws Exception {
    // Create rejection message without persisting - no valid GTNet entry exists for unknown servers
    GTNetMessage rejectMsg = new GTNetMessage(null, new java.util.Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_REJECT_S.getValue(),
        "You are not in my server list and we do not have automatic admission enabled.", null);

    MessageEnvelope response = createResponseEnvelopeWithPayload(context, rejectMsg, context.getMyGTNet());
    return new HandlerResult.ImmediateResponse(response);
  }

  private ValidationResult validateRequest(GTNetMessageContext context) {
    // Check that payload contains the remote GTNet entity
    if (!context.hasPayload()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_PAYLOAD", "First handshake requires GTNet payload");
    }

    // Check that params contain the token
    if (context.getParams() == null || context.getParams().isEmpty()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_TOKEN", "First handshake requires tokenThis parameter");
    }

    return ValidationResult.ok();
  }

  private GTNet addOrUpdateRemoteGTNet(GTNet existing, GTNet remoteGTNet, String theirTokenForUs) {
    if (existing == null) {
      existing = remoteGTNet;
      existing.setIdGtNet(null); // Ensure new entity
      existing.setGtNetConfig(null); // Clear any config from payload to avoid transient object exception
      existing.getGtNetEntities().forEach(e -> {
        e.setGtNetConfigEntity(null);
        e.setIdGtNet(null);
        e.setIdGtNetEntity(null);
      });
    }
    // Save GTNet first to get ID
    existing = gtNetJpaRepository.save(existing);

    // Create or get GTNetConfig and store their token (what we use to authenticate to them)
    GTNetConfig gtNetConfig = existing.getGtNetConfig();
    if (gtNetConfig == null) {
      gtNetConfig = new GTNetConfig();
      gtNetConfig.setIdGtNet(existing.getIdGtNet());  // Set FK manually
    }
    gtNetConfig.setTokenRemote(theirTokenForUs);
    // Set handshake timestamp for tracking when connection was established
    gtNetConfig.setHandshakeTimestamp(LocalDateTime.now());
    gtNetConfig = gtNetConfigJpaRepository.save(gtNetConfig);  // Save config separately
    existing.setGtNetConfig(gtNetConfig);  // Set reference on entity for later use
    return existing;
  }

  private GTNetMessage storeHandshakeRequest(GTNetMessageContext context, GTNet processedRemoteGTNet) {
    GTNetMessage message = new GTNetMessage(processedRemoteGTNet.getIdGtNet(), context.getTimestamp(),
        SendReceivedType.RECEIVED.getValue(), null, context.getMessageCodeValue(), context.getMessage(),
        context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());
    return gtNetMessageJpaRepository.saveMsg(message);
  }
}
