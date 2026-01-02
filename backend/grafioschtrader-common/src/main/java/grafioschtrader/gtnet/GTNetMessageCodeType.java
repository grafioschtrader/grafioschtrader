package grafioschtrader.gtnet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.GTNetMessage;

/**
 * Enumerates all message types in the GTNet peer-to-peer communication protocol.
 *
 * <h3>Naming Convention</h3> Message codes follow a structured naming pattern that indicates their purpose and
 * behavior:
 * <ul>
 * <li><b>Contains _RR_</b>: Used together with _SEL_ and means that the message expects a response. RR stands for Requires Respond
 * <li><b>Suffix _C</b>: Client-initiated messages (triggered from UI or scheduled jobs)</li>
 * <li><b>Suffix _S</b>: Server response messages (replies to requests)</li>
 * <li><b>Contains _SEL_</b>: Targeted at a specific selected remote server</li>
 * <li><b>Contains _ALL_</b>: Broadcast to all applicable remote servers</li>
 * </ul>
 *
 * <h3>Message Workflows</h3>
 * <ol>
 * <li><b>Handshake (1-4)</b>: Initial connection and token exchange between peers</li>
 * <li><b>Server List (10-13)</b>: Discovery of other GTNet participants via peer sharing</li>
 * <li><b>Notifications (20-27)</b>: Maintenance windows, shutdown announcements, and their cancellations</li>
 * <li><b>Data (50-54)</b>: Unified data exchange negotiation with entityKinds parameter</li>
 * </ol>
 *
 * @see GTNetModelHelper for mapping message codes to payload model classes
 * @see GTNetMessage for message storage and threading
 */
public enum GTNetMessageCodeType {
  //
  GT_NET_PING((byte) 0),

  /** Remote server wants to exchange data with this server, permission must be granted to do so */
  GT_NET_FIRST_HANDSHAKE_SEL_RR_S((byte) 1),
  /** The requested server accepts the connection */
  GT_NET_FIRST_HANDSHAKE_ACCEPT_S((byte) 2),
  /** The requested server refuses connection */
  GT_NET_FIRST_HANDSHAKE_REJECT_S((byte) 3),
  /** The requested server rejects because the requesting server is not in the GTNet list and allowServerCreation is false */
  GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S((byte) 4),

  /** Update server list */
  GT_NET_UPDATE_SERVERLIST_SEL_RR_C((byte) 10),
  /** Return server list */
  GT_NET_UPDATE_SERVERLIST_ACCEPT_S((byte) 11),
  /** Request for server list is rejected */
  GT_NET_UPDATE_SERVERLIST_REJECTED_S((byte) 12),
  /** My server list may no longer be queried (revoke) */
  GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C((byte) 13),

  /**
   * The server has gone offline. It is unknown when it will be back online. Perhaps it will just be restarted or even
   * shut down. Can also be triggered via the user interface.
   **/
  GT_NET_OFFLINE_ALL_C((byte) 20),
  /** The server has been transferred online. The server may have been restarted. Can also be triggered via the user interface.*/
  GT_NET_ONLINE_ALL_C((byte) 21),
  /** The system is at full capacity and should no longer be contacted. Only your own status changes should be sent to this server. 
   This setting can be changed via the user interface.
  */
  GT_NET_BUSY_ALL_C((byte) 22),
  /** The server is no longer busy and can be contacted again. This setting can be changed via the user interface.  */
  GT_NET_RELEASED_BUSY_ALL_C((byte) 23),
  /** Server is in maintenance mode during time period */
  GT_NET_MAINTENANCE_ALL_C((byte) 24),
  /** Server operation will be discontinued as of this date. */
  GT_NET_OPERATION_DISCONTINUED_ALL_C((byte) 25),
  /** Cancels a previously announced maintenance window */
  GT_NET_MAINTENANCE_CANCEL_ALL_C((byte) 26),
  /** Cancels a previously announced operation discontinuation */
  GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C((byte) 27),

  /** Request for data exchange - entityKinds parameter specifies which data types to request */
  GT_NET_DATA_REQUEST_SEL_RR_C((byte) 50),
  /** Data request accepted */
  GT_NET_DATA_REQUEST_ACCEPT_S((byte) 52),
  /** Data request rejected */
  GT_NET_DATA_REQUEST_REJECTED_S((byte) 53),
  /** Revoke data exchange for specified entity kinds */
  GT_NET_DATA_REVOKE_SEL_C((byte) 54),

  // Lastprice exchange messages (60-69)
  /** Request intraday prices from remote server, includes current local prices for bidirectional exchange */
  GT_NET_LASTPRICE_EXCHANGE_SEL_C((byte) 60),
  /** Response containing intraday prices that are more current than those in the request */
  GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S((byte) 61),
  /** Push intraday prices to remote server without requesting prices back */
  GT_NET_LASTPRICE_PUSH_SEL_C((byte) 62),
  /** Acknowledge receipt of pushed prices with count of accepted updates */
  GT_NET_LASTPRICE_PUSH_ACK_S((byte) 63),
  /** Response when the request exceeds the configured max_limit for instruments */
  GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S((byte) 64),

  // Exchange sync messages (70-79)
  /** Request exchange configuration sync from remote server, includes local changed entries since last sync */
  GT_NET_EXCHANGE_SYNC_SEL_RR_C((byte) 70),
  /** Response containing remote's changed exchange entries for bidirectional sync */
  GT_NET_EXCHANGE_SYNC_RESPONSE_S((byte) 71);



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

  /**
   * Maps request codes (_RR_) to their valid response codes. Used by the UI to show available response options for
   * unanswered incoming requests.
   */
  private static final Map<GTNetMessageCodeType, List<GTNetMessageCodeType>> RESPONSE_MAP = Map.of(
      GT_NET_FIRST_HANDSHAKE_SEL_RR_S,
      List.of(GT_NET_FIRST_HANDSHAKE_ACCEPT_S, GT_NET_FIRST_HANDSHAKE_REJECT_S),
      GT_NET_UPDATE_SERVERLIST_SEL_RR_C,
      List.of(GT_NET_UPDATE_SERVERLIST_ACCEPT_S, GT_NET_UPDATE_SERVERLIST_REJECTED_S),
      GT_NET_DATA_REQUEST_SEL_RR_C, List.of(GT_NET_DATA_REQUEST_ACCEPT_S, GT_NET_DATA_REQUEST_REJECTED_S),
      GT_NET_EXCHANGE_SYNC_SEL_RR_C, List.of(GT_NET_EXCHANGE_SYNC_RESPONSE_S));

  /**
   * Returns the valid response codes for a given request code.
   *
   * @param requestCode the request message code (must be an _RR_ type)
   * @return list of valid response codes, or empty list if not a request type
   */
  public static List<GTNetMessageCodeType> getValidResponses(GTNetMessageCodeType requestCode) {
    return RESPONSE_MAP.getOrDefault(requestCode, Collections.emptyList());
  }

  /**
   * Checks if this message code is a request that requires a response.
   *
   * @return true if this is an _RR_ type message code
   */
  public boolean isRequestRequiringResponse() {
    return this.name().contains("_RR_");
  }
}
