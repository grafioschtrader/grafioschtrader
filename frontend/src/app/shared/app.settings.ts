import {GlobalSessionNames} from './global.session.names';
import {GTNetMessage} from '../gtnet/model/gtnet.message';

export class AppSettings {

  // Entities
  public static readonly ALGO_TOP = 'AlgoTop';
  public static readonly ALGO_ASSETCLASS = 'AlgoAssetclass';
  public static readonly ALGO_SECURITY = 'AlgoSecurity';
  public static readonly ALGO_STRATEGY = 'AlgoStrategy';
  public static readonly ASSETCLASS = 'Assetclass';
  public static readonly CASHACCOUNT = 'Cashaccount';
  public static readonly CONNECTOR_API_KEY = 'ConnectorApiKey';
  public static readonly CURRENCYPAIR = 'Currencypair';
  public static readonly DIVIDEND = 'Dividend';
  public static readonly GLOBALPARAMETERS = 'Globalparameters';
  public static readonly GTNET = 'GTNet';
  public static readonly GTNETMESSAGE = 'GTNetMessage';
  public static readonly HISTORYQUOTE = 'Historyquote';
  public static readonly HISTORYQUOTE_PERIOD = 'HistoryquotePeriod';
  public static readonly IMPORT_TRANSACTION_HEAD = 'ImportTransactionHead';
  public static readonly IMPORT_TRANSACTION_PLATFORM = 'ImportTransactionPlatform';
  public static readonly IMPORT_TRANSACTION_POS = 'ImportTransactionPos';
  public static readonly IMPORT_TRANSACTION_TEMPLATE = 'ImportTransactionTemplate';
  public static readonly MAIL_INBOX = 'MailInbox';
  public static readonly MAIL_SENDBOX = 'MailSendbox';
  public static readonly PORTFOLIO = 'Portfolio';
  public static readonly PROPOSE_CHANGE_ENTITY = 'ProposeChangeEntity';
  public static readonly PROPOSE_USER_TASK = 'ProposeUserTask';
  public static readonly SECURITY = 'Security';
  public static readonly SECURITY_SPLIT = 'Securitysplit';
  public static readonly SECURITYACCOUNT = 'Securityaccount';
  public static readonly STOCKEXCHANGE = 'Stockexchange';
  public static readonly TASK_DATE_CHANGE = 'TaskDataChange';
  public static readonly TENANT = 'Tenant';
  public static readonly TRANSACTION = 'Transaction';
  public static readonly TRADING_DAYS_MINUS = 'TradingDaysMinus';
  public static readonly TRADING_DAYS_PLUS = 'TradingDaysPlus';
  public static readonly TRADING_PLATFORM_PLAN = 'TradingPlatformPlan';
  public static readonly USER = 'User';
  public static readonly USER_ENTITY_LIMIT = 'UserEntityChangeLimit';
  public static readonly WATCHLIST = 'Watchlist';

