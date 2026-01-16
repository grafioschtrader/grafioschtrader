package grafioschtrader.task.exec;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAttempt;
import grafioschtrader.gtnet.DeliveryStatus;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.repository.GTNetConfigJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetMessageAttemptJpaRepository;
import grafioschtrader.repository.GTNetMessageJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Background task that delivers pending future-oriented GTNet messages and handles cleanup.
 *
 * <p>
 * This task handles delivery of broadcast messages that are future-oriented:
 * <ul>
 *   <li>GT_NET_MAINTENANCE_ALL_C (24) - Maintenance window announcements</li>
 *   <li>GT_NET_OPERATION_DISCONTINUED_ALL_C (25) - Server discontinuation notices</li>
 *   <li>GT_NET_MAINTENANCE_CANCEL_ALL_C (26) - Cancellation of maintenance</li>
 *   <li>GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C (27) - Cancellation of discontinuation</li>
 * </ul>
 * </p>
 *
 * <p>
 * The task performs:
 * <ul>
 *   <li>Scheduled execution every 5 hours (configurable via gt.gtnet.future.message.cron)</li>
 *   <li>Immediate execution when any of the four message types is sent</li>
 *   <li>Creates GTNetMessageAttempt entries for remotes whose handshake completed after the message</li>
 *   <li>Delivers pending messages (hasSend = false) to their targets</li>
 *   <li>Handles cancellation logic - deletes pending attempts if original not yet delivered</li>
 *   <li>Cleans up entries when message dates are in the past</li>
 * </ul>
 * </p>
 */
