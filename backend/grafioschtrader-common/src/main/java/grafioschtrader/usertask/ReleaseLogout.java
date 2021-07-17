package grafioschtrader.usertask;

import java.io.Serializable;

public class ReleaseLogout implements Serializable {
  private static final long serialVersionUID = 1L;
  private short securityBreachCount;
  private short limitRequestExceedCount;

  public ReleaseLogout() {
  }

  public short getSecurityBreachCount() {
    return securityBreachCount;
  }

  public void setSecurityBreachCount(short securityBreachCount) {
    this.securityBreachCount = securityBreachCount;
  }

  public short getLimitRequestExceedCount() {
    return limitRequestExceedCount;
  }

  public void setLimitRequestExceedCount(short limitRequestExceedCount) {
    this.limitRequestExceedCount = limitRequestExceedCount;
  }

}
