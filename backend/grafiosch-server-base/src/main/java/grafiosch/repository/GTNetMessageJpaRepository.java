package grafiosch.repository;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNetMessage;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface GTNetMessageJpaRepository extends GTNetMessageJpaRepositoryBase,
    GTNetMessageJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetMessage> {

  /**
   * Marks a message as read by setting hasBeenRead to true.
   *
   * @param idGtNetMessage the ID of the message to mark as read
   * @return number of rows updated (0 or 1)
   */
  @Modifying
  @Query("UPDATE GTNetMessage m SET m.hasBeenRead = true WHERE m.idGtNetMessage = ?1")
  int markAsRead(Integer idGtNetMessage);

  List<GTNetMessage> findAllByOrderByIdGtNetAscTimestampDesc();

  /**
   * Finds unanswered request messages based on direction and message codes.
   *
   * Named query: GTNetMessage.findUnansweredRequests
   *
   * Returns Object[] with: [0] = id_gt_net (Integer), [1] = id_gt_net_message (Integer)
   *
   * @param sendRecv     the direction of request messages (0=SEND for outgoing, 1=RECEIVED for incoming)
   * @param messageCodes list of message codes that require a response (_RR_ codes: 1, 10, 50)
   * @return list of Object[] containing [id_gt_net, id_gt_net_message] for unanswered requests
   */
  @Query(name = "GTNetMessage.findUnansweredRequests", nativeQuery = true)
  List<Object[]> findUnansweredRequests(byte sendRecv, List<Byte> messageCodes);

  /**
   * Finds messages by direction and message codes. Used for finding future-oriented broadcast messages.
   *
   * @param sendRecv     the message direction (0=SEND, 1=RECEIVED)
   * @param messageCodes list of message codes to search for
   * @return list of matching messages
   */
  @Query("SELECT m FROM GTNetMessage m WHERE m.sendRecv = ?1 AND m.messageCode IN ?2")
  List<GTNetMessage> findBySendRecvAndMessageCodeIn(byte sendRecv, List<Byte> messageCodes);

  /**
   * Finds an open GT_NET_OPERATION_DISCONTINUED_ALL_C message if one exists.
   * An 'open' message is one that:
   * - Was sent by this instance (send_recv = 0)
   * - Has message_code = 25 (GT_NET_OPERATION_DISCONTINUED_ALL_C)
   * - Has closeStartDate in the future
   * - Has not been cancelled (no message with message_code = 27 and id_original_message pointing to it)
   *
   * Named query: GTNetMessage.findOpenDiscontinuedMessage
   *
   * @param sendMessageCode        the SEND direction value (0)
   * @param discontinuedCode       the GT_NET_OPERATION_DISCONTINUED_ALL_C value (25)
   * @param cancelCode             the GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C value (27)
   * @return the ID of the open discontinued message, or null if none exists
   */
  @Query(name = "GTNetMessage.findOpenDiscontinuedMessage", nativeQuery = true)
  Integer findOpenDiscontinuedMessage(byte sendMessageCode, byte discontinuedCode, byte cancelCode);

  /**
   * Counts messages grouped by idGtNet for lazy loading support.
   * Returns list of Object[] where [0] = idGtNet (Integer), [1] = count (Long).
   *
   * @return list of [idGtNet, count] pairs
   */
  @Query("SELECT m.idGtNet, COUNT(m) FROM GTNetMessage m GROUP BY m.idGtNet")
  List<Object[]> countMessagesGroupedByIdGtNet();

  /**
   * Finds all messages for a specific GTNet domain, ordered by timestamp descending.
   * Used for lazy loading when a row is expanded in the UI.
   *
   * @param idGtNet the GTNet domain ID
   * @return list of messages ordered by timestamp descending (newest first)
   */
  List<GTNetMessage> findByIdGtNetOrderByTimestampDesc(Integer idGtNet);

  /**
   * Finds all messages that are replies to a given message. Used for cascade deletion of response messages
   * when their parent request is deleted.
   *
   * @param replyTo the ID of the parent message
   * @return list of response messages that reply to the specified message
   */
  List<GTNetMessage> findByReplyTo(Integer replyTo);

  /**
   * Deletes reply messages that reference old GTNet messages being deleted.
   * Must be called BEFORE deleteOldMessagesByCodesAndDate to avoid FK constraint violations.
   * Uses multi-table DELETE to cascade delete associated parameters from gt_net_message_param.
   *
   * Named query: GTNetMessage.deleteRepliesToOldMessages
   *
   * @param messageCodes list of parent message codes whose replies should be deleted
   * @param beforeDate delete replies to messages with timestamp before this date
   * @return number of affected rows (may be greater than deleted messages due to params)
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteRepliesToOldMessages(List<Byte> messageCodes, Date beforeDate);

  /**
   * Deletes old GTNet messages by message codes and timestamp threshold.
   * Uses multi-table DELETE to cascade delete associated parameters from gt_net_message_param.
   * IMPORTANT: Call deleteRepliesToOldMessages first to avoid FK constraint violations on reply_to.
   *
   * Named query: GTNetMessage.deleteOldMessagesByCodesAndDate
   *
   * @param messageCodes list of message codes to delete (e.g., 60, 61 for LastPrice, 80, 81 for HistoryPrice)
   * @param beforeDate delete messages with timestamp before this date
   * @return number of affected rows (may be greater than deleted messages due to params)
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteOldMessagesByCodesAndDate(List<Byte> messageCodes, Date beforeDate);

  /**
   * Converts the count query result to a Map for efficient lookup.
   *
   * @return Map with idGtNet as key and message count as value
   */
  default Map<Integer, Integer> countMessagesByIdGtNet() {
    return countMessagesGroupedByIdGtNet().stream()
        .collect(Collectors.toMap(
            row -> (Integer) row[0],
            row -> ((Long) row[1]).intValue()
        ));
  }

  /**
   * Finds all admin messages (with specific visibility) ordered by idGtNet and timestamp.
   * Used for the admin messages tab to display messages filtered by visibility level.
   *
   * @param visibility the visibility level (0 = ALL_USERS, 1 = ADMIN_ONLY)
   * @return list of messages with the specified visibility, ordered by idGtNet ASC, timestamp DESC
   */
  List<GTNetMessage> findByVisibilityOrderByIdGtNetAscTimestampDesc(byte visibility);

  /**
   * Counts admin messages grouped by idGtNet for a specific visibility level.
   * Used for badge counts in the admin messages tab.
   * Returns list of Object[] where [0] = idGtNet (Integer), [1] = count (Long).
   *
   * @param visibility the visibility level (0 = ALL_USERS, 1 = ADMIN_ONLY)
   * @return list of [idGtNet, count] pairs for messages with the specified visibility
   */
  @Query("SELECT m.idGtNet, COUNT(m) FROM GTNetMessage m WHERE m.visibility = ?1 GROUP BY m.idGtNet")
  List<Object[]> countMessagesGroupedByIdGtNetAndVisibility(byte visibility);

  /**
   * Converts the visibility-filtered count query result to a Map for efficient lookup.
   *
   * @param visibility the visibility level (0 = ALL_USERS, 1 = ADMIN_ONLY)
   * @return Map with idGtNet as key and message count as value
   */
  default Map<Integer, Integer> countMessagesByIdGtNetAndVisibility(byte visibility) {
    return countMessagesGroupedByIdGtNetAndVisibility(visibility).stream()
        .collect(Collectors.toMap(
            row -> (Integer) row[0],
            row -> ((Long) row[1]).intValue()
        ));
  }

  /**
   * Finds a message by its ID. Used for visibility enforcement when saving replies.
   *
   * @param idGtNetMessage the message ID
   * @return the message, or null if not found
   */
  GTNetMessage findByIdGtNetMessage(Integer idGtNetMessage);
}
