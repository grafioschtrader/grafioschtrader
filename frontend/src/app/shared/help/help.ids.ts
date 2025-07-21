export enum HelpIds {

  // Intro
  HELP_INTRO = 'intro',
  HELP_INTRO_REGISTER = 'intro/register',
  HELP_INTRO_NAVIGATION = 'intro/userinterface',
  HELP_INTRO_PROPOSE_CHANGE_ENTITY = 'basedata',

  // Tenant, portfolio, security and cash account
  HELP_CLIENT = 'tenantportfolio/client',
  HELP_PORTFOLIO = 'tenantportfolio/portfolio',
  HELP_PORTFOLIO_ACCOUNT = 'tenantportfolio/cashaccount',
  HELP_PORTFOLIO_SECURITYACCOUNT = 'tenantportfolio/securityaccounts',
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT = 'tenantportfolio/securityaccounts/transactionimport',

  // Tenant and  Portfolios / Reports
  HELP_PORTFOLIOS = 'reportportfolio',
  HELP_PORTFOLIOS_PORTFOLIOS = 'reportportfolio/portfolios',
  HELP_PORTFOLIOS_PERIODPERFORMANCE = 'reportportfolio/periodperformance',
  HELP_PORTFOLIOS_SECURITY_ACCOUNT_REPORT = 'reportportfolio/securityaccountreport',
  HELP_PORTFOLIOS_DIVIDENDS = 'reportportfolio/dividends',
  HELP_PORTFOLIOS_TRANSACTIONCOSTS = 'reportportfolio/transactioncosts',
  HELP_PORTFOLIOS_TRANSACTIONLIST = 'reportportfolio/transactionlist',

  // Algo trading
  HELP_ALGO = 'algo',
  HELP_ALGO_RULE = 'algo/rule',
  HELP_ALGO_STRATEGY = 'algo/strategy',

  // Watchlist
  HELP_WATCHLIST = 'watchlistinstrument',
  HELP_WATCHLIST_CORRELATION = HELP_WATCHLIST + '/correlation',
  HELP_WATCHLIST_WATCHLIST = HELP_WATCHLIST + '/watchlist',
  HELP_WATCHLIST_PERFORMANCE = HELP_WATCHLIST_WATCHLIST + '/performance',
  HELP_WATCHLIST_UDF = HELP_WATCHLIST_WATCHLIST + '/udf',
  HELP_WATCHLIST_PRICE_FEED = HELP_WATCHLIST_WATCHLIST + '/pricefeed',
  HELP_WATCHLIST_DIVIDEND_SPLIT_FEED = HELP_WATCHLIST_WATCHLIST + '/dividendsplit',
  HELP_WATCHLIST_CURRENCYPAIR = 'watchlistinstrument/instrument/currencypair',
  HELP_WATCHLIST_SECURITY = 'watchlistinstrument/instrument/securityderived',
  HELP_WATCHLIST_SEARCHDIALOG = 'watchlistinstrument/instrument/searchdialog',
  HELP_WATCHLIST_DERIVED_INSTRUMENT = 'watchlistinstrument/instrument/securityderived/derivedinstrument',
  HELP_WATCHLIST_HISTORYQUOTES = 'watchlistinstrument/externaldata/historyquote/pricedata',
  HELP_WATCHLIST_HISTORYQUOTES_CHART = 'watchlistinstrument/eodchart',
  HELP_WATCHLIST_WITHOUT_PRICE_DATA = 'watchlistinstrument/instrument/securityderived/security/securitywithoutpricedata',

  // Transaction
  HELP_TRANSACTION_ACCOUNT = 'transaction/account',
  HELP_TRANSACTION_CASH_BASED = 'transaction/security/cashbased',
  HELP_TRANSACTION_MARGIN_BASED = 'transaction/security/marginbased',

  // Base data
  HELP_BASEDATA_ASSETCLASS = 'basedata/instrumentbased/assetclass',
  HELP_BASEDATA_STOCKEXCHANGE = 'basedata/instrumentbased/stockexchange',
  HELP_BASEDATA_TRADING_PLATFORM_PLAN = 'basedata/tradingplatformplan',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP = 'basedata/imptranstemplate',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_TEMPLATE = 'basedata/imptranstemplate/createimptranstemplate',
  HELP_BASEDATA_UDF_METADATA_GENERAL = 'basedata/udfmetadata',
  HELP_BASEDATA_UDF_METADATA_SECURITY = 'basedata/udfmetadata/instruments',

  // Admin data
  HELP_MESSAGE_SYSTEM = 'admindata',
  HELP_TRADING_CALENDAR = 'admindata/tradingcalendar',
  HELP_HISTORYQUOTE_QUALITY = 'admindata/historyquotequality/',
  HELP_GLOBAL_SETTINGS = 'admindata/globalsettings',
  HELP_GT_NET = 'admindata/gtnet',
  HELP_GT_NET_AUTOANSWER = 'admindata/gtnet/autoanswer',
  HELP_GT_NET_CONSUME_MONITOR = 'admindata/gtnet/consumemonitor',
  HELP_TASK_DATA_CHANGE_MONITOR = 'admindata/taskdatachangemonitor',
  HELP_CONNECTOR_API_KEY = 'admindata/connectorapikey',
  HELP_USER = 'admindata/user'

}