@Component
public class GTNetFutureMessageDeliveryTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetFutureMessageDeliveryTask.class);

  /** Message codes for future-oriented messages */
  private static final List<Byte> FUTURE_MESSAGE_CODES = List.of(
      GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue(),
      GTNetMessageCodeType.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
      GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue());

  /** Message codes for original announcements (not cancellations) */
  private static final List<Byte> ANNOUNCEMENT_MESSAGE_CODES = List.of(
      GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue());

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.GTNET_FUTURE_MESSAGE_DELIVERY;
  }

  /**
   * Scheduled method that creates the delivery task.
   * Runs at the configured cron expression (default: every 5 hours).
   */
  @Scheduled(cron = "${gt.gtnet.future.message.cron:0 0 */5 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void createDeliveryTask() {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping future message delivery");
      return;
    }
    log.info("Scheduling GTNet future message delivery task");
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping future message delivery");
      return;
    }

    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.debug("GTNet my entry ID not configured, skipping");
      return;
    }

    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElse(null);
    if (myGTNet == null) {
      log.warn("GTNet entry {} not found", myEntryId);
      return;
    }

    // Step 1: Create GTNetMessageAttempt entries for new partners
    createAttemptsForNewPartners(myGTNet);

    // Step 2: Process cancellation messages
    processCancellationMessages();

    // Step 3: Deliver pending messages
    int delivered = deliverPendingMessages(myGTNet);

    // Step 4: Cleanup expired messages
    int cleaned = cleanupExpiredMessages();

    log.info("GTNet future message delivery completed. Delivered: {}, Cleaned: {}", delivered, cleaned);
  }

  /**
   * Creates GTNetMessageAttempt entries for remote instances whose handshake completed
   * after a pending future-oriented message was created.
   */
  private void createAttemptsForNewPartners(GTNet myGTNet) {
    // Get all future-oriented messages we sent that are still valid
    List<GTNetMessage> futureMessages = gtNetMessageJpaRepository.findBySendRecvAndMessageCodeIn(
        SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    for (GTNetMessage message : futureMessages) {
      // Skip if message dates are in the past
      if (isMessageExpired(message)) {
        continue;
      }

      Date messageTimestamp = message.getTimestamp();

      // Find all GTNetConfigs (completed handshakes) that occurred after this message
      List<GTNetConfig> newPartners = gtNetConfigJpaRepository.findByHandshakeTimestampAfter(
          messageTimestamp.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());

      for (GTNetConfig config : newPartners) {
        // Skip our own entry
        if (config.getIdGtNet().equals(myGTNet.getIdGtNet())) {
          continue;
        }

        // Check if attempt already exists
        Optional<GTNetMessageAttempt> existing = gtNetMessageAttemptJpaRepository
            .findByIdGtNetMessageAndIdGtNet(message.getIdGtNetMessage(), config.getIdGtNet());

        if (existing.isEmpty()) {
          GTNetMessageAttempt attempt = new GTNetMessageAttempt(config.getIdGtNet(), message.getIdGtNetMessage());
          gtNetMessageAttemptJpaRepository.save(attempt);
          log.info("Created GTNetMessageAttempt for new partner {} for message {}",
              config.getIdGtNet(), message.getIdGtNetMessage());
        }
      }
    }
  }

  /**
   * Processes cancellation messages. For recipients who haven't received the original
   * message, both attempts are deleted. For recipients who received the original,
   * the cancellation is queued for delivery.
   */
  private void processCancellationMessages() {
    List<GTNetMessage> cancellationMessages = gtNetMessageJpaRepository.findBySendRecvAndMessageCodeIn(
        SendReceivedType.SEND.getValue(),
        List.of(GTNetMessageCodeType.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
            GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue()));

    for (GTNetMessage cancellation : cancellationMessages) {
      Integer originalMessageId = cancellation.getIdOriginalMessage();
      if (originalMessageId == null) {
        continue;
      }

      List<GTNetMessageAttempt> cancellationAttempts = gtNetMessageAttemptJpaRepository
          .findByIdGtNetMessageAndHasSendFalse(cancellation.getIdGtNetMessage());

      for (GTNetMessageAttempt cancellationAttempt : cancellationAttempts) {
        // Check if the original message was delivered to this target
        Optional<GTNetMessageAttempt> originalAttempt = gtNetMessageAttemptJpaRepository
            .findByIdGtNetMessageAndIdGtNet(originalMessageId, cancellationAttempt.getIdGtNet());

        if (originalAttempt.isPresent()) {
          if (!originalAttempt.get().isHasSend()) {
            // Original not yet delivered - delete both attempts, send neither
            gtNetMessageAttemptJpaRepository.deleteByIdGtNetMessageAndIdGtNet(
                originalMessageId, cancellationAttempt.getIdGtNet());
            gtNetMessageAttemptJpaRepository.deleteByIdGtNetMessageAndIdGtNet(
                cancellation.getIdGtNetMessage(), cancellationAttempt.getIdGtNet());
            log.info("Deleted pending original {} and cancellation {} for target {} (neither sent)",
                originalMessageId, cancellation.getIdGtNetMessage(), cancellationAttempt.getIdGtNet());
          }
          // If original was delivered (hasSend = true), keep cancellation for delivery
        }
      }
    }
  }

  /**
   * Delivers pending messages to their targets.
   *
   * @return number of messages successfully delivered
   */
  private int deliverPendingMessages(GTNet myGTNet) {
    List<GTNetMessageAttempt> pendingAttempts = gtNetMessageAttemptJpaRepository
        .findPendingFutureMessages(FUTURE_MESSAGE_CODES);

    int delivered = 0;

    // Group attempts by message for efficient processing
    Map<Integer, List<GTNetMessageAttempt>> attemptsByMessage = pendingAttempts.stream()
        .collect(Collectors.groupingBy(GTNetMessageAttempt::getIdGtNetMessage));

    for (Map.Entry<Integer, List<GTNetMessageAttempt>> entry : attemptsByMessage.entrySet()) {
      Integer messageId = entry.getKey();
      List<GTNetMessageAttempt> attempts = entry.getValue();

      Optional<GTNetMessage> messageOpt = gtNetMessageJpaRepository.findById(messageId);
      if (messageOpt.isEmpty()) {
        continue;
      }

      GTNetMessage message = messageOpt.get();

      // Skip if message is expired (only for non-cancellation messages)
      if (isAnnouncementMessage(message) && isMessageExpired(message)) {
        continue;
      }

      // Build the payload model for the message
      Object payloadModel = buildPayloadModel(message);

      int successCount = 0;
      int failCount = 0;

      for (GTNetMessageAttempt attempt : attempts) {
        Optional<GTNet> targetOpt = gtNetJpaRepository.findById(attempt.getIdGtNet());
        if (targetOpt.isEmpty()) {
          failCount++;
          continue;
        }

        GTNet targetGTNet = targetOpt.get();

        // Check if handshake is complete (tokenRemote exists)
        if (targetGTNet.getGtNetConfig() == null ||
            targetGTNet.getGtNetConfig().getTokenRemote() == null) {
          continue; // Not counted as fail - handshake may complete later
        }

        boolean success = sendMessageToTarget(myGTNet, targetGTNet, message, payloadModel);
        if (success) {
          attempt.markAsSent();
          gtNetMessageAttemptJpaRepository.save(attempt);
          delivered++;
          successCount++;
          log.info("Delivered message {} to target {}", messageId, targetGTNet.getDomainRemoteName());
        } else {
          failCount++;
        }
      }

      // Update deliveryStatus based on results
      updateMessageDeliveryStatus(message, successCount, failCount, attempts.size());
    }

    return delivered;
  }

  /**
   * Updates the deliveryStatus on a GTNetMessage based on delivery results.
   *
   * @param message      the message to update
   * @param successCount number of successful deliveries
   * @param failCount    number of failed deliveries
   * @param totalAttempts total number of attempts processed
   */
  private void updateMessageDeliveryStatus(GTNetMessage message, int successCount, int failCount, int totalAttempts) {
    DeliveryStatus currentStatus = message.getDeliveryStatus();

    if (successCount > 0 && currentStatus != DeliveryStatus.DELIVERED) {
      // At least one successful delivery
      message.setDeliveryStatus(DeliveryStatus.DELIVERED);
      gtNetMessageJpaRepository.save(message);
      log.debug("Updated message {} deliveryStatus to DELIVERED (success: {}, fail: {})",
          message.getIdGtNetMessage(), successCount, failCount);
    } else if (successCount == 0 && failCount == totalAttempts && failCount > 0) {
      // All attempts failed
      message.setDeliveryStatus(DeliveryStatus.FAILED);
      gtNetMessageJpaRepository.save(message);
      log.warn("Updated message {} deliveryStatus to FAILED (all {} attempts failed)",
          message.getIdGtNetMessage(), failCount);
    }
  }

  /**
   * Sends a message to a specific target.
   *
   * @return true if delivery was successful
   */
  private boolean sendMessageToTarget(GTNet myGTNet, GTNet targetGTNet, GTNetMessage message, Object payloadModel) {
    try {
      MessageEnvelope envelope = new MessageEnvelope(myGTNet, message);
      if (payloadModel != null) {
        envelope.payload = objectMapper.convertValue(payloadModel, JsonNode.class);
      }

      String tokenRemote = targetGTNet.getGtNetConfig().getTokenRemote();
      SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), envelope);

      if (result.isFailed()) {
        log.warn("Failed to deliver message {} to {}: httpError={}, statusCode={}, reachable={}",
            message.getIdGtNetMessage(), targetGTNet.getDomainRemoteName(),
            result.httpError(), result.httpStatusCode(), result.serverReachable());
        return false;
      }
      return result.isDelivered();
    } catch (Exception e) {
      log.warn("Failed to send message {} to {}: {}", message.getIdGtNetMessage(),
          targetGTNet.getDomainRemoteName(), e.getMessage());
      return false;
    }
  }

  /**
   * Builds the payload model for a message based on its parameters.
   * Converts message parameters to a Map that can be serialized as JSON payload.
   */
  private Object buildPayloadModel(GTNetMessage message) {
    GTNetMessageCodeType codeType = message.getMessageCode();
    GTNetModelHelper.GTNetMsgRequest msgRequest = GTNetModelHelper.getMsgClassByMessageCode(codeType);

    if (msgRequest == null || msgRequest.model == null) {
      return null;
    }

    try {
      // Convert GTNetMessageParam map to simple String map for JSON serialization
      Map<String, String> paramValues = message.getGtNetMessageParamMap().entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getParamValue()));

      // Convert the map to the typed model class using ObjectMapper
      return objectMapper.convertValue(paramValues, msgRequest.model);
    } catch (Exception e) {
      log.warn("Failed to build payload model for message {}: {}", message.getIdGtNetMessage(), e.getMessage());
      return null;
    }
  }

  /**
   * Cleans up GTNetMessageAttempt entries for messages whose dates have passed.
   *
   * @return number of entries cleaned up
   */
  private int cleanupExpiredMessages() {
    int cleaned = 0;

    // Find all announcement messages we sent
    List<GTNetMessage> announcementMessages = gtNetMessageJpaRepository.findBySendRecvAndMessageCodeIn(
        SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    for (GTNetMessage message : announcementMessages) {
      if (isMessageExpired(message)) {
        // Delete all attempts for this message
        List<GTNetMessageAttempt> attempts = gtNetMessageAttemptJpaRepository
            .findByIdGtNetMessage(message.getIdGtNetMessage());

        if (!attempts.isEmpty()) {
          gtNetMessageAttemptJpaRepository.deleteByIdGtNetMessage(message.getIdGtNetMessage());
          cleaned += attempts.size();
          log.info("Cleaned up {} attempts for expired message {}", attempts.size(), message.getIdGtNetMessage());
        }
      }
    }

    return cleaned;
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
          String value = toDateTimeParam.getParamValue();
          LocalDateTime toDateTime;
          if (value.endsWith("Z")) {
            toDateTime = LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
          } else {
            toDateTime = LocalDateTime.parse(value);
          }
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
   * Checks if a message is an announcement (not a cancellation).
   */
  private boolean isAnnouncementMessage(GTNetMessage message) {
    byte code = message.getMessageCode().getValue();
    return ANNOUNCEMENT_MESSAGE_CODES.contains(code);
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
