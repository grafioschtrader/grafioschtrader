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
}
