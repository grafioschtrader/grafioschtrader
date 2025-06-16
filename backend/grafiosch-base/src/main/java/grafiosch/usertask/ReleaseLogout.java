package grafiosch.usertask;

import java.io.Serializable;

/**
 * Model class representing user violation counters for release logout requests. This class is used when users request
 * administrative intervention to reset their violation counts and restore normal system access privileges.</br>
 * 
 * The class tracks two types of user violations:</br>
 * - Security breaches (unauthorized access attempts or data violations)</br>
 * - Request limit exceedances (too many requests within time constraints)</br>
 * 
 * When users accumulate violations, they may be restricted from system access. This model supports the proposal system
 * where users can request administrators to reset these counters and reinstate their access privileges.
 */
public class ReleaseLogout implements Serializable {
  private static final long serialVersionUID = 1L;
  private short securityBreachCount;
  private short limitRequestExceedCount;

  public ReleaseLogout() {
  }

  public short getSecurityBreachCount() {
    return securityBreachCount;
  }

  /**
   * Sets the security breach count for the user. This count is typically reset to zero when an administrator approves a
   * release logout request to restore the user's normal access privileges.
   *
   * @param securityBreachCount the new security breach count to set
   */
  public void setSecurityBreachCount(short securityBreachCount) {
    this.securityBreachCount = securityBreachCount;
  }

  /**
   * Gets the current count of request limit exceedances for the user. This tracks how many times the user has exceeded
   * the allowed number of requests within specified time periods, which may result in temporary access restrictions.
   *
   * @return the number of request limit violations recorded for the user
   */
  public short getLimitRequestExceedCount() {
    return limitRequestExceedCount;
  }

  /**
   * Sets the request limit exceedance count for the user. This count is typically reset to zero when an administrator
   * approves a release logout request to restore the user's normal request privileges.
   *
   * @param limitRequestExceedCount the new request limit exceedance count to set
   */
  public void setLimitRequestExceedCount(short limitRequestExceedCount) {
    this.limitRequestExceedCount = limitRequestExceedCount;
  }

}
