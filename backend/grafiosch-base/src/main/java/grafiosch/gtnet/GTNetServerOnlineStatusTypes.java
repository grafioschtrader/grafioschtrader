package grafiosch.gtnet;

/**
 * Defines the online status of a GTNet server instance.
 *
 * This status is used to track whether a remote GTNet instance is reachable and available for communication.
 * The status is updated based on communication attempts and explicit announcements during server startup/shutdown.
 */
public enum GTNetServerOnlineStatusTypes {

  /** Status is unknown - initial state before any communication attempt. */
  SOS_UNKNOWN((byte) 0),

  /** Server is online and reachable. */
  SOS_ONLINE((byte) 1),

  /** Server is offline or unreachable. */
  SOS_OFFLINE((byte) 2);

  private final Byte value;

  private GTNetServerOnlineStatusTypes(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static GTNetServerOnlineStatusTypes getGTNetServerOnlineStatusType(byte value) {
    for (GTNetServerOnlineStatusTypes statusType : GTNetServerOnlineStatusTypes.values()) {
      if (statusType.getValue() == value) {
        return statusType;
      }
    }
    return SOS_UNKNOWN;
  }

  /**
   * Converts a boolean reachability result to the appropriate status type.
   *
   * @param reachable true if server is reachable, false otherwise
   * @return SOS_ONLINE if reachable, SOS_OFFLINE otherwise
   */
  public static GTNetServerOnlineStatusTypes fromReachable(boolean reachable) {
    return reachable ? SOS_ONLINE : SOS_OFFLINE;
  }
}
