package grafiosch;

public class BaseConstants {

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

}
