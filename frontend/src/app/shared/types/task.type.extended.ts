/**
 * Extended task types specific to the Grafioschtrader application.
 * These task types implement Grafioschtrader-specific background jobs
 * for portfolio management, price data updates, and financial calculations.
 *
 * Numbering: application normal tasks occupy 30-79; the library enum (TaskTypeBase) owns 1-29.
 * System tasks that must not be user-created live in the shared 80+ band, above the maxUserCreateTask
 * threshold (see GitHub issue #205).
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/TaskTypeExtended.java
 */
export enum TaskTypeExtended {
  /** Historical and intraday price update with price data quality update */
  PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU = 30,

  /** Dividend connector has changed -> Read dividends */
  SECURITY_DIVIDEND_UPDATE_FOR_SECURITY = 31,

  /** Data connector for splits has been changed or triggered by a split calendar event -> Read splits */
  SECURITY_SPLIT_UPDATE_FOR_SECURITY = 32,

  /** Creation of currencies and possible recreation of holding tables */
  CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO = 33,

  /** Changed currency of tenant and portfolio - needs creation of currencies and possible recreation of holding tables */
  CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO = 34,

  /** Load or reload security price historical data */
  SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA = 35,

  /** The splits of security has changed, rebuild for all clients its security holdings */
  HOLDINGS_SECURITY_REBUILD = 36,

  /** Changed historical currency prices may influence the deposit holdings since it depends on it */
  REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE = 37,

  /** When a split is added it may take some days until the data provider reflect that in adjusted historical prices */
  CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES = 38,

  /** Rebuild Holdings for all tenants, normally only one when the database was created from export */
  REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT = 39,

  /** Load EOD data of an empty currency pair */
  LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES = 40,

  /** Copy the source tenant to the demo accounts */
  COPY_SOURCE_ACCOUNT_TO_DEMO_ACCOUNTS = 41,

  /** Creates the calendar for stock exchanged by a major index */
  CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX = 42,

  /** Traces possible new dividends of the instruments through the connectors */
  PERIODICALLY_DIVIDEND_UPDATE_CHECK = 43,

  /** Integrity check over held positions */
  CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST = 44,

  /** Loading historical exchange rates from the European Central Bank (ECB) - should be done daily if possible */
  LOAD_ECB_CURRENCY_EXCHANGE_RATES = 45,

  /** Checks whether a connector for historical price data may no longer be working */
  MONITOR_HISTORICAL_PRICE_DATA = 46,

  /** Checks whether a connector for intraday price data may no longer be working */
  MONITOR_INTRADAY_PRICE_DATA = 47,

  /** Stores the determined values of the user-defined fields of user 0 */
  UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES = 48,

  /** Resets retry counters (history and intra) for connector(s) on active instruments */
  RESET_CONNECTOR_RETRY_COUNTERS = 49,

  /** Evaluates indicator-based algo alert conditions (MA crossing, RSI, expression) */
  ALGO_ALARM_INDICATOR_EVALUATION = 50,

  /** Imports securities from GTNet peers for GTNetSecurityImpHead positions */
  GTNET_SECURITY_IMPORT_POSITIONS = 51,

  /** Processes due standing orders, creating transactions for the previous day */
  STANDING_ORDER_EXECUTION = 52,

  // System tasks (80+ band): use oldValueNumber/oldValueString and cannot be created by a user

  /** Update task migrated from V_0 */
  UPD_V_0_11_0 = 90,

  /** Create transactions from the dividend table (unofficial) */
  UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE = 100
}
