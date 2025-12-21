package grafioschtrader.gtnet;

public enum GTNetExchangeKindType {
  LAST_PRICE ((byte) 0),
  HISTORICAL_PRICES ((byte) 1);
  
  
  private final Byte value;

  private GTNetExchangeKindType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static GTNetExchangeKindType getGTNetExchangeKindType(byte value) {
    for (GTNetExchangeKindType exchangeKindType : GTNetExchangeKindType.values()) {
      if (exchangeKindType.getValue() == value) {
        return exchangeKindType;
      }
    }
    return LAST_PRICE;
  }
}
