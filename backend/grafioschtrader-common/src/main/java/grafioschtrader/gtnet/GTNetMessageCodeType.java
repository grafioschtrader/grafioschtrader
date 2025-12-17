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
 * <li><b>Last Price (20-24)</b>: Intraday price data sharing negotiation</li>
 * <li><b>Entity (30-34)</b>: Historical/EOD data sharing negotiation</li>
 * <li><b>Both (40-44)</b>: Combined entity and last price negotiation</li>
 * <li><b>Notifications (50-51)</b>: Maintenance windows and shutdown announcements</li>
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

  /** Request for intraday data */
  GT_NET_LASTPRICE_REQUEST_SEL_C((byte) 20),
  /** The request for Intraday data is in processing */
  GT_NET_LASTPRICE_REQUEST_IN_PROCESS_S((byte) 21),
  /** Request for intraday data is accepted */
  GT_NET_LASTPRICE_REQUEST_ACCEPT_S((byte) 22),
  /** Request for intraday data is rejected */
  GT_NET_LASTPRICE_REQUEST_REJECTED_S((byte) 23),
  /** Exchange intraday data is canceled */
  GT_NET_LASTPRICE_REVOKE_SEL_C((byte) 24),

  /** Request for entity data */
  GT_NET_ENTITY_REQUEST_SEL_C((byte) 30),
  /** The request for entity data is in process */
  GT_NET_ENTITY_REQUEST_IN_PROCESS_S((byte) 31),
  /** The request for entity data was approved */
  GT_NET_ENTITY_REQUEST_ACCEPT_S((byte) 32),
  /** The request for entity data was rejected */
  GT_NET_ENTITY_REQUEST_REJECTED_S((byte) 33),
  /** Revoke entity exchange */
  GT_NET_ENTITY_REVOKE_SEL_C((byte) 34),

  /** Request for all types of data */
  GT_NET_BOTH_REQUEST_SEL_C((byte) 40),
  /** Request for all types of data is in process */
  GT_NET_BOTH_REQUEST_IN_PROCESS_S((byte) 41),
  /** Request for all types of data is accepted */
  GT_NET_BOTH_REQUEST_ACCEPT_S((byte) 42),
  /** Request for all types of data is rejected */
  GT_NET_BOTH_REQUEST_REJECTED_S((byte) 43),
  /** Exchange of all data is canceled */
  GT_NET_BOTH_REVOKE_SEL_C((byte) 44),

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
