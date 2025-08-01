package grafioschtrader;

import java.util.List;

import grafiosch.BaseConstants;

public class GlobalConstants extends BaseConstants {

  /** GT application prefix for configuration parameters. */
  public static final String GT_PREFIX = "gt.";

  /** Prefix for users day entity limits */
  public static final String GT_LIMIT_DAY = GT_PREFIX + "limit.day.";

  public static final String STOCK_EX_MIC_UK = "XLON";
  public static final String STOCK_EX_MIC_NASDAQ = "XNAS";
  public static final String STOCK_EX_MIC_NYSE = "XNYS";
  public static final String STOCK_EX_MIC_SIX = "XSWX";
  public static final String STOCK_EX_MIC_XETRA = "XETR";
  public static final String STOCK_EX_MIC_FRANKFURT = "XFRA";
  public static final String STOCK_EX_MIC_SPAIN = "XMAD";
  public static final String STOCK_EX_MIC_ITALY = "XMIL";
  public static final String STOCK_EX_MIC_JAPAN = "XTKS";
  public static final String STOCK_EX_MIC_AUSTRIA = "XVIE";
  public static final String STOCK_EX_MIC_FRANCE = "XPAR";
  public static final String STOCK_EX_MIC_AUSTRALIA = "XASX";

  public static final String STOCK_EX_MIC_ZKB = "ZKBX";
  public static final String STOCK_EX_MIC_STUTTGART = "XSTU";
  public static final String STOCK_EX_MIC_WARSAW = "XWAR";

  public static final String MC_USD = "USD";
  public static final String MC_EUR = "EUR";
  public static final String MC_GBP = "GBP";
  public static final String MC_JPY = "JPY";
  public static final String MC_CHF = "CHF";
  public static final String MC_AUD = "AUD";
  public static final String CC_BTC = "BTC";

  /** Normal precision for decimal numbers */
  public static final int FID_STANDARD_FRACTION_DIGITS = 2;

  /** Maximum precision digits for currency exchange rates. */
  public static final int FID_MAX_CURRENCY_EX_RATE_PRECISION = 20;
  
  /** Maximum fraction digits for currency exchange rates. */
  public static final int FID_MAX_CURRENCY_EX_RATE_FRACTION = 10;

  /** Maximum character length for stock exchange website URLs. */
  public static final int FIELD_SIZE_MAX_Stockexchange_Website = 128;

  /** Daily correlation parameters: step, min value and max value. */
  public static final String CORR_DAILY = "10,20,120";
  
  /** Monthly correlation parameters: step, min value and max value. */
  public static final String CORR_MONTHLY = "12,12,60";
  
  /** Annual correlation parameters (not defined). */
  public static final String CORR_ANNUAL = null;
  
  /** Minimum required periods for correlation calculations. */
  public static final byte REQUIRED_MIN_PERIODS = 3;

  /**
   * It will adjust the currency exchange rate or quotation for the cash account amount. Sometimes the user tries to get
   * the total amount as exact as possible by adjusting the rate or exchange rate. In this case, the user interface
   * rounds the calculated amount, thus only the rounded calculation results in the desired calculated amount. The
   * backend can adjust the two parameters even more exactly with a rounding of FID_MAX_FRACTION_DIGITS.
   */
  public static boolean AUTO_CORRECT_TO_AMOUNT = true;

  /** Short date format for compact date representation. */
  public static final String SHORT_STANDARD_DATE_FORMAT = "yyyyMMdd";

  /** Standard local time format for hours and minutes. */
  public static final String STARNDARD_LOCAL_TIME = "HH:mm";

  /** GT supports back to this year, the entries of transactions and prices of securities. */
  public static final int OLDEST_TRADING_YEAR = 2000;

  /** GT supports back to this date, the entries of transactions and prices of securities. */
  public static final String OLDEST_TRADING_DAY = OLDEST_TRADING_YEAR + "-01-01";

