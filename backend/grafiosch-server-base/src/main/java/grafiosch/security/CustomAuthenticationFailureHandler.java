package grafiosch.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom authentication failure handler for Spring Security login failures.
 * 
 * <p>
 * This handler provides customized responses for authentication failures, distinguishing between general login failures
 * and account lockout scenarios. It returns simple text responses with message keys that can be localized by client
 * applications for appropriate user messaging.
 * </p>
 * 
 * <h3>Response Handling:</h3>
 * <ul>
 * <li><strong>Account Locked:</strong> Returns "login.ipaddress.locked" for LockedException</li>
 * <li><strong>General Failure:</strong> Returns "login.failure" for other authentication errors</li>
 * <li><strong>HTTP Status:</strong> Always returns 401 Unauthorized for all failures</li>
 * </ul>
 * 
 * <h3>Integration:</h3>
 * <p>
 * Used by Spring Security authentication filters to provide consistent error responses that can be processed by client
 * applications for proper user notification and error handling.
 * </p>
 */
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

  /**
   * Handles authentication failure by returning appropriate error messages.
   * 
   * <p>
   * This method processes authentication failures and returns different message keys based on the type of
   * authentication exception. It distinguishes between account lockout scenarios and general authentication failures to
   * provide more specific user guidance.
   * </p>
   * 
   * <h3>Response Logic:</h3>
   * <ul>
   * <li><strong>LockedException:</strong> Returns "login.ipaddress.locked" message key indicating account or IP address
   * lockout</li>
   * <li><strong>Other Exceptions:</strong> Returns "login.failure" message key for general authentication failures</li>
   * </ul>
   * 
   * <h3>Response Format:</h3>
   * <p>
   * Returns plain text responses with localization keys that client applications can use to display appropriate
   * localized error messages to users. The HTTP status is always set to 401 Unauthorized regardless of the failure
   * type.
   * </p>
   * 
   * @param request  the HTTP request that resulted in authentication failure
   * @param response the HTTP response to write the error information to
   * @param e        the authentication exception that caused the failure
   * @throws IOException      if response writing fails
   * @throws ServletException if servlet processing fails
   */
  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException e) throws IOException, ServletException {
    HttpServletResponse httpResponse = response;
    httpResponse.setContentType("text/plain");
    httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
    httpResponse.getWriter().append(e instanceof LockedException ? "login.ipaddress.locked" : "login.failure");
  }
}
