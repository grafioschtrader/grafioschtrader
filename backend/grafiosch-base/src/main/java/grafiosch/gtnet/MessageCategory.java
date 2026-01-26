package grafiosch.gtnet;

/**
 * Categorizes GTNet message types by their communication pattern.
 *
 * This classification determines how the message handler processes each message and whether a response is expected:
 * <ul>
 *   <li>{@link #REQUEST}: Requires a response (immediate via auto-rules or deferred via manual admin action)</li>
 *   <li>{@link #RESPONSE}: Reply to a previous request, updates local state</li>
 *   <li>{@link #ANNOUNCEMENT}: One-way notification, no response expected</li>
 * </ul>
 */
public enum MessageCategory {

  /**
   * Request messages that expect a response from the recipient.
   *
   * The response may be immediate (via auto-response rules) or deferred (awaiting manual admin review).
   * Examples: handshake requests, entity data requests.
   */
  REQUEST,

  /**
   * Response messages that reply to a previous request.
   *
   * These update local state based on the remote server's decision (accept/reject/in-process). The handler processes
   * the response and updates configuration accordingly. No further response is sent.
   */
  RESPONSE,

  /**
   * One-way announcement messages that do not expect a response.
   *
   * Used for notifications like maintenance windows or service discontinuation. The recipient stores the message for
   * admin visibility but does not reply. Examples: maintenance announcements, revoke messages.
   */
  ANNOUNCEMENT
}
