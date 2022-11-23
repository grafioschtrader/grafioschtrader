package grafioschtrader.gtnet;

/**
 * Enums ending with C are intended for communication triggering by the client.
 * Enums ending with S are intended as a response from the server.
 *
 */
public enum GTNetMessageCodeType {
  // 
  GTNET_PING((byte) 0), 
  GTNET_FIRST_HANDSHAKE_S((byte) 1),
  GTNET_FIRST_HANDSHAKE_ACCEPT_S((byte) 2), 
  GTNET_FIRST_HANDSHAKE_REJECT_S((byte) 3),
  // Update server list
  GTNET_UPDATE_SERVERLIST_C((byte) 4),
  // Return server list
  GTNET_UPDATE_SERVERLIST_ACCEPT_S((byte) 5),
  // Request for server list is rejected
  GTNET_UPDATE_SERVERLIST_REJECTED_S((byte) 6),
  // Request for intraday data
  GTNET_LASTPRICE_REQUEST_C((byte) 7),
  // The request for Intraday data is in processing
  GTNET_LASTPRICE_REQUEST_IN_PROCESS_S((byte) 8),
  // Request for intraday data is accepted
  GTNET_LASTPRICE_REQUEST_ACCEPT_S((byte) 9),
  // Request for intraday data is rejected
  GTNET_LASTPRICE_REQUEST_REJECTED_S((byte) 10),
  // Request for entity data
  GTNET_ENTITY_REQUEST_C((byte) 11),
  // The request for entity data is in process
  GTNET_ENTITY_REQUEST_IN_PROCESS_S((byte) 12),
  // The request for entity data was approved
  GTNET_ENTITY_REQUEST_ACCEPT_S((byte) 13),
  // The request for entity data was rejected
  GTNET_ENTITY_REQUEST_REJECTED_S((byte) 14),
  // Request for all types of data
  GTNET_BOTH_REQUEST_C((byte) 15),
  // Request for all types of data is in process
  GTNET_BOTH_REQUEST_IN_PROCESS_S((byte) 16),
  // Request for all types of data is accepted
  GTNET_BOTH_REQUEST_ACCEPT_S((byte) 17),
  // Request for all types of data is rejected
  GTNET_BOTH_REQUEST_REJECTED_S((byte) 18),
  // Server is in maintenance mode during time period
  GTNET_MAINTENANCE_C((byte) 19),
  // Server operation will be discontinued as of this date.
  GTNET_OPERATION_DISCONTINUED_C((byte) 20);
  

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