  // Keywords
  public static readonly API_ENDPOINT = '/api/';
  public static readonly ACTUATOR = 'actuator';
  public static readonly ALGO_TOP_KEY = AppSettings.ALGO_TOP.toLowerCase();
  public static readonly ALGO_ASSETCLASS_KEY = AppSettings.ALGO_ASSETCLASS.toLowerCase();
  public static readonly ALGO_SECURITY_KEY = AppSettings.ALGO_SECURITY.toLowerCase();
  public static readonly ALGO_STRATEGY_KEY = AppSettings.ALGO_STRATEGY.toLowerCase();
  public static readonly ALGO_STRATEGY_DEFINITION = 'algostrategydefinition';
  public static readonly LOGIN_KEY = 'login';
  public static readonly REGISTER_KEY = 'register';
  public static readonly TOKEN_VERIFY_KEY = 'tokenverify';
  public static readonly ASSETCLASS_KEY = AppSettings.ASSETCLASS.toLowerCase();
  public static readonly CORRELATION_SET_KEY = 'correlationset';
  public static readonly CORRELATION_CHART = 'correlationchart';
  public static readonly CONNECTOR_API_KEY_KEY = 'connectorapikey';
  public static readonly WATCHLIST_KEY = AppSettings.WATCHLIST.toLowerCase();
  public static readonly SECURITY_DIVIDEND_KEY = AppSettings.DIVIDEND.toLowerCase();
  public static readonly MAIL_INBOX_KEY = AppSettings.MAIL_INBOX.toLowerCase();
  public static readonly MAIL_SENDBOX_KEY = AppSettings.MAIL_SENDBOX.toLowerCase();
  public static readonly MAIL_SHOW_MESSAGE_KEY = 'mailmessage';
  public static readonly USER_MESSAGE_KEY = 'usermessage';
  public static readonly WATCHLIST_TAB_MENU_KEY = 'wachtlistTabMenu';
  public static readonly WATCHLIST_PERFORMANCE_KEY = 'watchlistperformance';
  public static readonly WATCHLIST_PRICE_FEED_KEY = 'watchlistpricefeed';
  public static readonly WATCHLIST_DIVIDEND_SPLIT_FEED_KEY = 'watchlistdividendsplitfeed';
  public static readonly TRANSACTION_KEY = AppSettings.TRANSACTION.toLowerCase();
  public static readonly TENANT_KEY = AppSettings.TENANT.toLowerCase();
  public static readonly HOLDING_KEY = 'holding';
  public static readonly MULTIPLE_REQUEST_TO_ONE_KEY = 'multiplerequesttoone';
  public static readonly STOCKEXCHANGE_KEY = AppSettings.STOCKEXCHANGE.toLowerCase();
  public static readonly SECURITY_KEY = AppSettings.SECURITY.toLowerCase();
  public static readonly SECURITY_SPLIT_KEY = AppSettings.SECURITY_SPLIT.toLowerCase();
  public static readonly SECURITYACCOUNT_KEY = AppSettings.SECURITYACCOUNT.toLowerCase();
  public static readonly TRADING_CALENDAR_GLOBAL_KEY = 'tradingcalendarglobal';
  public static readonly SECURITY_HISTORY_QUALITY_KEY = 'historyquotequality';
  public static readonly GLOBAL_SETTINGS_KEY = 'globalsettings';
  public static readonly TASK_DATA_CHANGE_KEY = 'taskdatachange';
  public static readonly TASK_DATA_CHANGE_MONITOR_KEY = 'taskdatachangemonitor';
  public static readonly GTNET_CONSUME_MONITOR_KEY = 'gtnetconsumemonitor';
  public static readonly GTNET_KEY = 'gtnet';
  public static readonly GTNET_AUTO_ANWSER_KEY = 'gtnetautoanwser';
  public static readonly GTNET_MESSAGE_KEY = 'gtnetmessage';
  public static readonly GTNET_PROVIDER_MONITOR_KEY = 'gtnetprovidermonitor';
  public static readonly TRADING_DAYS_MINUS_KEY = AppSettings.TRADING_DAYS_MINUS.toLowerCase();
  public static readonly TRADING_DAYS_PLUS_KEY = AppSettings.TRADING_DAYS_PLUS.toLowerCase();
  public static readonly IMPORT_TRANSACTION_HEAD_KEY = AppSettings.IMPORT_TRANSACTION_HEAD.toLowerCase();
  public static readonly IMPORT_TRANSACTION_POS_KEY = AppSettings.IMPORT_TRANSACTION_POS.toLowerCase();
  public static readonly TRADING_PLATFORM_PLAN_KEY = AppSettings.TRADING_PLATFORM_PLAN.toLowerCase();
  public static readonly IMP_TRANS_PLATFORM_KEY = AppSettings.IMPORT_TRANSACTION_PLATFORM.toLowerCase();
  public static readonly IMP_TRANS_TEMPLATE_KEY = AppSettings.IMPORT_TRANSACTION_TEMPLATE.toLowerCase();
  public static readonly CASHACCOUNT_KEY = AppSettings.CASHACCOUNT.toLowerCase();
  public static readonly CURRENCYPAIR_KEY = AppSettings.CURRENCYPAIR.toLowerCase();
  public static readonly CURRENCY_KEY = 'currency';
  public static readonly PORTFOLIO_KEY = AppSettings.PORTFOLIO.toLowerCase();
  public static readonly PROPOSE_CHANGE_ENTITY_KEY = AppSettings.PROPOSE_CHANGE_ENTITY.toLowerCase();
  public static readonly PROPOSE_CHANGE_YOUR_PROPOSAL_KEY = 'proposeyourproposal';
  public static readonly PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY = 'proposerequestforyou';
  public static readonly DEPOT_KEY = 'depot';
  public static readonly PERFORMANCE_KEY = 'performance';
  public static readonly PERFORMANCE_TAB_KEY = 'performancetabmenu';
  public static readonly EOD_DATA_QUALITY_KEY = 'eoddataquality';
  public static readonly DEPOT_CASH_KEY = 'depotcash';
  public static readonly HISTORYQUOTE_KEY = AppSettings.HISTORYQUOTE.toLowerCase();
  public static readonly HISTORYQUOTE_P_KEY = AppSettings.HISTORYQUOTE_KEY + 's';
  public static readonly HISTORYQUOTE_PERIOD_KEY = AppSettings.HISTORYQUOTE_PERIOD.toLowerCase();
  public static readonly GLOBALPARAMETERS_P_KEY = AppSettings.GLOBALPARAMETERS.toLowerCase();
  public static readonly CURRENCIES_P_KEY = 'currencies';
  public static readonly TIMESZONES_P_KEY = 'timezones';
  public static readonly LOCALES_P_KEY = 'locales';
  public static readonly USER_KEY = AppSettings.USER.toLowerCase();
  public static readonly USER_ADMIN_KEY = 'useradmin';
  public static readonly USER_ENTITY_LIMIT_KEY = AppSettings.USER_ENTITY_LIMIT.toLowerCase();
  public static readonly PROPOSE_USER_TASK_KEY = AppSettings.PROPOSE_USER_TASK.toLowerCase();

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

