package grafiosch.repository;

import java.util.List;
import java.util.Set;

import grafiosch.entities.GTNetMessage;

public interface GTNetMessageJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessage> {

  GTNetMessage saveMsg(GTNetMessage gtNetMessage);

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses.
   * Validates that all specified messages are deletable before performing deletion.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   * @param outgoingPendingIds set of outgoing message IDs awaiting responses
   * @param incomingPendingIds set of incoming message IDs awaiting responses
   * @throws grafiosch.exceptions.DataViolationException if any message cannot be deleted
   */
  void deleteBatch(List<Integer> idGtNetMessageList, Set<Integer> outgoingPendingIds, Set<Integer> incomingPendingIds);

  /**
   * Computes the canDelete flag for each message based on deletion rules.
   * Rules:
   * - GT_NET_OFFLINE_ALL_C (20): always deletable
   * - Messages with deliveryStatus=FAILED: always deletable
   * - Messages awaiting reply (_RR_ codes with no response in pending maps): NOT deletable
   * - GT_NET_MAINTENANCE_ALL_C (24): deletable if fromDateTime is in the past
   * - GT_NET_OPERATION_DISCONTINUED_ALL_C (25): deletable if closeStartDate is in the past
   * - Response messages (replyTo set): cascade-deleted with parent, no checkbox shown
   *
   * @param messages           the list of messages to process
   * @param outgoingPendingIds set of outgoing message IDs awaiting responses
   * @param incomingPendingIds set of incoming message IDs awaiting responses
   */
  void computeCanDeleteFlags(List<GTNetMessage> messages, Set<Integer> outgoingPendingIds,
      Set<Integer> incomingPendingIds);

}
