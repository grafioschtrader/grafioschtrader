package grafiosch.gtnet;

/**
 * Core protocol message codes for GNet peer-to-peer communication.
 *
 * This enum defines the fundamental protocol messages (codes 0-54) that are part of the GNet library.
 * Application-specific message codes (60+) are defined in separate enums that implement {@link GTNetMessageCode}.
 *
 * <p>
 * The name of a constant describes nothing the system relies on. What each code means — its category, the answers it
 * accepts, its payload, whether a person may send it — is declared once in {@code CoreProtocolDescriptors} and read
 * from {@code GTNetMessageCodeRegistry}.
 * </p>
 *
 * <h3>Message Code Ranges</h3>
 * <ul>
 * <li><b>0</b>: Ping/health check</li>
 * <li><b>1-4</b>: Handshake protocol</li>
 * <li><b>5-7</b>: Token refresh</li>
 * <li><b>10-13</b>: Server list exchange</li>
 * <li><b>20, 24-28</b>: Status announcements (offline, maintenance, discontinued, settings)</li>
 * <li><b>21-23</b>: Protocol outcomes (acknowledged, deferred, error)</li>
 * <li><b>29</b>: Rate limiting</li>
 * <li><b>30</b>: Admin-to-admin messaging</li>
 * <li><b>50-54</b>: Data exchange negotiation</li>
 * <li><b>60+</b>: Reserved for application-specific messages</li>
 * </ul>
 *
 * @see GTNetMessageCode for the common interface
 */
public enum GNetCoreMessageCode implements GTNetMessageCode {

  /**
   * Lightweight health check used on server startup to verify peer reachability. When a peer receives a ping and
   * responds, both sides automatically update each other's online status.
   */
  GT_NET_PING((byte) 0),

  // Handshake messages (1-4)

  /** Remote server wants to exchange data with this server, permission must be granted */
  GT_NET_FIRST_HANDSHAKE_SEL_RR_S((byte) 1),

  /** The requested server accepts the connection */
  GT_NET_FIRST_HANDSHAKE_ACCEPT_S((byte) 2),

  /** The requested server refuses connection */
  GT_NET_FIRST_HANDSHAKE_REJECT_S((byte) 3),

  /** Rejection because requesting server is not in GTNet list and allowServerCreation is false */
  GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S((byte) 4),

  // Token refresh messages (5-7)

  /** Request to refresh authentication tokens between established peers */
  GT_NET_TOKEN_REFRESH_SEL_RR_C((byte) 5),

  /** Token refresh accepted - new tokens have been generated */
  GT_NET_TOKEN_REFRESH_ACCEPT_S((byte) 6),

  /** Token refresh rejected */
  GT_NET_TOKEN_REFRESH_REJECTED_S((byte) 7),

  // Server list messages (10-13)

  /** Request to receive remote's server list */
  GT_NET_UPDATE_SERVERLIST_SEL_RR_C((byte) 10),

  /** Return server list to requester */
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S((byte) 11),

  /** Request for server list is rejected */
  GT_NET_UPDATE_SERVERLIST_REJECTED_S((byte) 12),

  /** Revoke permission to query server list */
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C((byte) 13),

  // Status announcements (20-28)

  /** Server has gone offline - may restart or shut down */
  GT_NET_OFFLINE_ALL_C((byte) 20),

  // Admin messages (30)
  // Note: byte value 31 was used by GT_NET_ADMIN_MESSAGE_ALL_C (deprecated) - do not reuse

  /** Admin message sent to a specific GTNet domain (targeted or multi-target via background job) */
  GT_NET_ADMIN_MESSAGE_SEL_C((byte) 30),

  /** Server is in maintenance mode during time period */
  GT_NET_MAINTENANCE_ALL_C((byte) 24),

  /** Server operation will be discontinued as of this date */
  GT_NET_OPERATION_DISCONTINUED_ALL_C((byte) 25),

  /** Cancels a previously announced maintenance window */
  GT_NET_MAINTENANCE_CANCEL_ALL_C((byte) 26),

  /** Cancels a previously announced operation discontinuation */
  GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C((byte) 27),

  /** Server settings have been updated (dailyRequestLimit, acceptRequest, serverState, maxLimit) */
  GT_NET_SETTINGS_UPDATED_ALL_C((byte) 28),

  // Protocol outcomes (21-23)

  /**
   * The message was received and fully processed and no reply follows. It is what an announcement or a one-way message
   * is answered with, and it says nothing beyond "this arrived and was dealt with". It is never a valid reply to a
   * request that expects a semantic answer.
   */
  GT_NET_ACK_S((byte) 21),

  /**
   * The message was accepted but not answered: an administrator has to decide, and the real response arrives later as a
   * message of its own. The sender keeps the request open — a deferred acknowledgement must never be recorded as the
   * reply that closes it.
   */
  GT_NET_DEFERRED_S((byte) 22),

  /**
   * The message was not processed. {@code MessageEnvelope.errorMsgCode} carries the stable reason, so the sender can
   * tell a malformed envelope from a refused one without parsing free text.
   */
  GT_NET_ERROR_S((byte) 23),

  // Rate limiting (29)

  /**
   * The remote has used up the daily request budget this server grants it. The budget is
   * {@link grafiosch.entities.GTNet#getDailyRequestLimit()} of the local entry, the consumption is
   * {@code GTNetConfig.dailyRequestLimitCount} of the remote's configuration row. The refusal lasts until the counter
   * rolls over on the first request of the next UTC day.
   */
  GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S((byte) 29),

  // Data exchange negotiation (50-54)

  /** Request for data exchange - entityKinds parameter specifies which data types */
  GT_NET_DATA_REQUEST_SEL_RR_C((byte) 50),

  /** Data request accepted */
  GT_NET_DATA_REQUEST_ACCEPT_S((byte) 52),

  /** Data request rejected */
  GT_NET_DATA_REQUEST_REJECTED_S((byte) 53),

  /** Revoke data exchange for specified entity kinds */
  GT_NET_DATA_REVOKE_SEL_C((byte) 54);

  private final byte value;

  GNetCoreMessageCode(byte value) {
    this.value = value;
  }

  @Override
  public byte getValue() {
    return this.value;
  }

  /**
   * Looks up a core message code by its byte value.
   *
   * <p>
   * This resolves the codes of this enum alone. Anything that has to resolve an application code as well reads
   * {@code GTNetMessageCodeRegistry}, which holds the whole protocol; a lookup here cannot, because
   * {@code grafiosch-base} does not know the application enum.
   * </p>
   *
   * @param value the byte value to look up
   * @return the corresponding GNetCoreMessageCode, or null if the value is not a core code
   */
  public static GNetCoreMessageCode getByValue(byte value) {
    for (GNetCoreMessageCode code : values()) {
      if (code.getValue() == value) {
        return code;
      }
    }
    return null;
  }
}
