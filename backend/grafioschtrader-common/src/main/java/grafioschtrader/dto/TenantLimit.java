package grafioschtrader.dto;

import java.util.regex.Pattern;

public class TenantLimit {
  public int limit;
  public int actual;
  public String msgKey;
  public String className;

  public TenantLimit(String msgKey, int limit, int actual, String className) {
    this.limit = limit;
    this.actual = actual;
    this.msgKey = msgKey;
    this.className = className;
  }

  public TenantLimit(int limit, int actual, String key, String className) {
    this(key.substring(3).toUpperCase().replaceAll(Pattern.quote("."), "_"), limit, actual, className);
  }

}
