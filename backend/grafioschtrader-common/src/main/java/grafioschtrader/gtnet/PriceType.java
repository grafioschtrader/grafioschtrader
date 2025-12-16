package grafioschtrader.gtnet;

public enum PriceType {

  LASTPRICE((byte) 0),

  HISTORICAL((byte) 1);

  private final Byte value;

  private PriceType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static PriceType getPriceType(byte value) {
    for (PriceType priceType : PriceType.values()) {
      if (priceType.getValue() == value) {
        return priceType;
      }
    }
    return null;
  }
}
