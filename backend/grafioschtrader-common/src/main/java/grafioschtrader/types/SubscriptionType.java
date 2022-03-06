package grafioschtrader.types;

import java.util.Arrays;

public enum SubscriptionType {
  // eodhistoricaldata
  EOD_HISTORICAL_DATA_ALL_IN_ONE((short) 11),
  EOD_HISTORICAL_DATA_ALL_WORLD((short) 12),
  EOD_HISTORICAL_DATA_CALENDAR_DATA_FEED((short) 13),
  // stockdata
  STOCK_DATA_ORG_BASIC((short) 21),
  STOCK_DATA_ORG_STANDARD_OR_PRO((short) 22),
  // finnhub
  FINNHUB_FREE((short) 31),
  FINNHUB_BASIC((short) 32),
  FINNHUB_STANDARD_OR_PROFESSIONAL((short) 33),
  FINNHUB_ALL_IN_ONE((short) 34),
  // alphavantage
  ALPHA_VANTAGE_FREE((short) 41),
  ALPHA_VANTAGE_PREMIUM((short) 42),
  // cryptocompare
  CRYPTOCOMPARE_FREE((short) 51),
  CRYPTOCOMPARE_OTHERS((short) 52),
  // currencyconverter
  CURRENCY_CONVERTER_FREE((short) 61),
  CURRENCY_CONVERTER_OTHERS((short) 62),
  // twelvedata
  TWELVEDATA_FREE((short) 71),
  TWELVEDATA_GROW_55((short) 72),
  TWELVEDATA_GROW_144((short) 73),
  TWELVEDATA_GROW_377((short) 74);
  
  private final Short value;
  
  private SubscriptionType(final short value)  {
    this.value = value;
  }
  
  public Short getValue() {
    return this.value;
  }
  
  public static SubscriptionType getTaskTypeByValue(short value) {
    return Arrays.stream(SubscriptionType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
