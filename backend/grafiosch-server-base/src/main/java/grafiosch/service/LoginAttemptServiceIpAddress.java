package grafiosch.service;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.beans.factory.annotation.Value;
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

  /** Literal proxy addresses allowed to supply forwarding information; never resolved through DNS. */
  private final Set<String> trustedProxies;

  /**
   * Creates the login counter with an explicit proxy trust boundary.
   *
   * @param trustedProxyAddresses comma-separated literal IPv4/IPv6 addresses; empty disables header trust
   */
  public LoginAttemptServiceIpAddress(
      @Value("${g.security.login.trusted-proxies:127.0.0.1,::1}") String trustedProxyAddresses) {
    trustedProxies = Arrays.stream(trustedProxyAddresses.split(",")).map(String::trim).filter(s -> !s.isEmpty())
        .map(address -> InetAddress.ofLiteral(address).getHostAddress()).collect(Collectors.toUnmodifiableSet());
    attemptsIPAdressCache = new PassiveExpiringMap<>(BaseConstants.SUSPEND_IP_ADDRESS_TIME);
  }

  public synchronized void loginSucceeded(HttpServletRequest request) {
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
  public synchronized void loginFailed(HttpServletRequest request) {
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
  public synchronized boolean isBlocked(HttpServletRequest request) {
    Integer attempts = attemptsIPAdressCache.getOrDefault(getClientIP(request), 0);
    return attempts >= BaseConstants.MAX_LOGIN_ATTEMPT;
  }

  /**
   * Walks the forwarding chain from the direct peer toward the client, stopping at the first untrusted address. Direct
   * HTTP and AJP clients cannot choose their counter through a header. Malformed hops fall back to the peer; equivalent
   * IPv6 spellings use one counter. Trusted edge proxies must overwrite client-supplied headers.
   *
   * @param request request before any container/filter forwarding-address rewriting
   * @return canonical client address, or the direct peer when no trustworthy chain is available
   */
  private String getClientIP(HttpServletRequest request) {
    String peer = literalAddress(request.getRemoteAddr());
    if (peer == null) {
      return request.getRemoteAddr();
    }
    if (!trustedProxies.contains(peer)) {
      return peer;
    }
    String header = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
    if (header.isEmpty()) {
      return peer;
    }
    String current = peer;
    String[] hops = header.split(",", -1);
    for (int i = hops.length - 1; i >= 0 && trustedProxies.contains(current); i--) {
      current = literalAddress(hops[i].trim());
      if (current == null) {
        return peer;
      }
    }
    return current;
  }

  private String literalAddress(String address) {
    try {
      return address == null || address.contains("%") ? null : InetAddress.ofLiteral(address).getHostAddress();
    } catch (IllegalArgumentException _) {
      return null;
    }
  }
}
