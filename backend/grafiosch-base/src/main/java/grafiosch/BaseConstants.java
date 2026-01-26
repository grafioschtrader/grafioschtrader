package grafiosch;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafiosch.types.UDFDataType;

/**
 * Abstract base class containing application-wide constants and configuration values.
 * 
 * <p>
 * This class centralizes all constant definitions used throughout the application including date formats, security
 * constraints, field size limits, user-defined field configurations, rate limiting parameters, and system defaults. It
 * provides a single source of truth for configuration values that need to be consistent across different modules.
 * </p>
 */
public abstract class BaseConstants {

  /** Global property prefix for system-wide configuration parameters. */
  public static final String G_PREFIX = "g.";

  /** Prefix for users day entity limits */
  public static final String G_LIMIT_DAY = G_PREFIX + "limit.day.";
  
  /**
   * Set of valid parameter prefixes for configuration. A dependent application should extend this prefix accordingly.
   * For example, used for the daily user limits of CUD transactions.
   */
  public static final Set<String> PREFIXES_PARAM = new HashSet<>(Set.of(G_PREFIX));
  
  /** Standard time zone used throughout the application. */
  public static final String TIME_ZONE = "UTC";

  /** This format is indeed a standard, specifically an international one (ISO 8601). */
  public static final String STANDARD_DATE_FORMAT = "yyyy-MM-dd";

  /** ISO 8601 date-time format for JSON serialization with LocalDateTime. */
  public static final String STANDARD_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

  /** Standard local date-time format including hours and minutes. */
  public static final String STANDARD_LOCAL_DATE_TIME = "yyyy-MM-dd HH:mm";
  
  /** Standard local date-time format including seconds precision. */
  public static final String STANDARD_LOCAL_DATE_TIME_SECOND = "yyyy-MM-dd HH:mm:ss";
  
  /**
   * A property of a blob of global parameters is automatically converted to StandardCharsets.UTF_8 if the suffix of the
   * property name matches this string.
   */
  public static final String BLOB_PROPERTIES = ".properties";

  /** Message key for NLS in the event of security breaches when a user attempts to access data of another client */
  public static final String CLIENT_SECURITY_BREACH = "client.security.breach";

  /**
   * Message key for NLS in the event when user tries to write tenant data outside the defined limits. For example a
   * watchlist may have a maximum of instruments, if it is exceeded then this exception thrown.
   */
  public static final String LIMIT_SECURITY_BREACH = "limit.security.breach";

  /** Access key for global parameters and NLS message key. The limits for the tenants rule violations */
  public static final String GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT = G_PREFIX + "max.limit.request.exceeded.count";

  /**
   * Access key for global parameters and NLS message key. Maximum number of security violations (external data access,
   * property modification)
   */
  public static final String GLOB_KEY_MAX_SECURITY_BREACH_COUNT = G_PREFIX + "max.security.breach.count";

 
  /** The standard note text size */
  public static final int FID_MAX_LETTERS = 1000;

  /** The Email verification expiration time in minutes */
  public static final int EMAIL_VERIFICATION_EXPIRATION_MINUTES = 180;

  /**
   * Property names of user-defined properties are indicated by this prefix. This is then extended with the ID of the
   * metadata for this property.This is also intended to avoid overlapping with field names of entities.
   */
  public static final String UDF_FIELD_PREFIX = "f";

  /** The maximum length of a web URL in characters. */
  public static final int FIELD_SIZE_MAX_G_WEB_URL = 254;

  /**
   * Defines the maximum values for user-defined input fields. The validation of these values should also be carried out
   * in the frontend.
   */
  public static final Map<UDFDataType, UDFPrefixSuffix> uDFPrefixSuffixMap = new HashMap<>();

