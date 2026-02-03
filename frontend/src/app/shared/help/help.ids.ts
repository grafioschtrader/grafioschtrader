import {HelpIds} from '../../lib/help/help.ids';

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
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT: 'tenantportfolio/securityaccounts/transactionimport/viewtransactionimport',
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT_GTNET: 'tenantportfolio/securityaccounts/transactionimport/securityimportfortransaction',

  // Tenant and  Portfolios / Reports
  HELP_PORTFOLIOS: 'reportportfolio',
  HELP_PORTFOLIOS_PORTFOLIOS: 'reportportfolio/portfolios',
  HELP_PORTFOLIOS_PERIODPERFORMANCE: 'reportportfolio/periodperformance',
  HELP_PORTFOLIOS_SECURITY_ACCOUNT_REPORT: 'reportportfolio/securityaccountreport',
  HELP_PORTFOLIOS_SECURITY_CASH_ACCOUNT_REPORT: 'reportportfolio/securitycashaccountreport',
  HELP_PORTFOLIOS_DIVIDENDS: 'reportportfolio/dividends',
  HELP_PORTFOLIOS_TRANSACTIONCOSTS: 'reportportfolio/transactioncosts',
  HELP_PORTFOLIOS_TRANSACTIONLIST: 'reportportfolio/transactionlist',

  // Algo trading
  HELP_ALGO: 'algo',
  HELP_ALGO_RULE: 'algo/rule',
  HELP_ALGO_STRATEGY: 'algo/strategy',

  // Watchlist
  HELP_WATCHLIST: 'watchlistinstrument',
  HELP_WATCHLIST_CORRELATION: 'watchlistinstrument/correlation',
  HELP_WATCHLIST_WATCHLIST: 'watchlistinstrument/watchlist',
  HELP_WATCHLIST_PERFORMANCE: 'watchlistinstrument/watchlist/performance',
  HELP_WATCHLIST_UDF: 'watchlistinstrument/watchlist/udf',
  HELP_WATCHLIST_PRICE_FEED: 'watchlistinstrument/watchlist/pricefeed',
  HELP_WATCHLIST_DIVIDEND_SPLIT_FEED: 'watchlistinstrument/watchlist/dividendsplit',
  HELP_WATCHLIST_CURRENCYPAIR: 'watchlistinstrument/instrument/currencypair',
  HELP_WATCHLIST_SECURITY: 'watchlistinstrument/instrument/securityderived',
  HELP_WATCHLIST_SEARCHDIALOG: 'watchlistinstrument/instrument/searchdialog',
  HELP_WATCHLIST_DERIVED_INSTRUMENT: 'watchlistinstrument/instrument/securityderived/derivedinstrument',
  HELP_WATCHLIST_HISTORYQUOTES: 'watchlistinstrument/externaldata/historyquote/pricedata',
  HELP_WATCHLIST_HISTORYQUOTES_CHART: 'watchlistinstrument/eodchart',
  HELP_WATCHLIST_WITHOUT_PRICE_DATA: 'watchlistinstrument/instrument/securityderived/security/securitywithoutpricedata',

  // Transaction
  HELP_TRANSACTION_ACCOUNT: 'transaction/account',
  HELP_TRANSACTION_CASH_BASED: 'transaction/security/cashbased',
  HELP_TRANSACTION_MARGIN_BASED: 'transaction/security/marginbased',

  // Base data (application-specific extensions)
  HELP_BASEDATA_ASSETCLASS: 'basedata/instrumentbased/assetclass',
  HELP_BASEDATA_STOCKEXCHANGE: 'basedata/instrumentbased/stockexchange',
  HELP_BASEDATA_TRADING_PLATFORM_PLAN: 'basedata/tradingplatformplan',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP: 'basedata/imptranstemplate',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_TEMPLATE: 'basedata/imptranstemplate/createimptranstemplate',
  HELP_BASEDATA_UDF_METADATA_SECURITY: 'basedata/udfmetadata/instruments',
  HELP_BASEDATA_GT_NET_IMPORT_SECURITY: 'basedata/gtnetsecurityimport',

  // Admin data (application-specific extensions)
  HELP_TRADING_CALENDAR: 'admindata/tradingcalendar',
  HELP_HISTORYQUOTE_QUALITY: 'admindata/historyquotequality/',

  HELP_GT_NET: HelpIds.HELP_MESSAGE_SYSTEM + '/gtnet',
  HELP_GT_NET_ADMIN_MGS: HelpIds.HELP_MESSAGE_SYSTEM +  '/gtnet/setup/msgadmin',
  HELP_GT_NET_AUTOANSWER: HelpIds.HELP_MESSAGE_SYSTEM +  '/gtnet/autoanswer',
  HELP_GT_NET_EXCHANGE: HelpIds.HELP_MESSAGE_SYSTEM +  '/gtnet/exchange',
  HELP_GT_NET_EXCHANGE_LOG: HelpIds.HELP_MESSAGE_SYSTEM +  '/gtnet/exchangelog'


};
