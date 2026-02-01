package grafiosch.gtnet;



/**
 * Core protocol message codes for GNet peer-to-peer communication.
 *
 * This enum defines the fundamental protocol messages (codes 0-54) that are part of the
 * GNet library. Application-specific message codes (60+) should be defined in separate
 * enums that implement {@link GTNetMessageCode}.
 *
 * <h3>Message Code Ranges</h3>
 * <ul>
 *   <li><b>0</b>: Ping/health check</li>
 *   <li><b>1-4</b>: Handshake protocol</li>
 *   <li><b>10-13</b>: Server list exchange</li>
 *   <li><b>20-28</b>: Status announcements (offline, maintenance, discontinued)</li>
 *   <li><b>30-34</b>: Admin-to-admin messaging</li>
 *   <li><b>50-54</b>: Data exchange negotiation</li>
 *   <li><b>60+</b>: Reserved for application-specific messages</li>
 * </ul>
 *
 * @see GTNetMessageCode for the common interface
 */
public enum GNetCoreMessageCode implements GTNetMessageCode {

  /**
   * Lightweight health check used on server startup to verify peer reachability.
   * When a peer receives a ping and responds, both sides automatically update
   * each other's online status.
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

  // Admin messages (30-34)
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
   * Looks up an app-specific message code (60+) by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCodeType, or null if not found in app-specific codes
   */
  public static GNetCoreMessageCode getGTNetMessageCodeTypeByValue(byte value) {
    for (GNetCoreMessageCode gtNetMessageCodeType : GNetCoreMessageCode.values()) {
      if (gtNetMessageCodeType.getValue() == value) {
        return gtNetMessageCodeType;
      }
    }
    return null;
  }
  
 
  /**
   * Unified lookup for message codes by byte value. Checks both app-specific codes (60+)
   * and core protocol codes (0-54).
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public static GTNetMessageCode getMessageCodeByValue(byte value) {
    return GNetCoreMessageCode.getGTNetMessageCodeTypeByValue(value);
  }
  

  /**
   * Looks up a core message code by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GNetCoreMessageCode, or null if not found
   */
  public static GNetCoreMessageCode getByValue(byte value) {
    for (GNetCoreMessageCode code : values()) {
      if (code.getValue() == value) {
        return code;
      }
    }
    return null;
  }

  /**
   * Looks up a message code by its name. First tries GNetCoreMessageCode, then falls back
   * to the provided app-specific enum class.
   *
   * @param name the enum constant name to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public static GTNetMessageCode getByName(String name) {
    if (name == null) {
      return null;
    }
    // First try core message codes
    try {
      return GNetCoreMessageCode.valueOf(name);
    } catch (IllegalArgumentException e) {
      // Not a core code, will try app-specific codes below
    }
    return null;
  }
}
