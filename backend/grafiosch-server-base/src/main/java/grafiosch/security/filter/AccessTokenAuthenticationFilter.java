package grafiosch.security.filter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.entities.UserAccessToken;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.rest.helper.RestErrorHandler;
import grafiosch.security.AccessTokenAuthenticationService;
import grafiosch.security.AccessTokenAuthenticationService.AuthenticatedToken;
import grafiosch.security.AccessTokenPrincipal;
import grafiosch.security.UserAuthentication;
import grafiosch.service.LoginAttemptServiceIpAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates a request with a personal access token sent as {@code Authorization: Bearer gpat_...}.
 *
 * <p>
 * The filter belongs only to the security chain an application declares for its machine endpoint; the main chain never
 * contains it, so a token is never accepted anywhere else. It is the only authentication filter of that chain, which
 * means a browser JWT authenticates nothing there either.
 * </p>
 *
 * <p>
 * A token lives for up to a year, so it must not travel in clear text. A request passes only when the client's
 * connection was encrypted, which {@link #isConfidential(HttpServletRequest)} decides from the connection itself (also
 * an AJP connection of an HTTPS Apache), from {@code X-Forwarded-Proto} of a trusted proxy, or for a direct client on
 * the same machine. The proxies are the same ones {@code g.security.login.trusted-proxies} lists for the login lockout,
 * so the installations keep {@code server.forward-headers-strategy=none}. Failed attempts are counted per source
 * address like failed logins, and a blocked address receives 429 until the suspension ends.
 * </p>
 *
 * <p>
 * Not a Spring bean on purpose: a filter bean would be registered with the servlet container as well and run in front
 * of every request.
 * </p>
 */
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

  private final AccessTokenAuthenticationService accessTokenAuthenticationService;
  private final LoginAttemptServiceIpAddress loginAttemptServiceIpAddress;
  private final Supplier<String> wwwAuthenticateValue;
  private final Supplier<String> expectedAudience;

  /**
   * @param accessTokenAuthenticationService resolves a token to its user
   * @param loginAttemptServiceIpAddress     counts failed attempts per source address
   * @param wwwAuthenticateValue             supplies the value of the {@code WWW-Authenticate} header of a 401 answer,
   *                                         for example {@code Bearer resource_metadata="..."}; asked on every refusal
   * @param expectedAudience                 supplies the canonical URI of the protected resource, which an OAuth access
   *                                         token must be issued for; null accepts personal tokens only
   */
  public AccessTokenAuthenticationFilter(AccessTokenAuthenticationService accessTokenAuthenticationService,
      LoginAttemptServiceIpAddress loginAttemptServiceIpAddress, Supplier<String> wwwAuthenticateValue,
      Supplier<String> expectedAudience) {
    this.accessTokenAuthenticationService = accessTokenAuthenticationService;
    this.loginAttemptServiceIpAddress = loginAttemptServiceIpAddress;
    this.wwwAuthenticateValue = wwwAuthenticateValue;
    this.expectedAudience = expectedAudience;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!isConfidential(request)) {
      reject(response, HttpStatus.FORBIDDEN, "HTTPS is required for access tokens");
      return;
    }
    if (loginAttemptServiceIpAddress.isBlocked(request)) {
      reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts from this address");
      return;
    }
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX + UserAccessToken.TOKEN_PREFIX)) {
      unauthorized(response);
      return;
    }
    Optional<AuthenticatedToken> authenticated;
    try {
      authenticated = accessTokenAuthenticationService.authenticate(header.substring(BEARER_PREFIX.length()).trim(),
          expectedAudience.get());
    } catch (RequestLimitAndSecurityBreachException e) {
      reject(response, HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
      return;
    } catch (AuthenticationException e) {
      unauthorized(response);
      return;
    }
    if (authenticated.isEmpty()) {
      loginAttemptServiceIpAddress.loginFailed(request);
      unauthorized(response);
      return;
    }
    loginAttemptServiceIpAddress.loginSucceeded(request);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UserAuthentication(authenticated.get().user()));
    SecurityContextHolder.setContext(context);
    request.setAttribute(AccessTokenPrincipal.REQUEST_ATTRIBUTE, authenticated.get().principal());
    chain.doFilter(request, response);
  }

  private void unauthorized(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticateValue.get());
    reject(response, HttpStatus.UNAUTHORIZED, "A valid access token is required");
  }

  private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
    SecurityContextHolder.clearContext();
    RestErrorHandler.createErrorResponseForServlet(response, status, new SingleNativeMsgError(message));
  }

  /**
   * Decides whether the client reached this server over an encrypted connection.
   *
   * <ul>
   * <li>The connection is secure itself: direct HTTPS, or AJP from an Apache that serves HTTPS.</li>
   * <li>The direct peer is a trusted proxy and its {@code X-Forwarded-Proto} says {@code https}; a chain of values must
   * consist of {@code https} only. A header from any other peer is ignored, because a client can send it freely.</li>
   * <li>No proxy is involved: a loopback peer without {@code X-Forwarded-Proto} is a client on the same machine.</li>
   * </ul>
   *
   * @param request the incoming request
   * @return true when the token did not travel in clear text over a network
   */
  private boolean isConfidential(HttpServletRequest request) {
    if (request.isSecure()) {
      return true;
    }
    String forwardedProto = request.getHeader(X_FORWARDED_PROTO);
    if (forwardedProto != null && loginAttemptServiceIpAddress.isTrustedProxy(request.getRemoteAddr())) {
      return Arrays.stream(forwardedProto.split(",")).map(String::trim).allMatch("https"::equalsIgnoreCase);
    }
    return forwardedProto == null && isLoopback(request.getRemoteAddr());
  }

  private static boolean isLoopback(String remoteAddress) {
    try {
      return remoteAddress != null && InetAddress.ofLiteral(remoteAddress).isLoopbackAddress();
    } catch (IllegalArgumentException _) {
      return false;
    }
  }
}
