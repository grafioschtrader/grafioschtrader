package grafiosch.service;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class LoginAttemptServiceIpAddress {

  private PassiveExpiringMap<String, Integer> attemptsIPAdressCache;

  public LoginAttemptServiceIpAddress() {
    super();
    attemptsIPAdressCache = new PassiveExpiringMap<>(BaseConstants.SUSPEND_IP_ADDRESS_TIME);
  }

  public void loginSucceeded(HttpServletRequest request) {
    attemptsIPAdressCache.remove(getClientIP(request));
  }

  public void loginFailed(HttpServletRequest request) {
    String ipAddress = getClientIP(request);
    int attempts = attemptsIPAdressCache.getOrDefault(ipAddress, 0);
    attempts++;
    attemptsIPAdressCache.put(ipAddress, attempts);
  }

  public boolean isBlocked(HttpServletRequest request) {
    Integer attempts = attemptsIPAdressCache.getOrDefault(getClientIP(request), 0);
    return attempts >= BaseConstants.MAX_LOGIN_ATTEMPT;
  }

  private String getClientIP(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null) {
      return request.getRemoteAddr();
    }
    return xfHeader.split(",")[0];
  }

}