  static {
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_NumericInteger,
        new UDFPrefixSuffix(Integer.MIN_VALUE, Integer.MAX_VALUE, null));
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_String, new UDFPrefixSuffix(0, 2048, null));
    BaseConstants.uDFPrefixSuffixMap.put(UDFDataType.UDF_Numeric, new UDFPrefixSuffix(22, 8, 1));
  }

  /**
   * Configuration class for user-defined field validation constraints. Defines validation rules including
   * minimum/maximum values, string lengths, and numeric precision constraints for different UDF data types.
   */
  public static class UDFPrefixSuffix {
    /** Minimum value or minimum length constraint. */
    public final int prefix;
    
    /** Maximum value or maximum length constraint. */
    public final int suffix;
    
    /** Combined constraint value for complex validation rules. */
    public final Integer together;

    /**
     * Creates UDF validation constraints.
     * 
     * @param prefix minimum value or length
     * @param suffix maximum value or length
     * @param together combined constraint value, null if not applicable
     */
    public UDFPrefixSuffix(int prefix, int suffix, Integer together) {
      this.prefix = prefix;
      this.suffix = suffix;
      this.together = together;
    }

  }

  /** Unix-style line ending character. */
  public static final String NEW_LINE = "\n";
  
  /** Windows-style line ending for cross-platform text compatibility. */
  public static final String RETURN_AND_NEW_LINE = "\r\n";
 

  /** Supported languages in this application */
  public static final List<String> G_LANGUAGE_CODES = Arrays.asList("de", "en");

  /** Default regular expression for password strength validation. Requires at least 8 characters with letter and digit. */
  public static final String STANDARD_PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";

  /** Normal precision for decimal numbers with currency */
  public static final int FID_STANDARD_FRACTION_DIGITS = 2;
  
  /** Maximum total digits allowed in numeric fields. */
  public static final int FID_MAX_DIGITS = 16;

  /** Maximum fractional digits for numeric precision. Numbers may be rounded to this precision. */
  public static final int FID_MAX_FRACTION_DIGITS = 8;

  /** NLS message key for error message of unauthorized data manipulation. */
  public static final String RIGHTS_SECURITY_BREACH = "rights.security.breach";

  /** If a record is created by the system id gets this id */
  public static final int SYSTEM_ID_USER = 0;

  
  /** User ID for global user-defined fields. Global user-defined fields belong to user 0. */
  public static final int UDF_ID_USER = 0;

  /** Maximum UI order value for user-defined fields. For user-defined fields, the sorting number must be less than 100. */
  public static final byte MAX_USER_UI_ORDER_VALUE = 100;

  /** Duration in milliseconds that an IP address is blocked after failed login attempts. */
  public static final int SUSPEND_IP_ADDRESS_TIME = 60 * 60 * 24 * 1000;

  /**
   * Maximum number of failed login attempts before user account is blocked. With this number of failed attempts for a
   * user login, the user will be blocked. The IP address is taken into consideration.
   */
  public static final int MAX_LOGIN_ATTEMPT = 5;

  /**
   * Configuration class for rate limiting and bandwidth management.
   * 
   * <p>Centralizes all bandwidth-related constants and provides factory methods for creating
   * properly configured rate limiting buckets. This class encapsulates the complexity of
   * token bucket configuration and ensures consistent rate limiting across the application.</p>
   */
  public final class BandwidthConfig {
    
    /** Maximum tokens that can be held per bucket in one minute. */
    public static final int MINUTE_BUCKET_SIZE = 30;
    
    /** Number of tokens regenerated in one minute per bucket. */
    public static final int MINUTE_REFILL = 30;
    
    /** Maximum tokens that can be held per bucket in one hour. */
    public static final int HOUR_BUCKET_SIZE = 300;
    
    /** Number of tokens regenerated in one hour per bucket. */
    public static final int HOUR_REFILL = 300;
    
    /** Duration for minute-based rate limiting. */
    public static final Duration MINUTE_DURATION = Duration.ofMinutes(1);
    
    /** Duration for hour-based rate limiting. */
    public static final Duration HOUR_DURATION = Duration.ofHours(1);
    
    // Prevent instantiation
    private BandwidthConfig() {
      throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
  }

  /** User tries to update a field which can only updated when it is created  */
  public static final String FILED_EDIT_SECURITY_BREACH = "field.edit.security.breach";

  /**
   * User tries to access shared data outside its boundary. For example access history quotes of a security or currency
   * pair which has no context to the tenant data.
   */
  public static final String STEAL_DATA_SECURITY_BREACH = "steal.data.security.breach";

 

 
}
