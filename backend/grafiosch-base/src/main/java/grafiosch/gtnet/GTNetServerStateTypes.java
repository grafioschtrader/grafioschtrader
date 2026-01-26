package grafiosch.gtnet;

/**
 * Defines the availability states for GTNet data sharing services.
 *
 * Each GTNet domain tracks server states to indicate service availability.
 * Consumer implementations should only attempt to query providers in {@code SS_OPEN} state.
 */
public enum GTNetServerStateTypes {

  /** The condition is unknown. */
  SS_NONE((byte) 0),

  /** Service is available and accepting requests. */
  SS_OPEN((byte) 1),

  /** Service is available but not accepting new connections. Existing agreements honored. */
  SS_CLOSED((byte) 2),

  /** Temporary unavailability for scheduled maintenance. Service will return to SS_OPEN. */
  SS_MAINTENANCE((byte) 3);

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
