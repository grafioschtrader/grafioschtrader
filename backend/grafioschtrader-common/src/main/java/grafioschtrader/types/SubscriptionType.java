package grafioschtrader.types;

import java.util.Arrays;

public enum SubscriptionType {
  EOD_HISTORICAL_DATA_ALL_IN_ONE((short) 11),
  EOD_HISTORICAL_DATA_ALL_WORLD((short) 12),
  EOD_HISTORICAL_DATA_CALENDAR_DATA_FEED((short) 13),
  STOCK_DATA_ORG_BASIC((short) 21),
  STOCK_DATA_ORG_STANDARD_OR_PRO((short) 22),
  FINNHUB_FREE((short) 31),
  FINNHUB_BASIC((short) 32),
  FINNHUB_STANDARD_OR_PROFESSIONAL((short) 32),
  FINNHUB_ALL_IN_ONE((short) 34),
  ALPHA_VANTAGE_FREE((short) 41),
  ALPHA_VANTAGE_PREMIUM((short) 41),
  CRYPTOCOMPARE_FREE((short) 51),
  CRYPTOCOMPARE_OTHERS((short) 52),
  CURRENCY_CONVERTER_FREE((short) 61),
  CURRENCY_CONVERTER__OTHERS((short) 62);
  
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