  public static readonly OWNER_TEMPLATE = 'owner';

  // HELP Support
  public static readonly HELP_DOMAIN = '//grafioschtrader.github.io/gt-user-manual/';

  // Native formats
  public static readonly FORMAT_DATE_SHORT_NATIVE = 'YYYY-MM-DD';
  public static readonly FORMAT_DATE_SHORT_US = 'YYYYMMDD';

  public static readonly DEFAULT_LANGUAGE = 'en';

  // Name for Icons
  public static readonly ICONNAME_SQUARE_EMTPY = 'fa fa-square-o';
  public static readonly ICONNAME_SQUARE_CHECK = 'fa fa-check-square-o';

  public static readonly ICONNAME_CHECK = 'fa fa-check';

  public static readonly ICONNAME_CIRCLE_EMTPY = 'fa fa-circle-o';
  public static readonly ICONNAME_CIRCLE_CHECK = 'fa fa-dot-circle-o';

  // Save table configuration in local storage
  public static readonly WATCHLIST_PERFORMANCE_TABLE_SETTINGS_STORE = 'u_watchlist_performance_3';
  public static readonly WATCHLIST_PRICE_FEED_TABLE_SETTINGS_STORE = 'u_watchlist_price_feed_3';
  public static readonly WATCHLIST_DIVIDEND_SPLIT_FEED_TABLE_SETTINGS_STORE = 'u_watchlist_dividend_split_feed_4';
  public static readonly IMPORT_TRANSACTION_POS_TABLE_SETTINGS_STORE = 'u_importtransactionpos_2';
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
  public static readonly SPLIT_SETTINGS = 'SPLIT_SETTING';

  // User Rights
  public static readonly ROLE_ADMIN = 'ROLE_ADMIN';
  public static readonly ROLE_ALL_EDIT = 'ROLE_ALLEDIT';
  public static readonly ROLE_USER = 'ROLE_USER';
  public static readonly ROLE_LIMIT_EDIT = 'ROLE_LIMITEDIT';

  public static readonly DIALOG_MENU_SUFFIX = '...';

  public static readonly INSTRUMENT_HEADER = 'I';
  public static readonly FIELD_SUFFIX = '$';

  public static FID_MAX_DIGITS = 14;
  public static FID_STANDARD_INTEGER_DIGITS = 9;
  public static FID_STANDARD_FRACTION_DIGITS = 2;
  public static FID_SMALL_INTEGER_LIMIT = 6;

  public static FID_MAX_INTEGER_DIGITS = 11;
  public static FID_MAX_FRACTION_DIGITS = 8;

  public static FID_MAX_LETTERS = 1000;

  public static resetInterFractionLimit(): void {
    const standardPrecisionMap: { [typename: string]: number } =
      JSON.parse(sessionStorage.getItem(GlobalSessionNames.STANDARD_PRECISION));
    if (standardPrecisionMap) {
      const keys = Object.keys(standardPrecisionMap);
      keys.forEach(key => {
        const value = standardPrecisionMap[key.toString()];
        AppSettings[key] = value;
      });
    }
  }

}
