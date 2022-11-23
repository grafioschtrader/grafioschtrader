package grafioschtrader.gtnet;

public enum SendReceivedType {
  // Message was send
  SEND((byte) 0),
  // Message was received
  RECEIVED((byte) 1),
  // Message is answered. This state is not persited 
  ANSWER((byte) 2);
  
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
