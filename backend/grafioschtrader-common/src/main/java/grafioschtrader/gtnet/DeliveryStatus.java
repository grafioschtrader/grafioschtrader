package grafioschtrader.gtnet;

import grafioschtrader.entities.GTNetMessage;

/**
 * Tracks the delivery status of outgoing GTNet messages.
 *
 * <p>This enum is used in conjunction with {@link grafioschtrader.entities.GTNetMessageAttempt}
 * to provide a quick lookup of the overall delivery state without querying attempt records.</p>
 *
 * <p>For received messages (sendRecv = RECEIVED), this status is not applicable and typically
 * remains at the default value or is ignored.</p>
 *
 * @see GTNetMessage#deliveryStatus for usage in the message entity
 * @see grafioschtrader.entities.GTNetMessageAttempt for individual transmission attempt tracking
 */
public enum DeliveryStatus {

  /** Message is queued for delivery or retry attempts are still possible. Initial state for sent messages. */
  PENDING((byte) 0),

  /** Message was successfully delivered. At least one transmission attempt succeeded. */
  DELIVERED((byte) 1),

  /** Message delivery permanently failed. All retry attempts have been exhausted. */
  FAILED((byte) 2);

  private final Byte value;

  private DeliveryStatus(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static DeliveryStatus getDeliveryStatus(byte value) {
    for (DeliveryStatus status : DeliveryStatus.values()) {
      if (status.getValue() == value) {
        return status;
      }
    }
    return PENDING;
  }

}
