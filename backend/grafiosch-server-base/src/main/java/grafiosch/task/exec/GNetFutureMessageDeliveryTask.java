package grafiosch.task.exec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.DeliveryStatus;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.GTNetTimeoutHelper;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetConfigJpaRepository;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageAttemptJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Background task that delivers pending future-oriented GTNet messages and handles cleanup.
 *
 * <p>
 * This task handles delivery of broadcast messages that are future-oriented:
 * <ul>
 * <li>GT_NET_MAINTENANCE_ALL_C (24) - Maintenance window announcements</li>
 * <li>GT_NET_OPERATION_DISCONTINUED_ALL_C (25) - Server discontinuation notices</li>
 * <li>GT_NET_MAINTENANCE_CANCEL_ALL_C (26) - Cancellation of maintenance</li>
 * <li>GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C (27) - Cancellation of discontinuation</li>
 * </ul>
 * </p>
 *
 * <p>
 * The task performs:
 * <ul>
 * <li>Scheduled execution every 5 hours (configurable via gt.gtnet.future.message.cron)</li>
 * <li>Immediate execution when any of the four message types is sent</li>
 * <li>Creates GTNetMessageAttempt entries for remotes whose handshake completed after the message</li>
 * <li>Delivers pending messages (hasSend = false) to their targets</li>
 * <li>Handles cancellation logic - deletes pending attempts if original not yet delivered</li>
 * <li>Cleans up entries when message dates are in the past</li>
 * </ul>
 * </p>
 */
