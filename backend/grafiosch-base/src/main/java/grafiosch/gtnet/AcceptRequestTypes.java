package grafiosch.gtnet;

/**
 * Defines the acceptance modes for incoming GTNet data exchange requests.
 *
 * Each GTNet entity can be configured with one of these acceptance types to control
 * how the local instance responds to data queries from remote instances:
 *
 * <ul>
 *   <li>{@link #AC_CLOSED} - No requests accepted, data exchange disabled</li>
 *   <li>{@link #AC_OPEN} - Accepts incoming requests, provides data to remote instances</li>
 *   <li>{@link #AC_PUSH_OPEN} - Accepts requests AND actively receives pushed updates from remote instances.
 *       This mode is only available for certain entity kinds as these instances
 *       maintain more active connections with more current data.</li>
 * </ul>
 *
 * Note: AC_PUSH_OPEN is preferred for data exchange because instances in this mode typically have
 * more active connections and therefore more and newer data available.
 */
public enum AcceptRequestTypes {

  /** Data exchange is closed, no requests are accepted. */
  AC_CLOSED((byte) 0),

  /** Open for receiving data requests from remote instances. */
  AC_OPEN((byte) 1),

  /**
   * Open for requests and also accepts pushed data updates from remote instances.
   * Only supported for certain entity kinds. These instances are preferred for data
   * exchange as they maintain more active connections with more current data.
   */
  AC_PUSH_OPEN((byte) 2);

  private final Byte value;

  private AcceptRequestTypes(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AcceptRequestTypes getAcceptRequestType(byte value) {
    for (AcceptRequestTypes acceptRequestType : AcceptRequestTypes.values()) {
      if (acceptRequestType.getValue() == value) {
        return acceptRequestType;
      }
    }
    return AC_CLOSED;
  }

  /**
   * Checks if this accept type allows receiving data requests.
   *
   * @return true if AC_OPEN or AC_PUSH_OPEN
   */
  public boolean isAccepting() {
    return this == AC_OPEN || this == AC_PUSH_OPEN;
  }

  /**
   * Checks if this accept type supports push functionality.
   *
   * @return true only for AC_PUSH_OPEN
   */
  public boolean isPushEnabled() {
    return this == AC_PUSH_OPEN;
  }
}
