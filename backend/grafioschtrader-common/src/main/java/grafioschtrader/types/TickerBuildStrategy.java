package grafioschtrader.types;

/**
 * Defines how the ticker placeholder in URL templates is constructed.
 * URL_EXTEND: Uses the urlExtend value directly as the ticker (default for most connectors).
 * CURRENCY_PAIR: Builds from fromCurrency + separator + toCurrency + suffix (for forex/crypto).
 */
public enum TickerBuildStrategy {
  URL_EXTEND((byte) 1),
  CURRENCY_PAIR((byte) 2);

  private final Byte value;

  private TickerBuildStrategy(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TickerBuildStrategy getByValue(byte value) {
    for (TickerBuildStrategy type : TickerBuildStrategy.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
