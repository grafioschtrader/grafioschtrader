export class AppSettings {

  // Keywords
  public static readonly API_ENDPOINT = '/api/';
  public static readonly ACTUATOR = 'actuator';
  public static readonly ALGO_TOP_KEY = 'algotop';
  public static readonly ALGO_ASSETCLASS = 'algoassetclass';
  public static readonly ALGO_SECURITY = 'algosecurity';
  public static readonly ALGO_STRATEGY = 'algostrategy';
  public static readonly ALGO_STRATEGY_DEFINITION = 'algostrategydefinition';
  public static readonly LOGIN_KEY = 'login';
  public static readonly REGISTER_KEY = 'register';
  public static readonly TOKEN_VERIFY_KEY = 'tokenverify';
  public static readonly ASSETCLASS_KEY = 'assetclass';
  public static readonly WATCHLIST_KEY = 'watchlist';
  public static readonly SECURITY_DIVIDEND_KEY = 'dividend';
  public static readonly MAIL_INBOX_KEY = 'mailinbox';
  public static readonly MAIL_SENDBOX_KEY = 'mailsendbox';
  public static readonly MAIL_SHOW_MESSAGE_KEY = 'mailmessage';
  public static readonly USERMESSAGE_KEY = 'usermessage';
  public static readonly WATCHLIST_TAB_MENU_KEY = 'wachtlistTabMenu';
  public static readonly WATCHLIST_PERFORMANCE_KEY = 'watchlistperformance';
  public static readonly WATCHLIST_PRICE_FEED_KEY = 'watchlistpricefeed';
  public static readonly WATCHLIST_DIVIDEND_SPLIT_FEED_KEY = 'watchlistdividendsplitfeed';
  public static readonly TRANSACTION_KEY = 'transaction';
  public static readonly TENANT_KEY = 'tenant';
  public static readonly HOLDING_KEY = 'holding';
  public static readonly STOCKEXCHANGE_KEY = 'stockexchange';
  public static readonly SECURITY_KEY = 'security';
  public static readonly SECURITY_SPLIT_KEY = 'securitysplit';
  public static readonly SECURITYACCOUNT_KEY = 'securityaccount';
  public static readonly TRADING_CALENDAR_GLOBAL_KEY = 'tradingcalendarglobal';
  public static readonly SECURITY_HISTORY_QUALITY_KEY = 'historyquotequality';
  public static readonly TRADING_DAYS_MINUS_KEY = 'tradingdaysminus';
  public static readonly TRADING_DAYS_PLUS_KEY = 'tradingdaysplus';
  public static readonly IMPORT_TRANSACTION_HEAD_KEY = 'importtransactionhead';
  public static readonly IMPORT_TRANSACTION_POS_KEY = 'importtransactionpos';
  public static readonly TRADING_PLATFORM_PLAN_KEY = 'tradingplatformplan';
  public static readonly IMP_TRANS_PLATFORM_KEY = 'importtransactionplatform';
  public static readonly IMP_TRANS_TEMPLATE_KEY = 'importtransactiontemplate';
  public static readonly CASHACCOUNT_KEY = 'cashaccount';
  public static readonly CURRENCYPAIR_KEY = 'currencypair';
  public static readonly CURRENCY_KEY = 'currency';
  public static readonly PORTFOLIO_KEY = 'portfolio';
  public static readonly PROPOSE_CHANGE_ENTITY_KEY = 'proposechangeentity';
  public static readonly PROPOSE_CHANGE_YOUR_PROPOSALE_KEY = 'proposeyourproposale';
  public static readonly PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY = 'proposerequestforyou';
  public static readonly ACCOUNT_KEY = 'account';
  public static readonly DEPOT_KEY = 'depot';
  public static readonly PERFORMANCE_KEY = 'performance';
  public static readonly PERFORMANCE_TAB_KEY = 'performancetabmenu';
  public static readonly EOD_DATA_QUALITY_KEY = 'eoddataquality';
  public static readonly DEPOT_CASH_KEY = 'depotcash';
  public static readonly HISTORYQUOTE_KEY = 'historyquote';
  public static readonly HISTORYQUOTE_P_KEY = 'historyquotes';
  public static readonly HISTORYQUOTE_PERIOD_KEY = 'historyquoteperiod';
  public static readonly GLOBALPARAMETERS_P_KEY = 'globalparameters';
  public static readonly CURRENCIES_P_KEY = 'currencies';
  public static readonly TIMESZONES_P_KEY = 'timezones';
  public static readonly LOCALES_P_KEY = 'locales';
  public static readonly USER_KEY = 'user';
  public static readonly USER_ADMIN_KEY = 'useradmin';
  public static readonly USER_ENTITY_LIMIT_KEY = 'userentitychangelimit';
  public static readonly PROPOSE_USER_TASK_KEY = 'proposeusertask';

  public static readonly TIME_SERIE_QUOTES = 'timeSerieQuotes';
  public static readonly CHART_GENERAL_PURPOSE = 'chartgeneralpurpose';

  public static readonly MAINVIEW_KEY = 'mainview';
  public static readonly MAIN_BOTTOM = 'mainbottom';
  public static readonly PORTFOLIO_SUMMARY_KEY = 'portfolioSummary';
  public static readonly PORTFOLIO_TRANSACTION_KEY = 'portfoliotransaction';
  public static readonly TENANT_TAB_MENU_KEY = 'tenantTabMenu';
  public static readonly PROPOSE_CHANGE_TAB_MENU_KEY = 'proposeChangeTabMenu';
  public static readonly PORTFOLIO_TAB_MENU_KEY = 'portfolioTabMenu';
  public static readonly SECURITYACCOUNT_TAB_MENU_KEY = 'securityaccountTabMenu';
  public static readonly SECURITYACCOUNT_IMPORT_KEY = 'securityaccountImport';
  public static readonly DIVIDENDS_ROUTER_KEY = 'portfolioDividens';
  public static readonly TRANSACTION_COST_KEY = 'portfoliotranscost';
  public static readonly STRATEGY_KEY = 'strategy';
  public static readonly STRATEGY_OVERVIEW_KEY = 'strategyoverview';
  public static readonly TENANT_TRANSACTION = 'tenanttransaction';
  public static readonly CASHACCOUNT_DETAIL_ROUTE_KEY = 'cashaccountDetail';
  public static readonly SECURITYACCOUNT_SUMMERY_ROUTE_KEY = 'securityaccountSummery';

  public static readonly SECURITYACCOUNT_EMPTY_ROUTE_KEY = 'securityaccountEmpty';
  public static readonly SECURITYACCOUNT_SUMMERIES_ROUTE_KEY = 'securityaccountSummeries';

  // Special key words
  public static readonly PREFIX_ALGO_FIELD = 'ALGO_F_';


  // HELP Support
  public static readonly HELP_DOMAIN = '//hugograf.github.io/grafioschtrader';


  // Native formats
  public static readonly FORMAT_DATE_SHORT_NATIVE = 'YYYY-MM-DD';

  public static readonly DEFAULT_LANGUAGE = 'en';

  // Name for Icons
  public static readonly ICONNAME_SQUARE_EMTPY = 'fa fa-square-o';
  public static readonly ICONNAME_SQUARE_CHECK = 'fa fa-check-square-o';

  public static readonly ICONNAME_CHECK = 'fa fa-check';

  public static readonly ICONNAME_CIRCLE_EMTPY = 'fa fa-circle-o';
  public static readonly ICONNAME_CIRCLE_CHECK = 'fa fa-dot-circle-o';

  // Save table configuration in local storage
  public static readonly WATCHLIST_PERFORMANCE_TABLE_SETTINGS_STORE = 'u_watchlist_performance_0';
  public static readonly WATCHLIST_PRICE_FEED_TABLE_SETTINGS_STORE = 'u_watchlist_price_feed_0';
  public static readonly WATCHLIST_DIVIDEND_SPLIT_FEED_TABLE_SETTINGS_STORE = 'u_watchlist_dividend_split_feed_0';
  public static readonly IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE = 'u_importtransactionpos_1';
  public static readonly HISTORYQUOTE_TABLE_SETTINGS_STORE = 'u_historyquote_01';

  // Save others in local storage
  public static readonly TA_INDICATORS_STORE = 'ta_indicator_';
  public static readonly DIV_SECURITYACCOUNTS = 'div_securityaccounts';
  public static readonly DIV_CASHACCOUNTS = 'div_cashaccounts';
  public static readonly HIST_SUPPORTED_CSV_FORMAT = 'hist_supported_csv_format';

  static readonly PATH_ASSET_ICONS = 'assets/icons/';

  // Some definitions of property names which are used in more than one class
  public static readonly VALUE_SECURITY_MAIN_CURRENCY_FIELD = 'accountValueSecurityMC';
  public static readonly SUCCESS_FAILED_IMP_TRANS = 'successFailedDirectImpTran';
  public static readonly VALUE_SECURITY_ACCOUNT_HEADER = 'ACCOUNT_RELEVANT';

  public static readonly DIVIDEND_SETTINGS = 'DIVIDEND_SETTINGS';
  public static  readonly SPLIT_SETTINGS = 'SPLIT_SETTING';

  // User Rights
  public static readonly ROLE_ADMIN = 'ROLE_ADMIN';
  public static readonly ROLE_ALL_EDIT = 'ROLE_ALLEDIT';
  public static readonly ROLE_USER = 'ROLE_USER';
  public static readonly ROLE_LIMIT_EDIT = 'ROLE_LIMITEDIT';

  public static readonly DIALOG_MENU_SUFFIX = '...';

  public static readonly INSTRUMENT_HEADER = 'I';
}
