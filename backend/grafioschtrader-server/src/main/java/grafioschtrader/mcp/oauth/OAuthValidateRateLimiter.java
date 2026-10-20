package grafioschtrader.mcp.oauth;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafiosch.service.LoginAttemptServiceIpAddress;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Bounds the anonymous validation of authorization requests to 30 per client address within an hour. The address is
 * resolved the way the login lockout resolves it, so a trusted proxy's forwarding header names the client and an
 * untrusted one is ignored. The window of an address starts with its first request and is fixed: further requests do
 * not prolong it.
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthValidateRateLimiter {

  public static final int MAX_REQUESTS_PER_HOUR = 30;
  /** More addresses than this within an hour are treated as one flood and refused as a whole. */
  public static final int MAX_TRACKED_ADDRESSES = 10_000;
  private static final long WINDOW_NANOS = Duration.ofHours(1).toNanos();

  /** Start of the window and number of requests of one address. */
  private static final class Window {
    final long startNanos;
    int count;

    Window(long startNanos) {
      this.startNanos = startNanos;
    }
  }

  private final LoginAttemptServiceIpAddress loginAttemptServiceIpAddress;
  /** In order of the window start, so expired windows are always at the front. */
  private final Map<String, Window> windows = new LinkedHashMap<>();

  public OAuthValidateRateLimiter(LoginAttemptServiceIpAddress loginAttemptServiceIpAddress) {
    this.loginAttemptServiceIpAddress = loginAttemptServiceIpAddress;
  }

  /**
   * Counts a validation of the request's client address.
   *
   * @return true when the address is still within its budget
   */
  public synchronized boolean tryAcquire(HttpServletRequest request) {
    long now = System.nanoTime();
    Iterator<Window> it = windows.values().iterator();
    while (it.hasNext() && now - it.next().startNanos >= WINDOW_NANOS) {
      it.remove();
    }
    String address = loginAttemptServiceIpAddress.clientAddress(request);
    Window window = windows.get(address);
    if (window == null) {
      if (windows.size() >= MAX_TRACKED_ADDRESSES) {
        return false;
      }
      window = new Window(now);
      windows.put(address, window);
    }
    if (window.count >= MAX_REQUESTS_PER_HOUR) {
      return false;
    }
    window.count++;
    return true;
  }
}
