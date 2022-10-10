package grafioschtrader.types;

public enum SendReceivedType {

  SEND((byte) 0),
  RECEIVED((byte) 1);
  
  private final Byte value;

  private SendReceivedType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static SendReceivedType getSendReceivedType(byte value) {
    for (SendReceivedType sendReceivedType : SendReceivedType.values()) {
      if (sendReceivedType.getValue() == value) {
        return sendReceivedType;
      }
    }
    return null;
  }
  
}