@Component
public class GNetFutureMessageDeliveryTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GNetFutureMessageDeliveryTask.class);

  /** Message codes for future-oriented messages */
  private static final List<Byte> FUTURE_MESSAGE_CODES = List.of(
      GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue());

  /** Message codes for original announcements (not cancellations) */
  private static final List<Byte> ANNOUNCEMENT_MESSAGE_CODES = List.of(
      GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue());

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_FUTURE_MESSAGE_DELIVERY;
  }

  /**
   * Scheduled method that creates the delivery task. Runs at the configured cron expression (default: every 5 hours).
   */
  @Scheduled(cron = "${gt.gtnet.future.message.cron:0 0 */5 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void createDeliveryTask() {
    if (!globalparametersJpaRepository.isGTNetOperational()) {
      log.debug("GTNet is disabled or has no own entry configured, skipping future message delivery");
      return;
    }
    log.info("Scheduling GTNet future message delivery task");
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetOperational()) {
      log.debug("GTNet is disabled or has no own entry configured, skipping future message delivery");
      return;
    }

    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
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

    // Step 5: Take peers out of service whose announced shutdown date has been reached
    int retired = applyAnnouncedShutdowns();

    log.info("GTNet future message delivery completed. Delivered: {}, Cleaned: {}, Out of service: {}", delivered,
        cleaned, retired);
  }

  /**
   * Puts every peer whose announced shutdown date has been reached permanently out of service.
   *
   * <p>
   * The date itself is cleared in the same step. It has served its purpose — the terminal status carries the fact from
   * here on — and leaving it would make an administrator who resets the status by hand be overruled at the next run.
   * All entity kinds of the peer are closed as well, so a supplier query that only looks at the per-kind state drops it
   * too.
   * </p>
   *
   * @return the number of peers taken out of service in this run
   */
  private int applyAnnouncedShutdowns() {
    List<GTNet> due = gtNetJpaRepository.findByCloseStartDateLessThanEqual(LocalDate.now());
    int retired = 0;
    for (GTNet peer : due) {
      peer.setCloseStartDate(null);
      if (!peer.isOutOfService()) {
        peer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OUT_OF_SERVICE);
        peer.getGtNetEntities().forEach(entity -> {
          entity.setServerState(GTNetServerStateTypes.SS_CLOSED);
          entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
        });
        retired++;
        log.info("Peer {} reached its announced shutdown date and is now out of service", peer.getDomainRemoteName());
      }
      gtNetJpaRepository.save(peer);
    }
    return retired;
  }

  /**
   * Creates GTNetMessageAttempt entries for remote instances whose handshake completed after a pending future-oriented
   * message was created.
   */
  private void createAttemptsForNewPartners(GTNet myGTNet) {
    // Get all future-oriented messages we sent that are still valid
    List<GTNetMessage> futureMessages = gtNetMessageJpaRepository
        .findBySendRecvAndMessageCodeIn(SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

    for (GTNetMessage message : futureMessages) {
      // Skip if message dates are in the past
      if (isMessageExpired(message)) {
        continue;
      }

      LocalDateTime messageTimestamp = message.getTimestamp();

      // Find all GTNetConfigs (completed handshakes) that occurred after this message
      List<GTNetConfig> newPartners = gtNetConfigJpaRepository.findByHandshakeTimestampAfter(messageTimestamp);

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
          log.info("Created GTNetMessageAttempt for new partner {} for message {}", config.getIdGtNet(),
              message.getIdGtNetMessage());
        }
      }
    }
  }

  /**
   * Processes cancellation messages. For recipients who haven't received the original message, both attempts are
   * deleted. For recipients who received the original, the cancellation is queued for delivery.
   */
  private void processCancellationMessages() {
    List<GTNetMessage> cancellationMessages = gtNetMessageJpaRepository.findBySendRecvAndMessageCodeIn(
        SendReceivedType.SEND.getValue(), List.of(GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
            GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue()));

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
            gtNetMessageAttemptJpaRepository.deleteByIdGtNetMessageAndIdGtNet(originalMessageId,
                cancellationAttempt.getIdGtNet());
            gtNetMessageAttemptJpaRepository.deleteByIdGtNetMessageAndIdGtNet(cancellation.getIdGtNetMessage(),
                cancellationAttempt.getIdGtNet());
            log.info("Deleted pending original {} and cancellation {} for target {} (neither sent)", originalMessageId,
                cancellation.getIdGtNetMessage(), cancellationAttempt.getIdGtNet());
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

        // A peer that has gone out of service is never contacted again; its pending attempts die with the cleanup.
        if (targetGTNet.isOutOfService()) {
          continue;
        }

        // Check if handshake is complete (tokenRemote exists)
        if (targetGTNet.getGtNetConfig() == null || targetGTNet.getGtNetConfig().getTokenRemote() == null) {
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
   * @param message       the message to update
   * @param successCount  number of successful deliveries
   * @param failCount     number of failed deliveries
   * @param totalAttempts total number of attempts processed
   */
  private void updateMessageDeliveryStatus(GTNetMessage message, int successCount, int failCount, int totalAttempts) {
    DeliveryStatus currentStatus = message.getDeliveryStatus();

    if (successCount > 0 && currentStatus != DeliveryStatus.DELIVERED) {
      // At least one successful delivery
      message.setDeliveryStatus(DeliveryStatus.DELIVERED);
      gtNetMessageJpaRepository.save(message);
      log.debug("Updated message {} deliveryStatus to DELIVERED (success: {}, fail: {})", message.getIdGtNetMessage(),
          successCount, failCount);
    } else if (successCount == 0 && failCount == totalAttempts && failCount > 0) {
      // All attempts failed
      message.setDeliveryStatus(DeliveryStatus.FAILED);
      gtNetMessageJpaRepository.save(message);
      log.warn("Updated message {} deliveryStatus to FAILED (all {} attempts failed)", message.getIdGtNetMessage(),
          failCount);
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
      SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), envelope,
          GTNetTimeoutHelper.resolveTimeout(targetGTNet, globalparametersJpaRepository));

      if (result.isFailed()) {
        log.warn("Failed to deliver message {} to {}: httpError={}, statusCode={}, reachable={}, errorMsg={}",
            message.getIdGtNetMessage(), targetGTNet.getDomainRemoteName(), result.httpError(), result.httpStatusCode(),
            result.serverReachable(), result.errorMessage());
        return false;
      }

      if (!result.isAccepted()) {
        // The bytes may well have arrived: the HTTP status is 200 for every protocol outcome, so a refusal shows only
        // in the answering code. Counting one as sent would retire an attempt the peer never processed.
        log.warn("Message {} to {} not accepted: response was null, invalid, or an error ({})",
            message.getIdGtNetMessage(), targetGTNet.getDomainRemoteName(),
            result.response() != null ? result.response().errorMsgCode : null);
        return false;
      }

      return true;
    } catch (Exception e) {
      log.warn("Failed to send message {} to {}: {}", message.getIdGtNetMessage(), targetGTNet.getDomainRemoteName(),
          e.getMessage());
      return false;
    }
  }

  /**
   * Builds the payload model for a message based on its parameters. Converts message parameters to a Map that can be
   * serialized as JSON payload.
   */
  private Object buildPayloadModel(GTNetMessage message) {
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(message.getMessageCodeValue());

    if (descriptor == null || descriptor.model() == null) {
      return null;
    }

    try {
      // Convert GTNetMessageParam map to simple String map for JSON serialization
      Map<String, String> paramValues = message.getGtNetMessageParamMap().entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getParamValue()));

      // Convert the map to the typed model class using ObjectMapper
      return objectMapper.convertValue(paramValues, descriptor.model());
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
    List<GTNetMessage> announcementMessages = gtNetMessageJpaRepository
        .findBySendRecvAndMessageCodeIn(SendReceivedType.SEND.getValue(), ANNOUNCEMENT_MESSAGE_CODES);

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
   *
   * @param message the sent announcement to test
   * @return true when the window has ended or the shutdown date has passed
   */
  private boolean isMessageExpired(GTNetMessage message) {
    return MessageParamDateParser.isAnnouncementExpired(message);
  }

  /**
   * Checks if a message is an announcement (not a cancellation).
   */
  private boolean isAnnouncementMessage(GTNetMessage message) {
    byte code = message.getMessageCodeValue();
    return ANNOUNCEMENT_MESSAGE_CODES.contains(code);
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
