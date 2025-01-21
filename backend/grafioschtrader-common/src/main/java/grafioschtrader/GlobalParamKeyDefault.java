package grafioschtrader;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import grafiosch.BaseConstants;
import grafiosch.dto.MaxDefaultDBValue;
import grafiosch.entities.Globalparameters;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.UDFMetadataGeneral;
import grafioschtrader.entities.UDFMetadataSecurity;

public class GlobalParamKeyDefault {

  public static final String GT_PREFIX = "gt.";
  
  
  public static final String DEFAULT_CURRENCY_PRECISION = "BTC=8,ETH=7,JPY=0,ZAR=0";
  public static final int DEFAULT_TASK_DATA_DAYS_PRESERVE = 10;
  public static final short DEFAULT_INTRA_RETRY = 4;
  public static final short DEFAULT_HISTORY_RETRY = 4;
  public static final short DEFAULT_DIVIDEND_RETRY = 2;
  public static final short DEFAULT_SPLIT_RETRY = 2;
  public static final int DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS = 300;
  public static final int DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS = 1200;
  public static final int DEFAULT_GLOB_KEY_JWT_EXPIRATION_MINUTES = 1440;
  public static final int DEFUALT_MAX_WATCHLIST = 30;
  public static final int DEFAULT_MAX_LIMIT_EXCEEDED_COUNT = 20;
  public static final int DEFAULT_MAX_SECURITY_BREACH_COUNT = 5;
  public static final Date DEFAULT_START_FEED_DATE = new GregorianCalendar(2000, Calendar.JANUARY, 1).getTime();
  public static final int DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK = 60;
  public static final int DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS = 0;
  public static final int DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE = 80;
  public static final int DEFAULT_HISTORY_OBSERVATION_DAYS_BACK = 60;
  public static final int DEFAULT_HISTORY_OBSERVATION_RETRY_MINUS = 0;
  public static final int DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE = 80;
  public static final int DEFAULT_ALERT_MAIL = Integer.MAX_VALUE;
  public static final int DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY = 5;
  public static final int DEFAULT_UPDATE_PRICE_BY_EXCHANGE = 0;


  public static final String GLOB_KEY_CURRENCY_PRECISION = GT_PREFIX + "currency.precision";
  public static final String GLOB_KEY_TASK_DATA_DAYS_PRESERVE = GT_PREFIX + "task.data.days.preserve";


  // Connector settings
  public static final String GLOB_KEY_CRYPTOCURRENCY_HISTORY_CONNECTOR = GT_PREFIX + "cryptocurrency.history.connector";
  public static final String GLOB_KEY_CRYPTOCURRENCY_INTRA_CONNECTOR = GT_PREFIX + "cryptocurrency.intra.connector";
  public static final String GLOB_KEY_CURRENCY_HISTORY_CONNECTOR = GT_PREFIX + "currency.history.connector";
  public static final String GLOB_KEY_CURRENCY_INTRA_CONNECTOR = GT_PREFIX + "currency.intra.connector";
  public static final String GLOB_KEY_INTRA_RETRY = GT_PREFIX + "intra.retry";
  public static final String GLOB_KEY_HISTORY_RETRY = GT_PREFIX + "history.retry";
  public static final String GLOB_KEY_DIVIDEND_RETRY = GT_PREFIX + "dividend.retry";
  public static final String GLOB_KEY_SPLIT_RETRY = GT_PREFIX + "split.retry";
  public static final String GLOB_KEY_START_FEED_DATE = GT_PREFIX + "core.data.feed.start.date";
  public static final String GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS = GT_PREFIX + "sc.intra.update.timeout.seconds";
  public static final String GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS = GT_PREFIX + "w.intra.update.timeout.seconds";
  public static final String GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY = GT_PREFIX + "history.max.filldays.currency";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK = GT_PREFIX + "intraday.observation.or.days.back";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS = GT_PREFIX + "intraday.observation.retry.minus";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE = GT_PREFIX + "intraday.observation.falling.percentage";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK = GT_PREFIX + "history.observation.days.back";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_RETRY_MINUS = GT_PREFIX + "history.observation.retry.minus";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE = GT_PREFIX + "history.observation.falling.percentage";

