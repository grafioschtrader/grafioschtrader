package grafioschtrader.types;

import grafiosch.types.ITaskType;

public enum TaskTypeExtended implements ITaskType {
  // Application task types occupy the byte range 30+; the library enum TaskTypeBase owns 1-29
  // (see GitHub issue #205). Values are migrated by V0_36_1__renumber_task_ids.sql.

  /** Historical and intraday price update with price data quality update */
  PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU((byte) 30),
  /** Dividend connector has changed -> Read dividends */
  SECURITY_DIVIDEND_UPDATE_FOR_SECURITY((byte) 31),
  /** Data connector for splits has been changed or triggered by a split calendar event. -> Read splits */
  SECURITY_SPLIT_UPDATE_FOR_SECURITY((byte) 32),
  /** Creation of currencies and possible recreation of holing tables */
  CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO((byte) 33),
  /**
   * Changed currency of tenant and portfolio it needs a creation of currencies and possible recreation of holing tables
   */
  CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO((byte) 34),
  /** Load or reload security price historical data */
  SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA((byte) 35),
  /** The splits of security has changed, rebuild for all clients its security holdings */
  HOLDINGS_SECURITY_REBUILD((byte) 36),
  /** Changed historical currency prices may influence the deposit holdings since it depends on it */
  REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE((byte) 37),
  /** When a split is added it may take some days until the data provider reflect that in adjusted historical prices */
  CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES((byte) 38),
  /** Rebuild Holdings for all tenants, normally one one when the database was created from export */
  REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT((byte) 39),
  /** Load EOD data of an empty currency pair */
  LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES((byte) 40),
  /** Copy the source tenant to the demo accounts */
  COPY_SOURCE_ACCOUNT_TO_DEMO_ACCOUNTS((byte) 41),
  /** Creates the calendar for stock exchanged by a mayor index */
  CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX((byte) 42),
  /** Traces possible new dividends of the instruments through the connectors */
  PERIODICALLY_DIVIDEND_UPDATE_CHECK((byte) 43),
  /** Integrity check over held positions */
  CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST((byte) 44),
  /**
   * Loading historical exchange rates from the European Central Bank (ECB). Should be done daily if possible but not
   * necessarily on weekends.
   */
  LOAD_ECB_CURRENCY_EXCHANGE_RATES((byte) 45),
  /** Checks whether a connector for historical price data may no longer be working. */
  MONITOR_HISTORICAL_PRICE_DATA((byte) 46),
  /** Checks whether a connector for intraday price data may no longer be working. */
  MONITOR_INTRADAY_PRICE_DATA((byte) 47),
  /** Stores the determined values of the user-defined fields of user 0. */
  UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES((byte) 48),
  /** Resets retry counters (history and intra) for connector(s) on active instruments. */
  RESET_CONNECTOR_RETRY_COUNTERS((byte) 49),
  /** Evaluates indicator-based algo alert conditions (MA crossing, RSI, expression) */
  ALGO_ALARM_INDICATOR_EVALUATION((byte) 50),
  /** Imports securities from GTNet peers for GTNetSecurityImpHead positions */
  GTNET_SECURITY_IMPORT_POSITIONS((byte) 51),
   /** Processes due standing orders, creating transactions for the previous day */
  STANDING_ORDER_EXECUTION((byte) 52),

  // Task which used oldValueNumber or oldValueString can not created by the admin
  ///////////////////////////////////////////////////////////////////////////////

  // Update from V_0
  UPD_V_0_11_0((byte) 90),

  // Create dividends from dividend table
  UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE((byte) 100);

  private final Byte value;

  private TaskTypeExtended(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  @Override
  public Enum<TaskTypeExtended>[] getValues() {
    return TaskTypeExtended.values();
  }

}
