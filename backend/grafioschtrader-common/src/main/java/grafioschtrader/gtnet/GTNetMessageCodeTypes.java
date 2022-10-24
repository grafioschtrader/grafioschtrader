package grafioschtrader.gtnet;

public enum GTNetMessageCodeTypes {
  // Update server list
  GTNET_UPDATE_SERVERLIST_C((short) 0),
  // Request for Intraday data
  GTNET_LASTPRICE_REQUEST_C((short) 2),
  // The request for Intraday data is in processing
  GTNET_LASTPRICE_REQUEST_IN_PROCESS_S((short) 3),
  // Request for Intraday data is accepted
  GTNET_LASTPRICE_REQUEST_ACCEPT_S((short) 4),
  // Request for Intraday data is rejected
  GTNET_LASTPRICE_REQUEST_REJECTED_S((short) 5),
  // Request for entity data
  GTNET_ENTITY_REQUEST_C((short) 6),
  // The request for entity data is in process
  GTNET_ENTITY_REQUEST_IN_PROCESS_S((short) 7),
  // The request for entity data was approved
  GTNET_ENTITY_REQUEST_ACCEPT_S((short) 8),
  // The request for entity data was rejected
  GTNET_ENTITY_REQUEST_REJECTED_S((short) 9),
  // Request for all types of data
  GTNET_BOTH_REQUEST_C((short) 10),
  // Request for all types of data is in process
  GTNET_BOTH_RREQUEST_IN_PROCESS_S((short) 11),
  // Request for all types of data is accepted
  GTNET_BOTH_RREQUEST_ACCEPT_S((short) 12),
  // Request for all types of data is rejected
  GTNET_BOTH_RREQUEST_REJECTED_S((short) 13),
  // Server is in maintenance mode during time period
  GTNET_MAINTENANCE_S ((short) 14),
  // Server operation will be discontinued as of this date.
  GTNET_OPERATION_DISCONTINUED_S ((short) 15);
  
  private final short value;

  private GTNetMessageCodeTypes(short value) {
    this.value = value;
  }

  public Short getValue() {
    return this.value;
  }

  public static GTNetMessageCodeTypes getGTNetMessageCodeTypes(short value) {
    for (GTNetMessageCodeTypes gtNetMessageCodeTypes : GTNetMessageCodeTypes.values()) {
      if (gtNetMessageCodeTypes.getValue() == value) {
        return gtNetMessageCodeTypes;
      }
    }
    return null;
  }
}