  // History quote quality. Date which last time when a history quality update was
  // happened
  public static final String GLOB_KEY_HISTORYQUOTE_QUALITY_UPDATE_DATE = GT_PREFIX + "historyquote.quality.update.date";
  public static final String GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE = GT_PREFIX + "securitysplit.append.date";
  public static final String GLOB_KEY_YOUNGEST_DIVIDEND_APPEND_DATE = GT_PREFIX + "securitydividend.append.date";
  public static final String GLOB_KEY_UDF_GENERAL_RECREATE = GT_PREFIX + "udf.general.recreate";
  // The idGTNet for this Server in GTNet
  public static final String GLOB_KEY_GTNET_MY_ENTRY_ID = GT_PREFIX + "gtnet.my.entry.id";
  // Set expiration time for the JWT token.
  public static final String GLOB_KEY_JWT_EXPIRATION_MINUTES = GT_PREFIX + "jwt.expiration.minutes";
  // Alert bitmap for sending email
  public static final String GLOB_KEY_ALERT_MAIL = GT_PREFIX + "alert.mail.bitmap";
  // Password regular expression properties
  public static final String GLOB_KEY_PASSWORT_REGEX = GT_PREFIX + "password.regex" + BaseConstants.BLOB_PROPERTIES;

  // Tenant data entity limits
  private static final String MAX = "max.";
  public static final String GLOB_KEY_MAX_CASH_ACCOUNT = GT_PREFIX + MAX + "cash.account";
  public static final String GLOB_KEY_MAX_PORTFOLIO = GT_PREFIX + MAX + "portfolio";
  public static final String GLOB_KEY_MAX_SECURITY_ACCOUNT = GT_PREFIX + MAX + "security.account";
  public static final String GLOB_KEY_MAX_SECURITIES_CURRENCIES = GT_PREFIX + MAX + "securities.currencies";
  public static final String GLOB_KEY_MAX_WATCHTLIST = GT_PREFIX + MAX + "watchlist";
  public static final String GLOB_KEY_MAX_WATCHLIST_LENGTH = GT_PREFIX + MAX + "watchlist.length";
  public static final String GLOB_KEY_MAX_CORRELATION_SET = GT_PREFIX + MAX + "correlation.set";
  public static final String GLOB_KEY_MAX_CORRELATION_INSTRUMENTS = GT_PREFIX + MAX + "correlation.instruments";
  public static final String GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE = GT_PREFIX + "update.price.by.exchange";


  // User day entity limits
  public static final String GT_LIMIT_DAY = GT_PREFIX + "limit.day.";
  public static final String GLOB_KEY_LIMIT_DAY_ASSETCLASS = GT_LIMIT_DAY + Assetclass.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE = GT_LIMIT_DAY + Stockexchange.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK = GT_LIMIT_DAY + ProposeUserTask.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_SECURITY = GT_LIMIT_DAY + Security.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_CURRENCYPAIR = GT_LIMIT_DAY + Currencypair.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_MAIL_SEND = GT_LIMIT_DAY + MailSendRecv.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE = GT_LIMIT_DAY
  + ImportTransactionTemplate.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM = GT_LIMIT_DAY
  + ImportTransactionPlatform.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN = GT_LIMIT_DAY
  + TradingPlatformPlan.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD = GT_LIMIT_DAY
  + MailSettingForward.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_UDFMETADATASEUCIRTY = GT_LIMIT_DAY
  + UDFMetadataSecurity.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL = GT_LIMIT_DAY
  + UDFMetadataGeneral.class.getSimpleName();

  // The limits for the tenants rule violations
  public static final String GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT = GT_PREFIX + "max.limit.request.exceeded.count";
  public static final String GLOB_KEY_MAX_SECURITY_BREACH_COUNT = GT_PREFIX + "max.security.breach.count";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_DE = GT_PREFIX + "source.demo.idtenant.de";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_EN = GT_PREFIX + "source.demo.idtenant.en";

  
  public GlobalParamKeyDefault() {
    Map<String, MaxDefaultDBValue> defaultLimitMap = Globalparameters.defaultLimitMap;
    // Set tenant data entity limits in total on not shared entries.
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CASH_ACCOUNT, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_PORTFOLIO, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITY_ACCOUNT, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITIES_CURRENCIES, new MaxDefaultDBValue(2000));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHTLIST, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHLIST_LENGTH, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_SET, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS, new MaxDefaultDBValue(20));
  

    // Set tenant regulations violations, with daily CUD limits on shared entries
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_ASSETCLASS, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_SECURITY, new MaxDefaultDBValue(50));

    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_CURRENCYPAIR, new MaxDefaultDBValue(15));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM, new MaxDefaultDBValue(3));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN, new MaxDefaultDBValue(3));

    // Set tenant regulations violations, with daily CUD limits on user or tenant
    // own entries
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_MAIL_SEND, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD, new MaxDefaultDBValue(12));

    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_UDFMETADATASEUCIRTY, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL, new MaxDefaultDBValue(20));
    
    
    // TODO Other entities -> otherwise null pointer exception
  }
  
}
