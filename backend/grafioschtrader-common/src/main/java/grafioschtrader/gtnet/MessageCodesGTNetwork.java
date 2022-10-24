package grafioschtrader.gtnet;

public enum MessageCodesGTNetwork {

  // Data is ready to give away
  DATA_READY_FOR_GIVEN((byte) 0),
  // Data no more delivered
  DATA_CANNOT_BE_DELIVERED_ANYMORE((byte) 1);

  private final Byte value;

  private MessageCodesGTNetwork(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static MessageCodesGTNetwork getMessageCodesGTNetwork(byte value) {
    for (MessageCodesGTNetwork messageCodesGTNetwork : MessageCodesGTNetwork.values()) {
      if (messageCodesGTNetwork.getValue() == value) {
        return messageCodesGTNetwork;
      }
    }
    return null;
  }
}
