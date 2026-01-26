package grafiosch.gtnet;

/**
 * Simplified message codes for tracking entity data exchange availability.
 *
 * This enum contains only the data availability states needed for tracking
 * individual entity exchanges.
 */
public enum MessageCodesGTNetwork {

  /** Entity data is available and can be requested. Normal operational state. */
  DATA_READY_FOR_GIVEN((byte) 0),

  /** Entity data is no longer available. Exchange has been revoked or terminated. */
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
