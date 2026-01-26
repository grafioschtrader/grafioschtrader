package grafiosch;

import java.util.Map;

import grafiosch.dto.MaxDefaultDBValue;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.UDFMetadataGeneral;

/**
 * Global parameter keys and default values for system configuration and tenant regulations.
 * 
 * <p>
 * This class defines configuration parameter keys and their default values used throughout the application for security
 * settings, rate limiting, email configuration, and tenant regulations. It centralizes global parameter management and
 * provides default values for various system limits and security policies to ensure system stability and prevent abuse.
 * The default values can be overwritten in the global parameter entity. The access key is also the parameter name and
 * the key for NLS messages.
 * </p>
 */
public class GlobalParamKeyBaseDefault {

  /** Base prefix for daily limit configuration keys. A dependent application should extend this prefix accordingly. */
  public static final String G_LIMIT_DAY = BaseConstants.G_PREFIX + "limit.day.";

  /** Default JWT token expiration time in minutes (24 hours). */
  public static final int DEFAULT_GLOB_KEY_JWT_EXPIRATION_MINUTES = 1440;

  /** Default maximum allowed limit exceeded count before enforcement action. */
  public static final int DEFAULT_MAX_LIMIT_EXCEEDED_COUNT = 20;

  /** Default number of days to preserve task data before cleanup. */
  public static final int DEFAULT_TASK_DATA_DAYS_PRESERVE = 10;

  /** Default maximum allowed security breach count before enforcement action. */
  public static final int DEFAULT_MAX_SECURITY_BREACH_COUNT = 5;

  /** Default alert email bitmap value (all alerts enabled). */
  public static final int DEFAULT_ALERT_MAIL = Integer.MAX_VALUE;

  /** Access key for global parameters. Daily MailSettingForward creation limit per user or tenant. */
  public static final String GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD = GlobalParamKeyBaseDefault.G_LIMIT_DAY
      + MailSettingForward.class.getSimpleName();

  /** Access key for global parameters. Daily UDFMetadataGeneral creation limit per user or tenant. */
  public static final String GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL = GlobalParamKeyBaseDefault.G_LIMIT_DAY
      + UDFMetadataGeneral.class.getSimpleName();

  /** Access key for global parameters. Daily MailSendRecv operation limit per user or tenant. */
  public static final String GLOB_KEY_LIMIT_DAY_MAIL_SEND = GlobalParamKeyBaseDefault.G_LIMIT_DAY
      + MailSendRecv.class.getSimpleName();

  /** Access key for global parameters. Set expiration time for the JWT token in minutes. */
  public static final String GLOB_KEY_JWT_EXPIRATION_MINUTES = BaseConstants.G_PREFIX + "jwt.expiration.minutes";

  /** Access key for global parameters. Alert bitmap for sending email notifications. */
  public static final String GLOB_KEY_ALERT_MAIL = BaseConstants.G_PREFIX + "alert.mail.bitmap";

  /**
   * Access key for global parameters. Password regular expression properties stored as blob. A property of a blob of
   * global parameters is automatically converted to StandardCharsets.UTF_8 if the suffix matches BLOB_PROPERTIES.
   */
  public static final String GLOB_KEY_PASSWORT_REGEX = BaseConstants.G_PREFIX + "password.regex"
      + BaseConstants.BLOB_PROPERTIES;

  /** Access key for global parameters. Task data preservation period in days before cleanup. */
  public static final String GLOB_KEY_TASK_DATA_DAYS_PRESERVE = BaseConstants.G_PREFIX + "task.data.days.preserve";

  /** The idGTNet for this Server in GTNet */
  public static final String GNET = BaseConstants.G_PREFIX + "gnet.";
  public static final String GLOB_KEY_GTNET_MY_ENTRY_ID = GNET + "my.entry.id";
  
  /** Flag to enable/disable GTNet functionality. 0 = disabled, non-zero = enabled. */
  public static final String GLOB_KEY_GTNET_USE = GNET + "use";
  /** Default value for GTNet enabled flag (disabled by default). */
  public static final int DEFAULT_GTNET_USE = 0;
  
  /** Flag to enable/disable GTNet exchange logging. 0 = disabled, non-zero = enabled. */
  public static final String GLOB_KEY_GTNET_USE_LOG = GNET + "use.log";
  /** Default value for GTNet logging enabled flag (disabled by default). */
  public static final int DEFAULT_GTNET_USE_LOG = 0;
  
  
  public GlobalParamKeyBaseDefault() {
    Map<String, MaxDefaultDBValue> defaultLimitMap = Globalparameters.defaultLimitMap;

    /** Set tenant regulations violations, with daily CUD limits on user or tenant own entries */
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_MAIL_SEND, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD, new MaxDefaultDBValue(12));
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL, new MaxDefaultDBValue(20));

  }
}
