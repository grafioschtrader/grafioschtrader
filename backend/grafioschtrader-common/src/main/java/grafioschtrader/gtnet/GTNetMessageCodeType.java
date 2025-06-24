package grafioschtrader.gtnet;

/**
 * Enums ending with C are intended for communication triggering by the client. Enums ending with S are intended as a
 * response from the server.
 *
 * Enums containing a _SEL_ are applied to the remote server. Enums that have an _ALL_ will be applied to all remote
 * servers that are affected.
 *
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

  /** Server is in maintenance mode during time period */
  GT_NET_MAINTENANCE_ALL_C((byte) 50),
  /** Server operation will be discontinued as of this date. */
  GT_NET_OPERATION_DISCONTINUED_ALL_C((byte) 51);

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
