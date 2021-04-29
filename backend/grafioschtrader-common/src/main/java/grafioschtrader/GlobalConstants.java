package grafioschtrader;

import java.util.Arrays;
import java.util.List;

public class GlobalConstants {

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
  public static final int STANDARD_PRECISION = 2;

  /**
   * Number maybe rounded to this precision
   */
  public static final int MAX_PRECISION = 8;

  public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";
  public static final String STANDARD_LOCAL_DATE_TIME = "yyyy-MM-dd HH:mm";
  public static final String OLDEST_TRADING_DAY = "2000-01-01";
  public static final int OLDEST_TRADING_YEAR = 2000;
  public static final String YOUNGEST_TRADING_CALENDAR_DAY = "2025-12-31";

  public static final int MAX_LOGIN_ATTEMPT = 5;
  /**
   * Time in milliseconds
   */
  public static final int SUSPEND_IP_ADDRESS_TIME = 60 * 60 * 24 * 1000;

  /**
   * The standard note text size
   */
  public static final int NOTE_SIZE = 1000;
  /**
   * Maximum of weeks in the period performance report
   */
  public static final int PERFORMANCE_MAX_WEEK_LIMIT = 53;
  public static final int PERFORMANCE_MIN_INCLUDE_MONTH_LIMIT = 2;
  public static final String USER_AGENT_HTTPCLIENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36";

  public static final String UNITS = "units";

  /**
   * Contains the supported cryptocurrencies
   */
  public static final List<String> CRYPTO_CURRENCY_SUPPORTED = List.of("BTC", "ETH", "ETC", "LTC", "XRP");

  /**
   * The Email verification expiration time in minutes
   */
  public static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 180;

  public static final double DETECT_SPLIT_ADJUSTED_FACTOR_STEP = 18.0;
  public static final int SPLIT_DAYS_FOR_AVERAGE_CALC = 5;
  public static final int EX_CHANGE_RATE_DAYS_LIMIT_LATEST_PRICE = 4;
  public static final double ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF = 8.0;

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
