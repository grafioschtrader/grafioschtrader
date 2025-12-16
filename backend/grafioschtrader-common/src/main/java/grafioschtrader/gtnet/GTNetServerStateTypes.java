package grafioschtrader.gtnet;

import grafioschtrader.entities.GTNet;

/**
 * Defines the availability states for GTNet data sharing services (entity data and intraday prices).
 *
 * Each GTNet domain tracks two independent server states:
 * <ul>
 *   <li>{@link GTNet#entityServerState} - Availability for historical/entity data sharing</li>
 *   <li>{@link GTNet#lastpriceServerState} - Availability for intraday price sharing</li>
 * </ul>
 *
 * State transitions typically follow this lifecycle:
 * <pre>
 * SS_NONE -> (handshake) -> SS_OPEN or SS_FAILED_HANDSHAKE
 * SS_OPEN -> (maintenance announcement) -> SS_MAINTENANCE -> SS_OPEN
 * SS_OPEN -> (capacity reached) -> SS_CLOSED
 * </pre>
 *
 * Consumer implementations should only attempt to query providers in {@code SS_OPEN} state.
 *
 * @see GTNet#entityServerState
 * @see GTNet#lastpriceServerState
 */
public enum GTNetServerStateTypes {

  /** Service not configured or not available. Initial state before handshake. */
  SS_NONE((byte) 0),

  /** Handshake with this domain failed and cannot be retried. Requires manual intervention. */
  SS_FAILED_HANDSHAKE((byte) 1),

  /** Service is available but not accepting new connections. Existing agreements honored. */
  SS_CLOSED((byte) 2),

  /** Temporary unavailability for scheduled maintenance. Service will return to SS_OPEN. */
  SS_MAINTENANCE((byte) 3),

  /** Service is fully operational and accepting requests. */
  SS_OPEN((byte) 4);

  private final Byte value;

  private GTNetServerStateTypes(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static GTNetServerStateTypes getGTNetServerStateType(byte value) {
    for (GTNetServerStateTypes readWriteType : GTNetServerStateTypes.values()) {
      if (readWriteType.getValue() == value) {
        return readWriteType;
      }
    }
    return null;
  }
}
