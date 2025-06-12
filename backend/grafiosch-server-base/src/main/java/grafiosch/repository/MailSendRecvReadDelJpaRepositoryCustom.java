package grafiosch.repository;

/**
 * Custom repository interface for managing read and delete status of mail messages.
 * 
 * <p>
 * This interface provides operations to track individual user interactions with mail messages, specifically for marking
 * messages as read or deleted without affecting the original message for other users. This is essential for role-based
 * messaging where multiple users may receive the same message but need individual read/delete tracking.
 */
public interface MailSendRecvReadDelJpaRepositoryCustom {

  /**
   * Marks a specific mail message as read for a given user.
   * 
   * <p>
   * This method creates or updates an entry in the mail_send_recv_read_del table to track that the specified user has
   * read the message. If no entry exists for this user-message combination, a new one is created. If an entry already
   * exists, it updates the read status.
   * 
   * <p>
   * This operation is user-specific and does not affect the read status for other users who may have received the same
   * message (e.g., through role-based messaging).
   * 
   * @param idMailSendRecv the unique identifier of the mail message to mark as read
   * @param idUser         the unique identifier of the user marking the message as read
   */
  void markForRead(Integer idMailSendRecv, Integer idUser);

  /**
   * Marks a single role-based mail message as deleted for a specific user.
   * 
   * <p>
   * This method provides a soft delete mechanism for individual messages within role-based conversations. Unlike
   * {@link #markForRead}, this is specifically designed for role messages where the user wants to hide a single message
   * rather than an entire conversation thread.
   * 
   * <p>
   * The message remains visible to other users but will be filtered out from queries for the specified user. This
   * creates or updates an entry in the mail_send_recv_read_del table with the delete flag set.
   * 
   * @param idMailSendRecv the unique identifier of the role-based mail message to mark as deleted
   * @param idUser         the unique identifier of the user marking the message as deleted
   */
  void markRoleSingleForDelete(Integer idMailSendRecv, Integer idUser);
}
