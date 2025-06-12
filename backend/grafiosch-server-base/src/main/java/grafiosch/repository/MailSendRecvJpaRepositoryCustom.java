package grafiosch.repository;

import grafiosch.dto.MailInboxWithSend;
import grafiosch.entities.MailSendRecv;

/**
 * Custom repository interface for {@link MailSendRecv} entities providing additional mail management operations beyond
 * standard JPA repository functionality.
 * 
 * <p>
 * This interface extends {@link BaseRepositoryCustom} to provide specialized methods for retrieving, marking, and
 * managing mail messages in the internal mail system.
 */
public interface MailSendRecvJpaRepositoryCustom extends BaseRepositoryCustom<MailSendRecv> {

  /**
   * Retrieves all mail messages for the currently authenticated user, including both direct messages and role-based
   * messages.
   * 
   * <p>
   * This method returns messages that are:
   * <ul>
   * <li>Directly addressed to or from the user</li>
   * <li>Sent to roles that the user belongs to</li>
   * <li>Not marked as hidden or deleted by the user</li>
   * </ul>
   * 
   * <p>
   * The returned object also includes counts of role-based conversation replies to help with UI display and
   * notification management.
   * 
   * @return a {@link MailInboxWithSend} containing the user's mail messages and conversation reply counts
   */
  MailInboxWithSend getMailsByUserOrRole();

  /**
   * Marks a specific mail message as read by the currently authenticated user.
   * 
   * <p>
   * This method updates the read status in the mail_send_recv_read_del table and returns the message with updated read
   * status information for immediate use in the UI.
   * 
   * @param idMailSendRecv the unique identifier of the mail message to mark as read
   * @return the {@link MailSendRecv} entity with updated read status and role name information attached
   */
  MailSendRecv markForRead(Integer idMailSendRecv);

  /**
   * Hides or deletes a mail message or conversation thread for the currently authenticated user.
   * 
   * <p>
   * The behavior depends on the message type and user ownership:
   * <ul>
   * <li><strong>One-to-one messages:</strong> Physically deleted from the database if the user is the sender or
   * receiver</li>
   * <li><strong>Role-based messages:</strong> Marked as hidden/deleted in the mail_send_recv_read_del table (soft
   * delete)</li>
   * <li><strong>Conversation threads:</strong> All related messages in the thread are processed according to the above
   * rules</li>
   * </ul>
   * 
   * <p>
   * This method ensures users can only hide/delete messages they have access to, providing appropriate security checks.
   * 
   * @param idMailSendRecv the unique identifier of the mail message or conversation thread to hide/delete
   * @throws SecurityException if the user attempts to delete a message they don't have access to
   */
  void hideDeleteResource(Integer idMailSendRecv);
}
