/**
 * Extended task types specific to the Grafioschtrader application.
 * These task types implement Grafioschtrader-specific background jobs
 * for portfolio management, price data updates, and financial calculations.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/TaskTypeExtended.java
 */
export enum TaskTypeExtended {
  /** Historical and intraday price update with price data quality update */
  PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU = 0,

  /** Dividend connector has changed -> Read dividends */
  SECURITY_DIVIDEND_UPDATE_FOR_SECURITY = 1,

  /** Data connector for splits has been changed or triggered by a split calendar event -> Read splits */
  SECURITY_SPLIT_UPDATE_FOR_SECURITY = 2,

  /** Creation of currencies and possible recreation of holding tables */
  CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO = 3,

  /** Changed currency of tenant and portfolio - needs creation of currencies and possible recreation of holding tables */
  CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO = 4,

  /** Load or reload security price historical data */
  SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA = 5,

  /** The splits of security has changed, rebuild for all clients its security holdings */
  HOLDINGS_SECURITY_REBUILD = 6,

  /** Changed historical currency prices may influence the deposit holdings since it depends on it */
  REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE = 7,

  /** When a split is added it may take some days until the data provider reflect that in adjusted historical prices */
  CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES = 8,

  /** Rebuild Holdings for all tenants, normally only one when the database was created from export */
  REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT = 9,

  /** Load EOD data of an empty currency pair */
  LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES = 10,

  /** Copy the source tenant to the demo accounts */
  COPY_SOURCE_ACCOUNT_TO_DEMO_ACCOUNTS = 11,

  /** Creates the calendar for stock exchanged by a major index */
  CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX = 12,

  /** Traces possible new dividends of the instruments through the connectors */
  PERIODICALLY_DIVIDEND_UPDATE_CHECK = 13,

  /** Integrity check over held positions */
  CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST = 14,

  /** Loading historical exchange rates from the European Central Bank (ECB) - should be done daily if possible */
  LOAD_ECB_CURRENCY_EXCHANGE_RATES = 16,

  /** Checks whether a connector for historical price data may no longer be working */
  MONITOR_HISTORICAL_PRICE_DATA = 17,

  /** Checks whether a connector for intraday price data may no longer be working */
  MONITOR_INTRADAY_PRICE_DATA = 18,

  /** Stores the determined values of the user-defined fields of user 0 */
  UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES = 19,

  GTNET_SERVER_STATUS_CHECK = 20,

  RESET_CONNECTOR_RETRY_COUNTERS = 21,

  /** Aggregates GTNet exchange log entries from shorter to longer periods. */
  GTNET_EXCHANGE_LOG_AGGREGATION = 22,

  /** Synchronizes GTNetExchange configurations with GTNet peers to update GTNetSupplierDetail entries. */
  GTNET_EXCHANGE_SYNC = 23,

  /** Broadcasts settings changes (maxLimit, acceptRequest, serverState, dailyRequestLimit) to all GTNet peers. */
  GTNET_SETTINGS_BROADCAST = 24,

  GTNET_FUTURE_MESSAGE_DELIVERY = 25,

  GTNET_SECURITY_IMPORT_POSITIONS = 27,


  // Update tasks
  UPD_V_0_11_0 = 51,

  // Unofficial tasks
  UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE = 100
}
