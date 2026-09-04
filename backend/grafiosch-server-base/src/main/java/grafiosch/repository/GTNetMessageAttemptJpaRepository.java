package grafiosch.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.gtnet.model.GTNetMessageAttemptView;
import jakarta.transaction.Transactional;

/**
 * Repository for managing GTNetMessageAttempt entities that track per-target delivery status for future-oriented
 * broadcast messages.
 *
 * <p>
 * This repository provides methods for:
 * </p>
 * <ul>
 * <li>Finding pending deliveries (hasSend = false) by message or target</li>
 * <li>Finding and deleting specific message-target combinations</li>
 * <li>Querying pending future-oriented messages for the delivery task</li>
 * </ul>
 *
 * @see GTNetMessageAttempt
 */
public interface GTNetMessageAttemptJpaRepository extends JpaRepository<GTNetMessageAttempt, Integer> {

  /**
   * Finds all pending delivery attempts for a specific message. Used when sending a message to check which targets
   * still need delivery.
   *
   * @param idGtNetMessage the message ID
   * @return list of attempts where hasSend = false
   */
  List<GTNetMessageAttempt> findByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

  /**
   * Finds all pending delivery attempts targeting a specific remote. Used after handshake to prioritize deliveries to
   * newly connected partners.
   *
   * @param idGtNet the target GTNet ID
   * @return list of pending attempts for this target
   */
  List<GTNetMessageAttempt> findByIdGtNetAndHasSendFalse(Integer idGtNet);

  /**
   * Finds all delivery attempts for a specific message (both pending and completed). Used for checking delivery status
   * and cancellation processing.
   *
   * @param idGtNetMessage the message ID
   * @return list of all attempts for this message
   */
  List<GTNetMessageAttempt> findByIdGtNetMessage(Integer idGtNetMessage);

  /**
   * Finds a specific message-target delivery attempt. Used to check if an attempt already exists before creating a new
   * one.
   *
   * @param idGtNetMessage the message ID
   * @param idGtNet        the target GTNet ID
   * @return optional containing the attempt if found
   */
  Optional<GTNetMessageAttempt> findByIdGtNetMessageAndIdGtNet(Integer idGtNetMessage, Integer idGtNet);

  /**
   * Deletes all delivery attempts for a specific message-target combination. Used when processing cancellations to
   * remove pending original message deliveries.
   *
   * @param idGtNetMessage the message ID
   * @param idGtNet        the target GTNet ID
   */
  @Modifying
  @Transactional
  void deleteByIdGtNetMessageAndIdGtNet(Integer idGtNetMessage, Integer idGtNet);

  /**
   * Deletes all delivery attempts for a specific message. Used when cleaning up after a message's effective dates have
   * passed.
   *
   * @param idGtNetMessage the message ID
   */
  @Modifying
  @Transactional
  void deleteByIdGtNetMessage(Integer idGtNetMessage);

  /**
   * Finds all pending delivery attempts for future-oriented message types. Used by GTNetFutureMessageDeliveryTask to
   * find messages that need delivery.
   *
   * Named query: GTNetMessageAttempt.findPendingFutureMessages Parameters in SQL: - ?1 (messageCodes): List of byte
   * values for future-oriented message codes (24, 25, 26, 27)
   *
   * @param messageCodes list of message code bytes to filter by
   * @return list of pending attempts for future-oriented messages
   */
  @Query(nativeQuery = true)
  List<GTNetMessageAttempt> findPendingFutureMessages(List<Byte> messageCodes);

  /** Returns administrator-facing attempt rows for messages stored under one GTNet entry. */
  @Query("""
      SELECT a.idGtNetMessageAttempt, a.idGtNetMessage, m.messageCode, m.timestamp, a.idGtNet,
             target.domainRemoteName, a.attemptStatus, a.tryCount, a.lastAttemptTimestamp, a.sendTimestamp, a.lastError
        FROM GTNetMessageAttempt a
        JOIN GTNetMessage m ON m.idGtNetMessage = a.idGtNetMessage
        JOIN GTNet target ON target.idGtNet = a.idGtNet
       WHERE m.idGtNet = ?1
       ORDER BY m.timestamp DESC, target.domainRemoteName ASC
      """)
  List<Object[]> findViewRowsBySourceIdGtNet(Integer idGtNet);

  /** Counts attempts by the GTNet entry under which their source message is stored. */
  @Query("""
      SELECT m.idGtNet, COUNT(a)
        FROM GTNetMessageAttempt a
        JOIN GTNetMessage m ON m.idGtNetMessage = a.idGtNetMessage
       GROUP BY m.idGtNet
      """)
  List<Object[]> countGroupedBySourceIdGtNet();

  default List<GTNetMessageAttemptView> findViewsBySourceIdGtNet(Integer idGtNet) {
    return findViewRowsBySourceIdGtNet(idGtNet).stream()
        .map(row -> new GTNetMessageAttemptView((Integer) row[0], (Integer) row[1], ((Number) row[2]).byteValue(),
            (java.time.LocalDateTime) row[3], (Integer) row[4], (String) row[5], ((Number) row[6]).byteValue(),
            ((Number) row[7]).intValue(), (java.time.LocalDateTime) row[8], (java.time.LocalDateTime) row[9],
            (String) row[10]))
        .toList();
  }

  default Map<Integer, Integer> countBySourceIdGtNet() {
    return countGroupedBySourceIdGtNet().stream()
        .collect(Collectors.toMap(row -> (Integer) row[0], row -> ((Number) row[1]).intValue()));
  }

  /**
   * Checks if any pending delivery attempts exist for a specific message. Used to quickly determine if a message has
   * outstanding deliveries.
   *
   * @param idGtNetMessage the message ID
   * @return true if any pending attempts exist
   */
  boolean existsByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

  /**
   * Counts pending delivery attempts for a specific message. Used for progress tracking and logging.
   *
   * @param idGtNetMessage the message ID
   * @return count of pending attempts
   */
  long countByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

}
