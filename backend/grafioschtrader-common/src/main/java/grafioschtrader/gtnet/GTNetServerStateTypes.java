package grafioschtrader.gtnet;

public enum GTNetServerStateTypes {

  // There is no support for last price
  NONE((byte) 0), 
  // There is support for last price, but no more remote domain is allowed
  CLOSED((byte) 1), 
  // The system is in maintenance but will come back
  MAINTENANCE((byte) 3), 
  // There is support for last price, but no more remote domain is allowed
  OPEN((byte) 4);

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
