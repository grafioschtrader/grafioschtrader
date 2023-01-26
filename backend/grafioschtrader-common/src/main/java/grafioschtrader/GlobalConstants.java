package grafioschtrader;

import java.util.Arrays;
import java.util.List;

public class GlobalConstants {

  public static final String STOCK_EX_MIC_UK = "XLON";
  public static final String STOCK_EX_MIC_NASDAQ = "XNAS";
  public static final String STOCK_EX_MIC_SIX = "XSWX";
  public static final String STOCK_EX_MIC_XETRA = "XSWX";
  public static final String STOCK_EX_MIC_SPAIN = "XMAD";
  public static final String STOCK_EX_MIC_ITALY = "XMIL";
  public static final String STOCK_EX_MIC_JAPAN = "XTKS";
  public static final String STOCK_EX_MIC_AUSTRIA = "XVIE";
  public static final String STOCK_EX_MIC_ZKB = "ZKBX";
  public static final String STOCK_EX_MIC_STUTTGART = "XSTU";
  public static final String STOCK_EX_MIC_WARSAW = "XWAR";

  /**
   * Supported languages in this application
   */
  public static final List<String> GT_LANGUAGE_CODES = Arrays.asList("de", "en");

  /**
   * If a record is created by the system id gets this id
   */
  public static final int SYSTEM_ID_USER = 0;

  /**
   * Admin user id will be set to this value, he will be the owner of the shared
   * records.
   */
  public static final int ADMIN_ID_USER = 1;

  /**
   * Normal precision for decimal numbers
   */
  public static final int FID_STANDARD_FRACTION_DIGITS = 2;
  // public static final int FID_STANDARD_INTEGER_DIGITS = 9;
  // public static final int FID_SMALL_INTEGER_LIMIT = 6;

  /**
   * Number maybe rounded to this precision
   */
  public static final int FID_MAX_FRACTION_DIGITS = 8;
  // public static final int FID_MAX_INTEGER_DIGITS = 11;
  // public static final int FID_MAX_DIGITS = 16;

  /** Step, min value and max value **/
  public static final String CORR_DAILY = "10,20,120";
  public static final String CORR_MONTHLY = "12,12,60";
  public static final String CORR_ANNUAL = null;
  public static final byte REQUIRED_MIN_PERIODS = 3;

  /**
   * It will adjust the currency exchange rate or quotation for the cash account
   * amount.
   */
  public static boolean AUTO_CORRECT_TO_AMOUNT = false;

  public static final String USER_AGENT = "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36\"";

  /**
   * The standard note text size
   */
  public static final int FID_MAX_LETTERS = 1000;

  public static final String TIME_ZONE = "UTC";
  public static final String SHORT_STANDARD_DATE_FORMAT = "yyyyMMdd";
  public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";
  public static final String STANDARD_LOCAL_DATE_TIME = "yyyy-MM-dd HH:mm";
  public static final String STANDARD_LOCAL_DATE_TIME_SECOND = "yyyy-MM-dd HH:mm:ss";
  public static final String STARNDARD_LOCAL_TIME = "HH:mm";
  public static final String OLDEST_TRADING_DAY = "2000-01-01";
  public static final int OLDEST_TRADING_YEAR = 2000;
  public static final String YOUNGEST_TRADING_CALENDAR_DAY = "2025-12-31";

  /**
   * Sometimes the EOD of the currency pair is not yet updated, in this case the
   * existing current price can be taken if there are not more than so many days
   * between the requested date and the current one.
   */
  public static final int MAX_DAY_DIFF_CURRENCY_UNTIL_NOW = 3;

  /**
   * The amount of time in minutes after the close of the relevant stock exchange
   * that is waited before an update of historical prices is made.
   */
  public static final int WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES = 180;

  /**
   * Time that had to elapse before the next obtainment of historical prices from
   * the external data source.
   */
  public static final int TiME_MUST_HAVE_PASSED_SINCE_LAST_UPDATE_IN_MINUTES = 20 * 60;

  public static final int MAX_LOGIN_ATTEMPT = 5;
  /**
   * Time in milliseconds
   */
  public static final int SUSPEND_IP_ADDRESS_TIME = 60 * 60 * 24 * 1000;

  /**
   * Dividend frequency + days for the next dividend check
   */
  public static final int DIVIDEND_FREQUENCY_PLUS_DAY = 10;

  /**
   * Earliest Day for the next dividend check, but the combination of frequency
   * and this value control the date of next possible check.
   */
  public static final int DIVIDEND_FROM_NOW_FOR_NEXT_CHECK_IN_DAYS = 8;

  /**
   * The number of cores for the fork join pool is multiplied by this value. This
   * pool is used for the connectors of the EOD and last price.
   */
  public static final int FORK_JOIN_POOL_CORE_MULTIPLIER = 4;

  /**
   * Maximum of weeks in the period performance report
   */
  public static final int PERFORMANCE_MAX_WEEK_LIMIT = 53;
  public static final int PERFORMANCE_MIN_INCLUDE_MONTH_LIMIT = 2;
  public static final String USER_AGENT_HTTPCLIENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36";

  public static final String UNITS = "units";

  /**
   * Contains the supported cryptocurrencies. When a new is added, check
   * references. Maybe a connector must be extended to this new cryptocurrency.
   */
  public static final List<String> CRYPTO_CURRENCY_SUPPORTED = List.of("BTC", "BNB", "ETH", "ETC", "LTC", "XRP");

  /**
   * The Email verification expiration time in minutes
   */
  public static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 180;

  public static final double DETECT_SPLIT_ADJUSTED_FACTOR_STEP = 18.0;
  public static final int SPLIT_DAYS_FOR_AVERAGE_CALC = 5;
  public static final int EX_CHANGE_RATE_DAYS_LIMIT_LATEST_PRICE = 4;
  public static final double ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF = 8.0;
  public static final double MAX_DAYS_FOR_SECURITY_IS_REFLECTING_SPLIT = 5;

  public static final int BANDWITH_MINUTE_BUCKET_SIZE = 30;
  public static final int BANDWITH_MINUTE_REFILL = 30;
  public static final int BANDWITH_HOOUR_BUCKET_SIZE = 300;
  public static final int BANDWITH_HOUR_REFILL = 300;
  /**
   * User try to access data of a other tenant
   */
  public static final String CLIENT_SECURITY_BREACH = "client.security.breach";
  /**
   * User hat not the privileges to access certain shared date
   */
  public static final String RIGHTS_SECURITY_BREACH = "rights.security.breach";
  /**
   * User tries to access shared data outside its boundary. For example access
   * history quotes of a security or currency pair which has no context to the
   * tenant data.
   */
  public static final String STEAL_DATA_SECURITY_BREACH = "steal.data.security.breach";
  /**
   * User tries to write tenant data outside the defined limits. For example a
   * watchlist may have a maximum of instruments, if it is exceeded then this
   * exception thrown.
   */
  public static final String LIMIT_SECURITY_BREACH = "limit.security.breach";
  /**
   * User tries to update a field which can only updated when it is created
   */
  public static final String FILED_EDIT_SECURITY_BREACH = "field.edit.security.breach";

}