  /**
   * The youngest trading day in the future. The trading calendar can be entered up to this date in the future.
   */
  public static final String YOUNGEST_TRADING_CALENDAR_DAY = "2028-12-31";

  /**
   * Sometimes the EOD of the currency pair is not yet updated, in this case the existing current price can be taken if
   * there are not more than so many days between the requested date and the current one.
   */
  public static final int MAX_DAY_DIFF_CURRENCY_UNTIL_NOW = 3;

  /**
   * The amount of time in minutes after the close of the relevant stock exchange that is waited before an update of
   * historical prices is made.
   */
  public static final int WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES = 180;

  /**
   * Time that had to elapse before the next obtainment of historical prices from the external data source.
   */
  public static final int TiME_MUST_HAVE_PASSED_SINCE_LAST_UPDATE_IN_MINUTES = 20 * 60;

  /**
   * Dividend frequency + days for the next dividend check
   */
  public static final int DIVIDEND_FREQUENCY_PLUS_DAY = 10;

  /**
   * Earliest day for the next dividend check, but the combination of frequency and this value control the date of next
   * possible check.
   */
  public static final int DIVIDEND_FROM_NOW_FOR_NEXT_CHECK_IN_DAYS = 8;

  /** Number of days to look back when checking for dividend payments. */
  public static final int DIVIDEND_CHECK_DAYS_LOOK_BACK = 360;

  /**
   * There may be certain differences between the transaction date and the official dividend payment date. This
   * indicates the number of days for this tolerance.
   */
  public static final int DIVIDEND_CHECK_PAY_DATE_TOLERANCE_IN_DAYS = 4;

  /**
   * The number of cores for the fork join pool is multiplied by this value. This pool is used for the connectors of the
   * EOD and last price.
   */
  public static final int FORK_JOIN_POOL_CORE_MULTIPLIER = 4;

  /** Maximum of weeks in the period performance report */
  public static final int PERFORMANCE_MAX_WEEK_LIMIT = 53;

  /** Minimum number of months to include in period performance report as calculations. */
  public static final int PERFORMANCE_MIN_INCLUDE_MONTH_LIMIT = 2;

  /** Short user agent string for HTTP client requests. */
  public static final String USER_AGENT_HTTPCLIENT_SHORT = "Mozilla/5.0";

  /** Full user agent string for HTTP client requests with detailed browser information. */
  public static final String USER_AGENT_HTTPCLIENT = USER_AGENT_HTTPCLIENT_SHORT
      + " (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

  /** Units is a field name, which is referenced more often in validation exceptions */
  public static final String UNITS = "units";

  /**
   * Contains the supported crypto currencies. When a new is added, check references. Maybe a connector must be extended
   * to this new cryptocurrency.
   */
  public static final List<String> CRYPTO_CURRENCY_SUPPORTED = List.of(CC_BTC, "BNB", "ETH", "ETC", "LTC", "XRP");

  /** Days before split date to end the historical quote comparison period. */
  public static final int SPLIT_DAYS_LOOK_BACK_END_DATE_BEFORE_SPLIT = 30;

  /** Days before split date to start the historical quote comparison period. */
  public static final int SPLIT_DAYS_LOCK_BACK_START_DATE = SPLIT_DAYS_LOOK_BACK_END_DATE_BEFORE_SPLIT + 7;

  /** Maximum days for using latest exchange rate price. */
  public static final int EX_CHANGE_RATE_DAYS_LIMIT_LATEST_PRICE = 4;

  /** Accepted percentage difference threshold for exchange rate validation. */
  public static final double ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF = 8.0;
  /**
   * It is possible that the split from the calendar was internally assigned to a wrong security, therefore after
   * reaching this number of days the repetitive query to the connector is terminated.
   */
  public static final double MAX_DAYS_FOR_SECURITY_IS_REFLECTING_SPLIT = 5;

  /**
   * Defines the maximum values for user-defined input fields. The validation of these values should also be carried out
   * in the frontend.
   */
  public static final String PREFIX_FOR_DOWNLOAD_REDIRECT_TO_BACKEND = "--";

}
