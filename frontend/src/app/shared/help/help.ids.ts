export enum HelpIds {

  // Intro
  HELP_INTRO = 'intro',
  HELP_INTRO_REGISTER = 'intro/register',
  HELP_INTRO_NAVIGATION = 'intro/userinterface',
  HELP_INTRO_PROPOSE_CHANGE_ENTITY = 'proposechangeentity',

  // Tenant, portfolio, security and cash account
  HELP_CLIENT = 'tenantportfolio/client',
  HELP_PROTFOLIO = 'tenantportfolio/portfolio',
  HELP_PORTFOLIO_ACCOUNT = 'tenantportfolio/cashaccount',
  HELP_PORTFOLIO_SECURITYACCOUNT = 'tenantportfolio/securityaccounts',
  HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT = 'tenantportfolio/securityaccounts/transactionimport',

  // Tenant and  Portfolios / Reports
  HELP_PROTFOLIOS = 'reportportfolio',
  HELP_PROTFOLIOS_PORTFOLIOS = 'reportportfolio/portfolios',
  HELP_PORTFOLIOS_PERIODPERFORMANCE = 'reportportfolio/periodperformance',
  HELP_PROTFOLIOS_SECURITY_ACCOUNT_REPORT = 'reportportfolio/securityaccountreport',
  HELP_PROTFOLIOS_DIVIDENDS = 'reportportfolio/dividends',
  HELP_PORTFOLIOS_TRANSACTIONCOSTS = 'reportportfolio/transactioncosts',
  HELP_PORTFOLIOS_TRANSACTIONLIST = 'reportportfolio/transactionlist',

  // Algo trading
  HELP_ALGO = 'algo',
  HELP_ALGO_RULE = 'algo/rule',
  HELP_ALOG_STRATEGY = 'algo/strategy',

  // Watchlist
  HELP_WATCHLIST = 'watchlistinstrument',
  HELP_WATCHLIST_WATCHLIST = 'watchlistinstrument/watchlist',
  HELP_WATCHLIST_PERFORMANCE = 'watchlistinstrument/watchlist/performance',
  HELP_WATCHLIST_PRICE_FEED = 'watchlistinstrument/watchlist/pricefeed',
  HELP_WATCHLIST_DIVIDEND_SPLIT_FEED = 'watchlistinstrument/watchlist/dividendsplit',
  HELP_WATCHLIST_CURRENCYPAIR = 'watchlistinstrument/instrument/currencypair',
  HELP_WATCHLIST_SECURITY = 'watchlistinstrument/instrument/security',
  HELP_WATCHLIST_SEARCHDIALOG= 'watchlistinstrument/instrument/searchdialog',
  HELP_WATCHLIST_DERIVED_INSTRUMENT = 'watchlistinstrument/instrument/security/derivedinstrument',
  HELP_WATCHLIST_HISTORYQUOTES = 'watchlistinstrument/historyquote/pricedata',
  HELP_WATCHLIST_HISTORYQUOTES_CHART = 'watchlistinstrument/eodchart',

  // Transaction
  HELP_TRANSACTION_ACCOUNT = 'transaction/account',
  HELP_TRANSACTION_CASH_BASED = 'transaction/security/cashbased',
  HELP_TRANSACTION_MARGIN_BASED = 'transaction/security/marginbased',

  // Base data
  HELP_BASEDATA_ASSETCLASS = 'basedata/assetclass',
  HELP_BASEDATA_STOCKEXCHANGE = 'basedata/stockexchange',
  HELP_BASEDATA_TRADING_PLATFORM_PLAN = 'basedata/tradingplatformplan',
  HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE = 'basedata/imptranstemplate',

  // Admin data
  HELP_MESSAGE_SYSTEM = 'masseage',
  HELP_TRADING_CALENDAR = 'admindata/tradingcalendar',
  HELP_HISTORYQUOTE_QUALITY = 'historyquotequality',
  HELP_USER = 'admindata/user'

}
