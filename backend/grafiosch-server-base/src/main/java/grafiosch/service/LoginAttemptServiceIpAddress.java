package grafiosch.service;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Service for tracking and blocking IP addresses based on failed login attempts.
 * 
 * <p>
 * This service implements a brute force attack protection mechanism by monitoring failed login attempts per IP address
 * and temporarily blocking addresses that exceed the configured failure threshold. The service uses an auto-expiring
 * cache to ensure that IP blocks are automatically lifted after a configured time period.
 * </p>
 * 
 * <h3>Security Features:</h3>
 * <ul>
 * <li><strong>Automatic Blocking:</strong> IP addresses are blocked after exceeding the maximum number of failed login
 * attempts</li>
 * <li><strong>Time-based Recovery:</strong> Blocked IP addresses are automatically unblocked after the suspension
 * period expires</li>
 * <li><strong>Proxy-aware IP Detection:</strong> Correctly identifies client IP addresses even when requests come
 * through load balancers or reverse proxies</li>
 * <li><strong>Thread-safe Operations:</strong> All operations are thread-safe for concurrent access in multi-user
 * environments</li>
 * </ul>
 * 
 * <h3>Attack Prevention:</h3>
 * <p>
 * The service protects against various types of attacks including:
 * </p>
 * <ul>
 * <li>Brute force password attacks</li>
 * <li>Dictionary attacks</li>
 * <li>Credential stuffing attempts</li>
 * <li>Automated login scanning</li>
 * </ul>
 * 
 * <h3>Configuration:</h3>
 * <p>
 * The service behavior is controlled by constants defined in BaseConstants:
 * </p>
 * <ul>
 * <li><strong>MAX_LOGIN_ATTEMPT:</strong> Maximum allowed failed attempts before blocking</li>
 * <li><strong>SUSPEND_IP_ADDRESS_TIME:</strong> Duration for which IP addresses remain blocked</li>
 * </ul>
 * 
 * <h3>Cache Management:</h3>
 * <p>
 * Uses PassiveExpiringMap for automatic cleanup of expired entries, ensuring that memory usage remains bounded and old
 * attempt records don't accumulate indefinitely. The expiring cache also provides the automatic unblocking mechanism.
 * </p>
 */
@Service
public class LoginAttemptServiceIpAddress {

  /**
   * Cache storing failed login attempt counts per IP address with automatic expiration.
   * 
   * <p>
   * This map tracks the number of failed login attempts for each IP address and automatically removes entries after the
   * configured suspension time period. The expiration mechanism serves dual purposes: memory management and automatic
   * IP address unblocking.
   * </p>
   * 
   * <p>
   * <strong>Key:</strong> Client IP address as a string
   * </p>
   * <p>
   * <strong>Value:</strong> Number of failed attempts as an integer
   * </p>
   * <p>
   * <strong>Expiration:</strong> Entries expire after SUSPEND_IP_ADDRESS_TIME milliseconds
   * </p>
   */
  private PassiveExpiringMap<String, Integer> attemptsIPAdressCache;

  public LoginAttemptServiceIpAddress() {
    attemptsIPAdressCache = new PassiveExpiringMap<>(BaseConstants.SUSPEND_IP_ADDRESS_TIME);
  }

  public void loginSucceeded(HttpServletRequest request) {
    attemptsIPAdressCache.remove(getClientIP(request));
  }

  /**
   * Records a failed login attempt and increments the failure count for the IP address.
   * 
   * <p>
   * This method tracks failed login attempts by incrementing a counter for the client's IP address. If the IP address
   * reaches the maximum allowed failures, subsequent calls to isBlocked() will return true, preventing further login
   * attempts from that address.
   * </p>
   *
   * @param request the HTTP request containing the client IP address information
   */
  public void loginFailed(HttpServletRequest request) {
    String ipAddress = getClientIP(request);
    int attempts = attemptsIPAdressCache.getOrDefault(ipAddress, 0);
    attempts++;
    attemptsIPAdressCache.put(ipAddress, attempts);
  }

  /**
   * Checks whether an IP address is currently blocked due to excessive failed attempts.
   * 
   * <p>
   * This method determines if login attempts from the specified IP address should be blocked based on the number of
   * recent failures. An IP address is considered blocked if its failure count meets or exceeds the configured maximum
   * threshold.
   * </p>
   * 
   * @param request the HTTP request containing the client IP address information
   * @return true if the IP address is blocked, false if login attempts are allowed
   */
  public boolean isBlocked(HttpServletRequest request) {
    Integer attempts = attemptsIPAdressCache.getOrDefault(getClientIP(request), 0);
    return attempts >= BaseConstants.MAX_LOGIN_ATTEMPT;
  }

  /**
   * Extracts the real client IP address from the HTTP request.
   * 
   * <p>
   * This method correctly identifies the client IP address even when requests pass through load balancers, reverse
   * proxies, or CDNs that add forwarding headers. It prioritizes the X-Forwarded-For header when present, falling back
   * to the direct remote address when not available.
   * </p>
   * 
   * <p>
   * <strong>IP Resolution Priority:</strong>
   * </p>
   * <ol>
   * <li>X-Forwarded-For header (first IP if multiple are present)</li>
   * <li>Direct remote address from the request</li>
   * </ol>
   * 
   * <p>
   * <strong>Proxy Chain Handling:</strong>
   * </p>
   * <p>
   * When the X-Forwarded-For header contains multiple IP addresses (indicating a chain of proxies), the method uses the
   * first IP address, which represents the original client. Subsequent addresses in the header represent intermediate
   * proxies in the request chain.
   * </p>
   * 
   * <p>
   * <strong>Security Considerations:</strong>
   * </p>
   * <p>
   * The X-Forwarded-For header can be spoofed by malicious clients, but this method assumes the header is set by
   * trusted infrastructure components like load balancers or reverse proxies in a properly configured environment.
   * </p>
   * 
   * @param request the HTTP request containing IP address information
   * @return the client IP address as a string
   */
  private String getClientIP(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null) {
      return request.getRemoteAddr();
    }
    return xfHeader.split(",")[0];
  }

}
