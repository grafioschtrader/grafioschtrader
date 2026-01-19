package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.gtnet.DeliveryStatus;
import grafioschtrader.gtnet.GTNetMessageCodeType;

public class GTNetMessageJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessage>
    implements GTNetMessageJpaRepositoryCustom {

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Override
  public GTNetMessage saveMsg(GTNetMessage gtNetMessage) {
    gtNetMessage.checkAndUpdateSomeValues();
    return gtNetMessageJpaRepository.save(gtNetMessage);
  }

  @Override
  public GTNetMessage saveOnlyAttributes(GTNetMessage entity, GTNetMessage existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return gtNetMessageJpaRepository.saveMsg(entity);
  }

  @Override
  public void computeCanDeleteFlags(List<GTNetMessage> messages, Set<Integer> outgoingPendingIds,
      Set<Integer> incomingPendingIds) {
    LocalDateTime now = LocalDateTime.now();
    for (GTNetMessage msg : messages) {
      msg.setCanDelete(canDeleteMessage(msg, outgoingPendingIds, incomingPendingIds, now));
    }
  }

  /**
   * Determines if a message can be deleted based on the deletion rules.
   *
   * @param msg                the message to check
   * @param outgoingPendingIds set of outgoing message IDs awaiting responses
   * @param incomingPendingIds set of incoming message IDs awaiting responses
   * @param now                current date/time for comparison
   * @return true if the message can be deleted
   */
  private boolean canDeleteMessage(GTNetMessage msg, Set<Integer> outgoingPendingIds,
      Set<Integer> incomingPendingIds, LocalDateTime now) {
    // Response messages (replyTo set) are cascade-deleted - don't show checkbox
    if (msg.getReplyTo() != null) {
      return false;
    }

    GTNetMessageCodeType messageCode = msg.getMessageCode();

    // Messages with FAILED delivery status are always deletable
    if (msg.getDeliveryStatus() == DeliveryStatus.FAILED) {
      return true;
    }

    // GT_NET_OFFLINE_ALL_C (20) and GT_NET_ONLINE_ALL_C (21): always deletable
    if (messageCode == GTNetMessageCodeType.GT_NET_OFFLINE_ALL_C
        || messageCode == GTNetMessageCodeType.GT_NET_ONLINE_ALL_C) {
      return true;
    }

    // Messages awaiting reply (_RR_ codes in pending maps): NOT deletable
    if (messageCode != null && messageCode.isRequestRequiringResponse()) {
      if (outgoingPendingIds.contains(msg.getIdGtNetMessage())
          || incomingPendingIds.contains(msg.getIdGtNetMessage())) {
        return false;
      }
    }

    // GT_NET_MAINTENANCE_ALL_C (24): deletable if fromDateTime is in the past
    if (messageCode == GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C) {
      LocalDateTime fromDateTime = getDateTimeParam(msg, "fromDateTime");
      return fromDateTime != null && fromDateTime.isBefore(now);
    }

    // GT_NET_OPERATION_DISCONTINUED_ALL_C (25): deletable if closeStartDate is in the past
    if (messageCode == GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      LocalDateTime closeStartDate = getDateTimeParam(msg, "closeStartDate");
      return closeStartDate != null && closeStartDate.isBefore(now);
    }

    // Default: deletable
    return true;
  }

  /**
   * Extracts a LocalDateTime parameter from the message's parameter map.
   */
  private LocalDateTime getDateTimeParam(GTNetMessage msg, String paramName) {
    if (msg.getGtNetMessageParamMap() == null) {
      return null;
    }
    GTNetMessageParam param = msg.getGtNetMessageParamMap().get(paramName);
    if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
      return null;
    }
    try {
      String value = param.getParamValue();
      if (value.endsWith("Z")) {
        return LocalDateTime.parse(value.substring(0, value.length() - 1));
      }
      return LocalDateTime.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void deleteBatch(List<Integer> idGtNetMessageList, Set<Integer> outgoingPendingIds,
      Set<Integer> incomingPendingIds) {
    LocalDateTime now = LocalDateTime.now();
    List<GTNetMessage> messagesToDelete = new ArrayList<>();

    // Validate all messages are deletable before deleting any
    for (Integer id : idGtNetMessageList) {
      GTNetMessage msg = gtNetMessageJpaRepository.findById(id).orElse(null);
      if (msg == null) {
        continue;
      }
      if (!canDeleteMessage(msg, outgoingPendingIds, incomingPendingIds, now)) {
        throw new DataViolationException("idGtNetMessage", "gt.gtnet.message.cannot.delete",
            new Object[] { id });
      }
      messagesToDelete.add(msg);
    }

    // Delete messages and their cascade responses
    for (GTNetMessage msg : messagesToDelete) {
      // First delete any responses (messages with replyTo pointing to this message)
      List<GTNetMessage> responses = gtNetMessageJpaRepository.findByReplyTo(msg.getIdGtNetMessage());
      for (GTNetMessage response : responses) {
        gtNetMessageJpaRepository.delete(response);
      }
      // Then delete the message itself
      gtNetMessageJpaRepository.delete(msg);
    }
  }

}
