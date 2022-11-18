package grafioschtrader.gtnet;

public enum GTNetServerStateTypes {

  // There is no support for last price or entity
  SS_NONE((byte) 0), 
  // There is support for last price or entity, but no more remote domain is allowed
  SS_CLOSED((byte) 1), 
  // The system is in maintenance but will come back
  SS_MAINTENANCE((byte) 2), 
  // There is support for last price or entity, but no more remote domain is allowed
  SS_OPEN((byte) 3);

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
