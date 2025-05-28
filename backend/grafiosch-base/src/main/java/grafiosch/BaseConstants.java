package grafiosch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafiosch.types.UDFDataType;

public abstract class BaseConstants {

  // TODO This should be in GT Artifacts
  public static final String GT_PREFIX = "gt.";
  
  public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";
  public static final String BLOB_PROPERTIES = ".properties";
  /**
   * User try to access data of a other tenant
   */
  public static final String CLIENT_SECURITY_BREACH = "client.security.breach";
  /**
   * User tries to write tenant data outside the defined limits. For example a
   * watchlist may have a maximum of instruments, if it is exceeded then this
   * exception thrown.
   */
  public static final String LIMIT_SECURITY_BREACH = "limit.security.breach";
  // The limits for the tenants rule violations
  public static final String GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT = GT_PREFIX + "max.limit.request.exceeded.count";
  public static final String GLOB_KEY_MAX_SECURITY_BREACH_COUNT = GT_PREFIX + "max.security.breach.count";

  public static final String STANDARD_LOCAL_DATE_TIME = "yyyy-MM-dd HH:mm";

  /**
   * The standard note text size
   */
  public static final int FID_MAX_LETTERS = 1000;

  /**
   * The Email verification expiration time in minutes
   */
  public static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 180;

  /**
   * Property names of user-defined properties are indicated by this prefix. This
   * is then extended with the ID of the metadata for this property.
   */
  public static final String UDF_FIELD_PREFIX = "f";

  /**
   * The maximum length of a web URL in characters.
   */
  public static final int FIELD_SIZE_MAX_G_WEB_URL = 254;

  /**
   * Defines the maximum values for user-defined input fields. The validation of
   * these values should also be carried out in the frontend.
   */
  public static final Map<UDFDataType, UDFPrefixSuffix> uDFPrefixSuffixMap = new HashMap<>();
  
  static {
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_NumericInteger,
        new UDFPrefixSuffix(Integer.MIN_VALUE, Integer.MAX_VALUE, null));
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_String, new UDFPrefixSuffix(0, 2048, null));
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_Numeric, new UDFPrefixSuffix(22, 8, 1));
  }

  public static class UDFPrefixSuffix {
    public final int prefix;
    public final int suffix;
    public final Integer together;

    public UDFPrefixSuffix(int prefix, int suffix, Integer together) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.together = together;
    }

  }

  public static final String RETURN_AND_NEW_LINE = "\r\n";

  public static final String STANDARD_LOCAL_DATE_TIME_SECOND = "yyyy-MM-dd HH:mm:ss";

  public static final String NEW_LINE = "\n";

  /**
   * Supported languages in this application
   */
  public static final List<String> GT_LANGUAGE_CODES = Arrays.asList("de", "en");

  public static final String STANDARD_PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";

  // public static final int FID_MAX_INTEGER_DIGITS = 11;
  public static final int FID_MAX_DIGITS = 16;

  /**
   * Number maybe rounded to this precision
   */
  public static final int FID_MAX_FRACTION_DIGITS = 8;

  /**
   * User hat not the privileges to access certain shared date
   */
  public static final String RIGHTS_SECURITY_BREACH = "rights.security.breach";

  /**
   * Global user-defined fields belong to user 0.
   */
  public static final int UDF_ID_USER = 0;

  /**
   * For user-defined fields, the sorting number must be less than 100.
   */
  public static final byte MAX_USER_UI_ORDER_VALUE = 100;

  public static final String TIME_ZONE = "UTC";

  /**
   * Number of milliseconds an IP address is blocked from further user login
   * attempts.
   */
  public static final int SUSPEND_IP_ADDRESS_TIME = 60 * 60 * 24 * 1000;

  /**
   * With this number of failed attempts for a user login, the user will be
   * blocked. The IP address is taken.
   */
  public static final int MAX_LOGIN_ATTEMPT = 5;

  /**
   * Maximum tokens that can be held per bucket in one minute.
   */
  public static final int BANDWITH_MINUTE_BUCKET_SIZE = 30;

  /**
   * Number of tokens regenerated in one minute per bucket.
   */
  public static final int BANDWITH_MINUTE_REFILL = 30;

  public static final int BANDWITH_HOOUR_BUCKET_SIZE = 300;

  public static final int BANDWITH_HOUR_REFILL = 300;

  /**
   * If a record is created by the system id gets this id
   */
  public static final int SYSTEM_ID_USER = 0;

}
