package grafiosch.gtnet;

/**
 * Indicates the direction of a GTNet message from the local instance's perspective.
 *
 * This enum is crucial for displaying message threads correctly in the UI, where sent and received
 * messages need different visual treatment (similar to a chat interface).
 */
public enum SendReceivedType {

  /** Message was sent by this instance to a remote domain. idGtNet refers to the target. */
  SEND((byte) 0),

  /** Message was received by this instance from a remote domain. idGtNet refers to the source. */
  RECEIVED((byte) 1),

  /**
   * Transient state for response messages being constructed. Not typically persisted; used during
   * message processing to distinguish immediate responses from stored messages.
   */
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
