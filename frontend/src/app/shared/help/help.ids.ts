import { HelpIds } from '../../lib/help/help.ids';
import { TaskTypeExtended } from '../types/task.type.extended';

/**
 * Application-specific (Grafioschtrader) help IDs.
 * These help IDs are specific to the Grafioschtrader application.
 * They will be registered at application startup via registerHelpIds().
 */
export const AppHelpIds: Record<string, string> = {
  // Tenant, portfolio, security and cash account
  HELP_CLIENT: 'tenantportfolio/client',
  HELP_PORTFOLIO: 'tenantportfolio/portfolio',
  HELP_PORTFOLIO_ACCOUNT: 'tenantportfolio/cashaccount',
  HELP_PORTFOLIO_SECURITYACCOUNT: 'tenantportfolio/securityaccounts',
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT:
    'tenantportfolio/securityaccounts/transactionimport/viewtransactionimport',
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT_GTNET:
    'tenantportfolio/securityaccounts/transactionimport/securityimportfortransaction',

  // Tenant and  Portfolios / Reports
  HELP_PORTFOLIOS: 'reportportfolio',
  HELP_PORTFOLIOS_PORTFOLIOS: 'reportportfolio/portfolios',
  HELP_PORTFOLIOS_PERIODPERFORMANCE: 'reportportfolio/periodperformance',
  HELP_PORTFOLIOS_SECURITY_ACCOUNT_REPORT: 'reportportfolio/securityaccountreport',
  HELP_PORTFOLIOS_SECURITY_CASH_ACCOUNT_REPORT: 'reportportfolio/securitycashaccountreport',
  HELP_PORTFOLIOS_DIVIDENDS: 'reportportfolio/dividends',
  HELP_PORTFOLIOS_DIVIDENDS_CHARTS: 'reportportfolio/dividends/charts',
  HELP_PORTFOLIOS_TRANSACTIONCOSTS: 'reportportfolio/transactioncosts',
  HELP_PORTFOLIOS_TRANSACTIONLIST: 'reportportfolio/transactionlist',

  // Standing orders
  HELP_STANDING_ORDER: 'transaction/standingorder',
  HELP_STANDING_ORDER_CASH: 'transaction/standingorder/cash',
  HELP_STANDING_ORDER_SECURITY: 'transaction/standingorder/security',

  // Algo trading
  HELP_ALGO: 'algoalert',
  /** The strategy tree itself: creating a strategy, its allocation and its simulation environments. */
  HELP_ALGO_TREE: 'algoalert/algo',
  HELP_ALGO_STRATEGY: 'algoalert/strategy',
  /** The historical replay of a simulation environment: starting a run, its options, results and course. */
  HELP_ALGO_HISTORICAL_RUN: 'algoalert/historicalrun',
  /**
   * The YAML simulation tax model of a tax country. It is edited from the tax data view but documented with the
   * historical replay, the only consumer of the model.
   */
  HELP_ALGO_HISTORICAL_RUN_TAX_MODEL: 'algoalert/historicalrun/taxmodel/',
  /** The alert overview and how alerts are evaluated, deduplicated and delivered. */
  HELP_ALGO_ALERT: 'algoalert/alert',

  // Watchlist
  HELP_WATCHLIST: 'watchlistinstrument',
  HELP_WATCHLIST_CORRELATION: 'watchlistinstrument/correlation',
  HELP_WATCHLIST_SEASONALITY_HEATMAP: 'watchlistinstrument/seasonalityheatmap',
  HELP_WATCHLIST_WATCHLIST: 'watchlistinstrument/watchlist',
  HELP_WATCHLIST_PERFORMANCE: 'watchlistinstrument/watchlist/performance',
  HELP_WATCHLIST_INDICATOR: 'watchlistinstrument/watchlist/indicator',
  HELP_WATCHLIST_UDF: 'watchlistinstrument/watchlist/udf',
  HELP_WATCHLIST_PRICE_FEED: 'watchlistinstrument/watchlist/pricefeed',
  HELP_WATCHLIST_DIVIDEND_SPLIT_FEED: 'watchlistinstrument/watchlist/dividendsplit',
  HELP_WATCHLIST_CURRENCYPAIR: 'watchlistinstrument/instrument/currencypair',
  HELP_WATCHLIST_SECURITY: 'watchlistinstrument/instrument/securityderived',
  HELP_WATCHLIST_SEARCHDIALOG: 'watchlistinstrument/instrument/searchdialog',
  HELP_WATCHLIST_DERIVED_INSTRUMENT: 'watchlistinstrument/instrument/securityderived/derivedinstrument',
  HELP_WATCHLIST_HISTORYQUOTES: 'watchlistinstrument/externaldata/historyquote/pricedata',
  HELP_WATCHLIST_HISTORYQUOTES_LEGACY: 'watchlistinstrument/externaldata/historyquote/pricedata/archive',
  HELP_WATCHLIST_HISTORYQUOTES_CHART: 'watchlistinstrument/eodchart',
  HELP_WATCHLIST_WITHOUT_PRICE_DATA: 'watchlistinstrument/instrument/securityderived/security/securitywithoutpricedata',

  // Transaction
  HELP_TRANSACTION_ACCOUNT: 'transaction/account',
  HELP_TRANSACTION_CASH_BASED: 'transaction/security/cashbased',
  HELP_TRANSACTION_MARGIN_BASED: 'transaction/security/marginbased',

  // Base data (application-specific extensions)
  HELP_BASEDATA_ASSETCLASS: 'basedata/instrumentbased/assetclass',
  HELP_BASEDATA_STOCKEXCHANGE: 'basedata/instrumentbased/stockexchange',
  HELP_BASEDATA_TRADING_CALENDAR_RULE: 'basedata/instrumentbased/stockexchange/tradingcalendarruleset/',
  HELP_BASEDATA_TRADING_PLATFORM_PLAN: 'basedata/tradingplatformplan',
  HELP_BASEDATA_GENERIC_CONNECTOR: 'basedata/genericconnector',
  HELP_BASEDATA_GENERIC_CONNECTOR_ENDPOINTS: 'basedata/genericconnector/endpoints',

  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP: 'basedata/imptranstemplate',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_TEMPLATE: 'basedata/imptranstemplate/createimptranstemplate',
  HELP_BASEDATA_BANKRUPT_SECURITY: 'basedata/bankruptsecurity/',
  HELP_BASEDATA_RISK_FREE_RATE_MAPPING: 'basedata/riskfreeratemapping',
  HELP_BASEDATA_UDF_METADATA_SECURITY: 'basedata/udfmetadata/instruments',
  HELP_BASEDATA_GT_NET_IMPORT_SECURITY: 'basedata/gtnetsecurityimport',
  HELP_BASEDATA_SECURITY_ACTION: 'basedata/securityaction',
  HELP_BASEDATA_SECURITY_ACTION_SECURITY_TRANSFER: 'basedata/securityaction/securitytransfer',
  HELP_BASEDATA_SECURITY_ACTION_ISIN_RENAME: 'basedata/securityaction/securityisinrename',

  // Admin data (application-specific extensions)
  HELP_TRADING_CALENDAR: 'admindata/tradingcalendar',
  HELP_HISTORYQUOTE_QUALITY: 'admindata/historyquotequality/',
  HELP_TAX_DATA: 'admindata/taxdata/',
  HELP_TASK_DESCRIPTION_PRICE_DATA: HelpIds.HELP_TASK_DESCRIPTION + '/pricedata',
  HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND: HelpIds.HELP_TASK_DESCRIPTION + '/splitdividend',

  // The GTNet landing page is the architectural overview of the network; it describes no single view. Every screen
  // therefore points at the page that documents it, HELP_GT_NET_SETUP being the one for the server overview, its
  // dialogs and the message history.
  HELP_GT_NET: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet',
  HELP_GT_NET_SETUP: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet/setup',
  HELP_GT_NET_ADMIN_MGS: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet/setup/msgadmin',
  HELP_GT_NET_AUTOANSWER: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet/autoanswer',
  HELP_GT_NET_EXCHANGE: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet/exchange',
  HELP_GT_NET_EXCHANGE_LOG: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet/exchangelog'
};

