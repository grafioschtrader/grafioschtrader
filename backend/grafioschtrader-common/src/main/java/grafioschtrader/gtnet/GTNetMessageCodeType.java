package grafioschtrader.gtnet;

import grafioschtrader.entities.GTNetMessage;

/**
 * Enumerates all message types in the GTNet peer-to-peer communication protocol.
 *
 * <h3>Naming Convention</h3> Message codes follow a structured naming pattern that indicates their purpose and
 * behavior:
 * <ul>
 * <li><b>Suffix _C</b>: Client-initiated messages (triggered from UI or scheduled jobs)</li>
 * <li><b>Suffix _S</b>: Server response messages (replies to requests)</li>
 * <li><b>Contains _SEL_</b>: Targeted at a specific selected remote server</li>
 * <li><b>Contains _ALL_</b>: Broadcast to all applicable remote servers</li>
 * </ul>
 *
 * <h3>Message Workflows</h3>
 * <ol>
 * <li><b>Handshake (1-3)</b>: Initial connection and token exchange between peers</li>
 * <li><b>Server List (10-13)</b>: Discovery of other GTNet participants via peer sharing</li>
 * <li><b>Notifications (50-55)</b>: Maintenance windows and shutdown announcements</li>
 * <li><b>Data (60-64)</b>: Unified data exchange negotiation with entityKinds parameter</li>
 * </ol>
 *
 * @see GTNetModelHelper for mapping message codes to payload model classes
 * @see GTNetMessage for message storage and threading
 */
public enum GTNetMessageCodeType {
  //
  GT_NET_PING((byte) 0),

  /** Remote server wants to exchange data with this server, permission must be granted to do so */
  GT_NET_FIRST_HANDSHAKE_S((byte) 1),
  /** The requested server accepts the connection */
  GT_NET_FIRST_HANDSHAKE_ACCEPT_S((byte) 2),
  /** The requested server refuses connection */
  GT_NET_FIRST_HANDSHAKE_REJECT_S((byte) 3),

  /** Update server list */
  GT_NET_UPDATE_SERVERLIST_SEL_C((byte) 10),
  /** Return server list */
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S((byte) 11),
  /** Request for server list is rejected */
  GT_NET_UPDATE_SERVERLIST_REJECTED_S((byte) 12),
  /** My server list may no longer be queried (revoke) */
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C((byte) 13),

  /** Request for data exchange - entityKinds parameter specifies which data types to request */
  GT_NET_DATA_REQUEST_SEL_C((byte) 20),
  /** Data request is being processed */
  GT_NET_DATA_REQUEST_IN_PROCESS_S((byte) 21),
  /** Data request accepted */
  GT_NET_DATA_REQUEST_ACCEPT_S((byte) 22),
  /** Data request rejected */
  GT_NET_DATA_REQUEST_REJECTED_S((byte) 23),
  /** Revoke data exchange for specified entity kinds */
  GT_NET_DATA_REVOKE_SEL_C((byte) 24),

  /**
   * The server has gone offline. It is unknown when it will be back online. Perhaps it will just be restarted or even
   * shut down. Can also be triggered via the user interface.
   **/
  GT_NET_OFFLINE_ALL_C((byte) 50),
  /** The server has been transferred online. The server may have been restarted. Can also be triggered via the user interface.*/
  GT_NET_ONLINE_ALL_C((byte) 51),
  /** The system is at full capacity and should no longer be contacted. Only your own status changes should be sent to this server. 
   This setting can be changed via the user interface.
  */
  GT_NET_BUSY_ALL_C((byte) 52),
  /** The server is no longer busy and can be contacted again. This setting can be changed via the user interface.  */
  GT_NET_RELEASED_BUSY_ALL_C((byte) 53),
  /** Server is in maintenance mode during time period */  
  GT_NET_MAINTENANCE_ALL_C((byte) 54),
  /** Server operation will be discontinued as of this date. */
  GT_NET_OPERATION_DISCONTINUED_ALL_C((byte) 55);

  private final byte value;

  private GTNetMessageCodeType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return this.value;
  }

  public static GTNetMessageCodeType getGTNetMessageCodeTypeByValue(byte value) {
    for (GTNetMessageCodeType gtNetMessageCodeType : GTNetMessageCodeType.values()) {
      if (gtNetMessageCodeType.getValue() == value) {
        return gtNetMessageCodeType;
      }
    }
    return null;
  }
}
