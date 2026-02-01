package grafioschtrader.task.exec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.gtnet.DeliveryStatus;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageAttemptJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskTypeBase;

/**
 * Background task that delivers pending GTNet admin messages to multiple targets.
 *
 * <p>
 * This task handles delivery of admin messages (GT_NET_ADMIN_MESSAGE_SEL_C) that were created
 * via multi-select in the GTNetAdminMessagesComponent. Unlike immediate delivery, this allows
 * the UI to respond quickly while delivery happens asynchronously.
 * </p>
 *
 * <p>
 * The task performs:
 * <ul>
 *   <li>Immediate execution when triggered by submitMsgToMultiple()</li>
 *   <li>Queries pending GTNetMessageAttempt entries for admin messages</li>
 *   <li>Delivers messages via BaseDataClient.sendToMsgWithStatus()</li>
 *   <li>Marks entries as hasSend=true on successful delivery</li>
 * </ul>
 * </p>
 */
@Component
public class GTNetAdminMessageDeliveryTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetAdminMessageDeliveryTask.class);

  /** Message code for admin messages */
  private static final List<Byte> ADMIN_MESSAGE_CODES = List.of(
      GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C.getValue());

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_ADMIN_MESSAGE_DELIVERY;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping admin message delivery");
      return;
    }

    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.debug("GTNet my entry ID not configured, skipping");
      return;
    }

    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElse(null);
    if (myGTNet == null) {
      log.warn("GTNet entry {} not found", myEntryId);
      return;
    }

    // Deliver pending admin messages
    int delivered = deliverPendingMessages(myGTNet);

    log.info("GTNet admin message delivery completed. Delivered: {}", delivered);
  }

  /**
   * Delivers pending admin messages to their targets.
   *
   * @return number of messages successfully delivered
   */
  private int deliverPendingMessages(GTNet myGTNet) {
    List<GTNetMessageAttempt> pendingAttempts = gtNetMessageAttemptJpaRepository
        .findPendingFutureMessages(ADMIN_MESSAGE_CODES);

    int delivered = 0;

    // Group attempts by message for efficient processing
    Map<Integer, List<GTNetMessageAttempt>> attemptsByMessage = pendingAttempts.stream()
        .collect(Collectors.groupingBy(GTNetMessageAttempt::getIdGtNetMessage));

    for (Map.Entry<Integer, List<GTNetMessageAttempt>> entry : attemptsByMessage.entrySet()) {
      Integer messageId = entry.getKey();
      List<GTNetMessageAttempt> attempts = entry.getValue();

      Optional<GTNetMessage> messageOpt = gtNetMessageJpaRepository.findById(messageId);
      if (messageOpt.isEmpty()) {
        log.warn("Message {} not found, skipping {} attempts", messageId, attempts.size());
        continue;
      }

      GTNetMessage message = messageOpt.get();

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
          log.debug("Target {} has no completed handshake, skipping", targetGTNet.getDomainRemoteName());
          continue; // Not counted as fail - handshake may complete later
        }

        boolean success = sendMessageToTarget(myGTNet, targetGTNet, message);
        if (success) {
          attempt.markAsSent();
          gtNetMessageAttemptJpaRepository.save(attempt);
          delivered++;
          successCount++;
          log.info("Delivered admin message {} to target {}", messageId, targetGTNet.getDomainRemoteName());
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
      log.debug("Updated admin message {} deliveryStatus to DELIVERED (success: {}, fail: {})",
          message.getIdGtNetMessage(), successCount, failCount);
    } else if (successCount == 0 && failCount == totalAttempts && failCount > 0) {
      // All attempts failed
      message.setDeliveryStatus(DeliveryStatus.FAILED);
      gtNetMessageJpaRepository.save(message);
      log.warn("Updated admin message {} deliveryStatus to FAILED (all {} attempts failed)",
          message.getIdGtNetMessage(), failCount);
    }
  }

  /**
   * Sends a message to a specific target.
   *
   * @return true if delivery was successful
   */
  private boolean sendMessageToTarget(GTNet myGTNet, GTNet targetGTNet, GTNetMessage message) {
    try {
      MessageEnvelope envelope = new MessageEnvelope(myGTNet, message);

      String tokenRemote = targetGTNet.getGtNetConfig().getTokenRemote();
      SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), envelope);

      if (result.isFailed()) {
        log.warn("Failed to deliver admin message {} to {}: httpError={}, statusCode={}, reachable={}",
            message.getIdGtNetMessage(), targetGTNet.getDomainRemoteName(),
            result.httpError(), result.httpStatusCode(), result.serverReachable());
        return false;
      }
      return result.isDelivered();
    } catch (Exception e) {
      log.warn("Failed to send admin message {} to {}: {}", message.getIdGtNetMessage(),
          targetGTNet.getDomainRemoteName(), e.getMessage());
      return false;
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
