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
 * Consumer implementations should only attempt to query providers in {@code SS_OPEN} state.
 *
 * @see GTNet#entityServerState
 * @see GTNet#lastpriceServerState
 */
public enum GTNetServerStateTypes {

  /** Service not configured or not available. Initial state before handshake. */
  SS_OPEN((byte) 0),

  /** Service is available but not accepting new connections. Existing agreements honored. */
  SS_CLOSED((byte) 1),

  /** Temporary unavailability for scheduled maintenance. Service will return to SS_OPEN. */
  SS_MAINTENANCE((byte) 2);


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
