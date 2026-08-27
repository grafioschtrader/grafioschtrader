package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNetMessage;
import grafiosch.exceptions.DataViolationException;
import grafiosch.gtnet.DeliveryStatus;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.MessageVisibility;

public class GTNetMessageJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessage>
    implements GTNetMessageJpaRepositoryCustom {

  /** How far a reply chain is followed upwards before the walk gives up; a real conversation is far shorter. */
  private static final int MAX_THREAD_DEPTH = 100;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

  @Override
  public GTNetMessage saveMsg(GTNetMessage gtNetMessage) {
    gtNetMessage.checkAndUpdateSomeValues();
    enforceVisibilityInheritance(gtNetMessage);
    return gtNetMessageJpaRepository.save(gtNetMessage);
  }

  /**
   * Forces a reply into the visibility of the conversation it joins.
   *
   * <p>
   * The rule is one-directional: an {@code ADMIN_ONLY} thread stays {@code ADMIN_ONLY} for every message in it, while a
   * thread that is visible to all users leaves each reply its own choice. It is decided by the <b>root</b> of the
   * thread rather than by the immediate parent, so a row saved outside this chain — a reply that arrives over the wire,
   * an older row written before the rule existed — cannot open a private conversation from the middle.
   * </p>
   *
   * @param gtNetMessage the message being saved
   */
  private void enforceVisibilityInheritance(GTNetMessage gtNetMessage) {
    if (gtNetMessage.getReplyTo() == null) {
      return;
    }
    GTNetMessage root = resolveThreadRoot(gtNetMessage.getReplyTo());
    if (root != null && root.getVisibility() == MessageVisibility.ADMIN_ONLY) {
      gtNetMessage.setVisibility(MessageVisibility.ADMIN_ONLY);
    }
  }

  /**
   * Walks up a conversation to the message that started it.
   *
   * <p>
   * The depth is capped: {@code reply_to} is an ordinary column with no constraint that forbids a cycle, and a
   * corrupted one must not turn a save into an endless loop. A chain longer than the cap yields the deepest ancestor
   * reached, which is still an ancestor and therefore still safe to inherit from.
   * </p>
   *
   * @param idGtNetMessage the message a reply points at
   * @return the root of that thread, or null when the chain is broken
   */
  private GTNetMessage resolveThreadRoot(Integer idGtNetMessage) {
    GTNetMessage current = gtNetMessageJpaRepository.findByIdGtNetMessage(idGtNetMessage);
    for (int depth = 0; depth < MAX_THREAD_DEPTH; depth++) {
      if (current == null || current.getReplyTo() == null) {
        return current;
      }
      GTNetMessage parent = gtNetMessageJpaRepository.findByIdGtNetMessage(current.getReplyTo());
      if (parent == null || parent.getIdGtNetMessage().equals(current.getIdGtNetMessage())) {
        return current;
      }
      current = parent;
    }
    return current;
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
  private boolean canDeleteMessage(GTNetMessage msg, Set<Integer> outgoingPendingIds, Set<Integer> incomingPendingIds,
      LocalDateTime now) {
    // Response messages (replyTo set) are cascade-deleted - don't show checkbox
    if (msg.getReplyTo() != null) {
      return false;
    }

    // Through the protocol registry, not the core enum: that lookup resolves nothing above 54, so every application
    // code fell through to "deletable" and no pending payload request was ever protected.
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(msg.getMessageCodeValue());
    GNetCoreMessageCode coreCode = GNetCoreMessageCode.getByValue(msg.getMessageCodeValue());

    // Messages with FAILED delivery status are always deletable
    if (msg.getDeliveryStatus() == DeliveryStatus.FAILED) {
      return true;
    }

    // GT_NET_OFFLINE_ALL_C (20): always deletable
    if (coreCode == GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C) {
      return true;
    }

    // A request whose answer is still outstanding: NOT deletable
    if (descriptor != null && descriptor.blocksDeletion()) {
      if (outgoingPendingIds.contains(msg.getIdGtNetMessage())
          || incomingPendingIds.contains(msg.getIdGtNetMessage())) {
        return false;
      }
    }

    // GT_NET_MAINTENANCE_ALL_C (24): deletable if fromDateTime is in the past
    if (coreCode == GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C) {
      LocalDateTime fromDateTime = getDateTimeParam(msg, "fromDateTime");
      return fromDateTime != null && fromDateTime.isBefore(now);
    }

    // GT_NET_OPERATION_DISCONTINUED_ALL_C (25): deletable if closeStartDate is in the past
    if (coreCode == GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      LocalDateTime closeStartDate = getDateTimeParam(msg, "closeStartDate");
      return closeStartDate != null && closeStartDate.isBefore(now);
    }

    // Default: deletable
    return true;
  }

  /**
   * Extracts a LocalDateTime parameter from the message's parameter map. {@code closeStartDate} is a plain date and the
   * two producers of these parameters do not agree on a format, so the shared lenient parser is used rather than a
   * local one.
   */
  private LocalDateTime getDateTimeParam(GTNetMessage msg, String paramName) {
    return MessageParamDateParser.parseDateTime(msg.getGtNetMessageParamMap(), paramName);
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
        throw new DataViolationException("id.gtnet.message", "gt.gtnet.message.cannot.delete", new Object[] { id });
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
