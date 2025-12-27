package grafioschtrader.gtnet;

/**
 * Defines the exchange status between this GTNet instance and a remote instance.
 *
 * Each GTNetConfig tracks two independent exchange statuses:
 * <ul>
 *   <li>{@link GTNetConfig#lastpriceExchange} - Exchange status for intraday/last price data</li>
 *   <li>{@link GTNetConfig#entityExchange} - Exchange status for entity/historical data</li>
 * </ul>
 *
 * The status determines the direction of data flow between instances.
 */
public enum GTNetExchangeStatusTypes {

  /** No data exchange configured. Initial state before any request is accepted. */
  ES_NO_EXCHANGE((byte) 0),

  /** This instance sends data to the remote instance (outbound only). */
  ES_SEND((byte) 1),

  /** This instance receives data from the remote instance (inbound only). */
  ES_RECEIVE((byte) 2),

  /** Bidirectional data exchange - both sending and receiving. */
  ES_BOTH((byte) 3);

  private final Byte value;

  private GTNetExchangeStatusTypes(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static GTNetExchangeStatusTypes getGTNetExchangeStatusType(byte value) {
    for (GTNetExchangeStatusTypes exchangeType : GTNetExchangeStatusTypes.values()) {
      if (exchangeType.getValue() == value) {
        return exchangeType;
      }
    }
    return ES_NO_EXCHANGE;
  }

  /**
   * Checks if this status includes sending capability.
   *
   * @return true if ES_SEND or ES_BOTH
   */
  public boolean canSend() {
    return this == ES_SEND || this == ES_BOTH;
  }

  /**
   * Checks if this status includes receiving capability.
   *
   * @return true if ES_RECEIVE or ES_BOTH
   */
  public boolean canReceive() {
    return this == ES_RECEIVE || this == ES_BOTH;
  }

  /**
   * Returns a new status with sending capability added.
   * ES_NO_EXCHANGE -> ES_SEND, ES_RECEIVE -> ES_BOTH, others unchanged.
   *
   * @return the combined status
   */
  public GTNetExchangeStatusTypes withSend() {
    return getGTNetExchangeStatusType((byte) (this.value | ES_SEND.value));
  }

  /**
   * Returns a new status with receiving capability added.
   * ES_NO_EXCHANGE -> ES_RECEIVE, ES_SEND -> ES_BOTH, others unchanged.
   *
   * @return the combined status
   */
  public GTNetExchangeStatusTypes withReceive() {
    return getGTNetExchangeStatusType((byte) (this.value | ES_RECEIVE.value));
  }

  /**
   * Returns a new status with sending capability removed.
   * ES_SEND -> ES_NO_EXCHANGE, ES_BOTH -> ES_RECEIVE, others unchanged.
   *
   * @return the reduced status
   */
  public GTNetExchangeStatusTypes withoutSend() {
    return getGTNetExchangeStatusType((byte) (this.value & ~ES_SEND.value));
  }

  /**
   * Returns a new status with receiving capability removed.
   * ES_RECEIVE -> ES_NO_EXCHANGE, ES_BOTH -> ES_SEND, others unchanged.
   *
   * @return the reduced status
   */
  public GTNetExchangeStatusTypes withoutReceive() {
    return getGTNetExchangeStatusType((byte) (this.value & ~ES_RECEIVE.value));
  }
}
