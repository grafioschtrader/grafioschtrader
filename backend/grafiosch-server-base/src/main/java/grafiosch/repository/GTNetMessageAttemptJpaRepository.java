package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.GTNetMessageAttempt;
import jakarta.transaction.Transactional;

/**
 * Repository for managing GTNetMessageAttempt entities that track per-target delivery status
 * for future-oriented broadcast messages.
 *
 * <p>This repository provides methods for:</p>
 * <ul>
 *   <li>Finding pending deliveries (hasSend = false) by message or target</li>
 *   <li>Finding and deleting specific message-target combinations</li>
 *   <li>Querying pending future-oriented messages for the delivery task</li>
 * </ul>
 *
 * @see GTNetMessageAttempt
 */
public interface GTNetMessageAttemptJpaRepository extends JpaRepository<GTNetMessageAttempt, Integer> {

  /**
   * Finds all pending delivery attempts for a specific message.
   * Used when sending a message to check which targets still need delivery.
   *
   * @param idGtNetMessage the message ID
   * @return list of attempts where hasSend = false
   */
  List<GTNetMessageAttempt> findByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

  /**
   * Finds all pending delivery attempts targeting a specific remote.
   * Used after handshake to prioritize deliveries to newly connected partners.
   *
   * @param idGtNet the target GTNet ID
   * @return list of pending attempts for this target
   */
  List<GTNetMessageAttempt> findByIdGtNetAndHasSendFalse(Integer idGtNet);

  /**
   * Finds all delivery attempts for a specific message (both pending and completed).
   * Used for checking delivery status and cancellation processing.
   *
   * @param idGtNetMessage the message ID
   * @return list of all attempts for this message
   */
  List<GTNetMessageAttempt> findByIdGtNetMessage(Integer idGtNetMessage);

  /**
   * Finds a specific message-target delivery attempt.
   * Used to check if an attempt already exists before creating a new one.
   *
   * @param idGtNetMessage the message ID
   * @param idGtNet the target GTNet ID
   * @return optional containing the attempt if found
   */
  Optional<GTNetMessageAttempt> findByIdGtNetMessageAndIdGtNet(Integer idGtNetMessage, Integer idGtNet);

  /**
   * Deletes all delivery attempts for a specific message-target combination.
   * Used when processing cancellations to remove pending original message deliveries.
   *
   * @param idGtNetMessage the message ID
   * @param idGtNet the target GTNet ID
   */
  @Modifying
  @Transactional
  void deleteByIdGtNetMessageAndIdGtNet(Integer idGtNetMessage, Integer idGtNet);

  /**
   * Deletes all delivery attempts for a specific message.
   * Used when cleaning up after a message's effective dates have passed.
   *
   * @param idGtNetMessage the message ID
   */
  @Modifying
  @Transactional
  void deleteByIdGtNetMessage(Integer idGtNetMessage);

  /**
   * Finds all pending delivery attempts for future-oriented message types.
   * Used by GTNetFutureMessageDeliveryTask to find messages that need delivery.
   *
   * Named query: GTNetMessageAttempt.findPendingFutureMessages
   * Parameters in SQL:
   * - ?1 (messageCodes): List of byte values for future-oriented message codes (24, 25, 26, 27)
   *
   * @param messageCodes list of message code bytes to filter by
   * @return list of pending attempts for future-oriented messages
   */
  @Query(nativeQuery = true)
  List<GTNetMessageAttempt> findPendingFutureMessages(List<Byte> messageCodes);

  /**
   * Checks if any pending delivery attempts exist for a specific message.
   * Used to quickly determine if a message has outstanding deliveries.
   *
   * @param idGtNetMessage the message ID
   * @return true if any pending attempts exist
   */
  boolean existsByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

  /**
   * Counts pending delivery attempts for a specific message.
   * Used for progress tracking and logging.
   *
   * @param idGtNetMessage the message ID
   * @return count of pending attempts
   */
  long countByIdGtNetMessageAndHasSendFalse(Integer idGtNetMessage);

}
