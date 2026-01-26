package grafioschtrader.gtnet;

import grafiosch.gtnet.IExchangeKindType;

/**
 * Exchange kind types specific to Grafioschtrader.
 *
 * Defines the types of data that can be exchanged between GTNet peers, including
 * intraday prices, historical prices, and security metadata.
 *
 * @see IExchangeKindType for the interface contract
 */
public enum GTNetExchangeKindType implements IExchangeKindType {
  LAST_PRICE ((byte) 0),
  HISTORICAL_PRICES ((byte) 1),
  SECURITY_METADATA ((byte) 2) {
    @Override
    public boolean isSyncable() {
      return false;
    }

    @Override
    public boolean supportsPush() {
      return false;
    }
  };


  private final Byte value;

  private GTNetExchangeKindType(final Byte value) {
    this.value = value;
  }

  @Override
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