/**
 * Help page of every application task type that can be created in the batch processing monitor. Registered at
 * startup via registerTaskTypeHelpIds(); a task type described on the main page maps to HELP_TASK_DESCRIPTION.
 */
export const AppTaskTypeHelpIds: Record<number, string> = {
  [TaskTypeExtended.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY]: AppHelpIds.HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND,
  [TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY]: AppHelpIds.HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND,
  [TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.HOLDINGS_SECURITY_REBUILD]: AppHelpIds.HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND,
  [TaskTypeExtended.REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES]: AppHelpIds.HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND,
  [TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.COPY_SOURCE_ACCOUNT_TO_DEMO_ACCOUNTS]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.PERIODICALLY_DIVIDEND_UPDATE_CHECK]: AppHelpIds.HELP_TASK_DESCRIPTION_SPLIT_DIVIDEND,
  [TaskTypeExtended.CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.LOAD_ECB_CURRENCY_EXCHANGE_RATES]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.MONITOR_HISTORICAL_PRICE_DATA]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.MONITOR_INTRADAY_PRICE_DATA]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.UDF_FILL_VALUES]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.RESET_CONNECTOR_RETRY_COUNTERS]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.ALGO_ALARM_INDICATOR_EVALUATION]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.GTNET_SECURITY_IMPORT_POSITIONS]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeExtended.STANDING_ORDER_EXECUTION]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.HOLD_TABLE_CONSISTENCY_CHECK]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.HOLD_DAILY_TOTAL_UPDATE]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeExtended.FUNDAMENTAL_DATA_LOAD]: AppHelpIds.HELP_TASK_DESCRIPTION_PRICE_DATA,
  [TaskTypeExtended.UDF_USER_SUGGEST_VALUES]: HelpIds.HELP_TASK_DESCRIPTION
};
