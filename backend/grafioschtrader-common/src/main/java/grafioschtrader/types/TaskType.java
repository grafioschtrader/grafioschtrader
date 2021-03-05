package grafioschtrader.types;

import java.util.Arrays;

public enum TaskType {

  // Historical and intraday price update with price data quality update
  PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU((byte) 0),
  // Dividend connector has changed -> Read dividends
  SECURTY_DIVIDEND_UPDATE_FOR_SECURITY((byte) 1),
  // Split connector has changed -> Read splits
  SECURTY_SPLIT_UPDATE_FOR_SECURITY((byte) 2),
  // Creation of currencies and possible recreation of holing tables
  CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO((byte) 3),
  // Changed currency of tenant and portfolio it needs a creation of currencies
  // and possible recreation of holing tables
  CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO((byte) 4),
  // Load or reload security price historical data
  SECURITY_LOAD_HISORICAL_INTRA_PRICE_DATA((byte) 5),
  // The splits of security has changed, rebuild for all clients its security holdings
  HOLDINGS_SECURITY_REBUILD((byte) 6),
  // Changed historical currency prices may influence the deposit holdings since it depends on it
  REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE((byte) 7),
  // When a split is added it may take some days until the data provider reflect that in adjusted historical prices
  CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES((byte) 8),
  // Rebuild Holdings for all tenants, normally one one when the database was created from export 
  REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT((byte) 9),
  //
  LOAD_EMPTY_CURRENCYPAIR_HISTORYQOUTES((byte) 10),
  // Copy the source tenant to the demo accounts
  COPY_DEMO_ACCOUNTS((byte) 11),
  
  // Creates the calendar for stock exchanged by a manger index
  UNOFFICIAL_CREATE_STOCK_EXCHANGE_CALENDAR((byte) 20),
  // Create dividends from dividend table
  UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE((byte) 21);
  
  private final Byte value;

  private TaskType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TaskType getTaskTypeByValue(byte value) {
    return Arrays.stream(TaskType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
