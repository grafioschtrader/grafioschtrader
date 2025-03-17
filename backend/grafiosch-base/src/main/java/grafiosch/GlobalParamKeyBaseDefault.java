package grafiosch;

import java.util.Map;

import grafiosch.dto.MaxDefaultDBValue;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.UDFMetadataGeneral;

public class GlobalParamKeyBaseDefault {

  public static final int DEFAULT_GLOB_KEY_JWT_EXPIRATION_MINUTES = 1440;
  public static final int DEFAULT_MAX_LIMIT_EXCEEDED_COUNT = 20;
  public static final int DEFAULT_TASK_DATA_DAYS_PRESERVE = 10;
  public static final int DEFAULT_ALERT_MAIL = Integer.MAX_VALUE;

  
  // User day entity limits
  public static final String GT_LIMIT_DAY = BaseConstants.GT_PREFIX + "limit.day.";

  public static final String GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD = GlobalParamKeyBaseDefault.GT_LIMIT_DAY
      + MailSettingForward.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL = GlobalParamKeyBaseDefault.GT_LIMIT_DAY
      + UDFMetadataGeneral.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_MAIL_SEND = GlobalParamKeyBaseDefault.GT_LIMIT_DAY
      + MailSendRecv.class.getSimpleName();

  // Set expiration time for the JWT token.
  public static final String GLOB_KEY_JWT_EXPIRATION_MINUTES = BaseConstants.GT_PREFIX + "jwt.expiration.minutes";

  // Alert bitmap for sending email
  public static final String GLOB_KEY_ALERT_MAIL = BaseConstants.GT_PREFIX + "alert.mail.bitmap";

  // Password regular expression properties
  public static final String GLOB_KEY_PASSWORT_REGEX = BaseConstants.GT_PREFIX + "password.regex" + BaseConstants.BLOB_PROPERTIES;

  public static final String GLOB_KEY_TASK_DATA_DAYS_PRESERVE = BaseConstants.GT_PREFIX + "task.data.days.preserve";
  public static final int DEFAULT_MAX_SECURITY_BREACH_COUNT = 5;



 


  public GlobalParamKeyBaseDefault() {
    Map<String, MaxDefaultDBValue> defaultLimitMap = Globalparameters.defaultLimitMap;
 // Set tenant regulations violations, with daily CUD limits on user or tenant
    // own entries
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_MAIL_SEND, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_MAILSETTINGFORWARD, new MaxDefaultDBValue(12));

   
    defaultLimitMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_LIMIT_DAY_UDFMETADATAGENERAL, new MaxDefaultDBValue(20));
 
  }
}
