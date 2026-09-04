package grafiosch.gtnet;

/** Outcome of delivering one persisted GTNet message to one remote peer. */
public enum GTNetMessageAttemptStatus {

  /** The attempt was created but has not been evaluated yet. */
  QUEUED((byte) 0),

  /** Delivery cannot start until the peer completes or renews its handshake. */
  WAITING_HANDSHAKE((byte) 1),

  /** The last transmission failed, but a later delivery-task run may retry it. */
  RETRYABLE_FAILURE((byte) 2),

  /** The peer accepted the message. */
  DELIVERED((byte) 3),

  /** The peer is permanently out of service and will never be contacted again. */
  PEER_OUT_OF_SERVICE((byte) 4),

  /** The announcement became ineffective before it could be delivered. */
  EXPIRED((byte) 5);

  private final byte value;

  GTNetMessageAttemptStatus(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public boolean isTerminal() {
    return this == DELIVERED || this == PEER_OUT_OF_SERVICE || this == EXPIRED;
  }

  public boolean isDelivered() {
    return this == DELIVERED;
  }

  public static GTNetMessageAttemptStatus getByValue(byte value) {
    for (GTNetMessageAttemptStatus status : values()) {
      if (status.value == value) {
        return status;
      }
    }
    return QUEUED;
  }
}
