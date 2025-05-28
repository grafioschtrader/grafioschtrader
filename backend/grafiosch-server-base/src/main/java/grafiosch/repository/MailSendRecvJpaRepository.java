package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.dto.MailSendRecvDTO;
import grafiosch.entities.MailSendRecv;
import grafiosch.rest.UpdateCreateJpaRepository;

/**
 * JPA Repository for {@link MailSendRecv} entities. It includes methods for standard CRUD operations and custom queries
 * for managing mail messages, including their read/delete status and group operations.
 */
public interface MailSendRecvJpaRepository extends JpaRepository<MailSendRecv, Integer>,
    MailSendRecvJpaRepositoryCustom, UpdateCreateJpaRepository<MailSendRecv> {

  MailSendRecv findFirstByIdReplyToLocalOrderByIdMailSendRecv(Integer idReplyToLocal);

  /**
   * Deletes mail messages from the {@code mail_send_recv} table that belong to a specific conversation thread
   * (identified by {@code idReplyToLocal}) and are directly associated with the specified user ({@code idUser}, either
   * as sender 'S' or receiver 'R'). This method permanently removes the message records.
   *
   * @param idReplyToLocal The ID of the conversation thread from which messages will be deleted.
   * @param idUser         The ID of the user whose messages (sent or received) within that thread will be deleted.
   */
  @Query(nativeQuery = true)
  void deleteByIdReplyToLocalAndIdUser(Integer idReplyToLocal, Integer idUser);

  /**
   * Deletes a specific mail message from the {@code mail_send_recv} table by its unique ID ({@code idMailSendRecv}),
   * but only if it was either sent by ('S') or received by ('R') the specified user ({@code idUser}). This acts as an
   * ownership check before deletion. This method permanently removes the message record.
   *
   * @param idMailSendRecv The unique ID of the mail message to be deleted.
   * @param idUser         The ID of the user who must be either the sender or receiver of the message.
   */
  @Query(nativeQuery = true)
  void deleteByIdMailSendRecvAndIdUser(Integer idMailSendRecv, Integer idUser);

  /**
   * Retrieves a list of mail messages as {@link MailSendRecvDTO} objects for a given user ({@code idUser}). This query
   * aggregates messages directly addressed to/from the user and messages related to the user's roles, considering
   * message timestamps against role modification times. It also joins with {@code mail_send_recv_read_del} to include
   * read status and filters out messages marked as hidden or deleted by the user. The results are ordered by message
   * ID.
   *
   * @param idUser The ID of the user whose messages are to be retrieved.
   * @return A list of {@link MailSendRecvDTO} objects representing the user's messages.
   */
  @Query(nativeQuery = true)
  List<MailSendRecvDTO> findByUserOrGroup(Integer idUser);

  /**
   * Counts the number of sent replies ({@code send_recv = "S"}) within conversation threads (grouped by
   * {@code id_reply_to_local}) that were initiated by a role message received by the specified user ({@code idUser}).
   * This helps in determining activity or unread replies in role-based discussions relevant to the user.
   *
   * @param idUser The ID of the user for whom to count replies in their role-based conversations.
   * @return A list of {@link CountRoleSend} projections, where each entry contains an {@code idReplyToLocal}
   *         (conversation thread ID) and {@code numberOfAnswer} (the count of sent replies in that thread).
   */
  @Query(nativeQuery = true)
  List<CountRoleSend> countRoleSend(Integer idUser);

  /**
   * Projection interface for the result of the {@link #countRoleSend(Integer)} query.
   */
  public interface CountRoleSend {
    /**
     * @return The ID of the local reply (conversation thread ID).
     */
    Integer getIdReplyToLocal();

    /**
     * @return The number of answers/replies in the conversation thread.
     */
    Integer getNumberOfAnswer();
  }
}
