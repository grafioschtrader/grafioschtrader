package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.MailSendRecvReadDel;
import grafiosch.entities.MailSendRecvReadDel.MailSendRecvReadDelKey;

public interface MailSendRecvReadDelJpaRepository
    extends JpaRepository<MailSendRecvReadDel, MailSendRecvReadDelKey>, MailSendRecvReadDelJpaRepositoryCustom {

  //@formatter:off
  /**
   * Marks a group of related mail messages (a conversation thread) as deleted for a specific user.
   * This is achieved by inserting new records into the {@code mail_send_recv_read_del} table
   * with {@code mark_hide_del = 1}, or updating existing ones if they already exist
   * (ON DUPLICATE KEY UPDATE).
   *
   * <p>The SQL query identifies messages belonging to the specified {@code idReplyToLocal} (conversation thread ID)
   * that are relevant to the given {@code idUser}. This includes:
   * <ol>
   * <li>Messages received by the user as part of a role, considering the user's role membership start time.</li>
   * <li>Messages directly sent by or received by the user within that conversation thread.</li>
   * <li>Other messages within the same role-based conversation thread that the user might have visibility to
   * (e.g., public replies in a role discussion, or private replies if the user was the sender of the original role message),
   * again considering role membership start times.</li>
   * </ol>
   * For all identified messages, a corresponding entry in {@code mail_send_recv_read_del} is created or updated
   * to set {@code mark_hide_del = 1} and {@code has_been_read = 0} (as this operation only marks for deletion,
   * not as read).
   * @param idUser The ID of the user for whom the message group should be marked as deleted.
   * @param idReplyToLocal The ID that identifies the conversation thread/group of messages.
   */
  //@formatter:on
  @Query(nativeQuery = true)
  void markforDelGroup(Integer idUser, Integer idReplyToLocal);
}
