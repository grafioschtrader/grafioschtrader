package grafiosch.security.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.error.ErrorWithLogout;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.rest.helper.RestErrorHandler;
import grafiosch.security.TokenAuthentication;
import grafiosch.service.UserService;
import grafiosch.types.UserRightLimitCounter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security filter for stateless authentication and request rate limiting on API requests.
 * 
 * <p>
 * This filter processes every API request after the initial login authentication to validate JWT tokens, establish
 * security context, and enforce per-user request rate limits. It implements a comprehensive protection mechanism
 * against API abuse while maintaining stateless operation suitable for REST API applications.
 * </p>
 * 
 * <h3>Authentication Processing:</h3>
 * <ul>
 * <li><strong>JWT Token Validation:</strong> Extracts and validates JWT tokens from request headers</li>
 * <li><strong>Security Context Establishment:</strong> Sets up Spring Security context for authenticated users</li>
 * <li><strong>Stateless Operation:</strong> No server-side session dependency, purely token-based</li>
 * <li><strong>Error Handling:</strong> Comprehensive handling of authentication and authorization failures</li>
 * </ul>
 * 
 * <h3>Rate Limiting System:</h3>
 * <p>
 * Implements a sophisticated rate limiting mechanism using the token bucket algorithm to prevent API abuse and ensure
 * fair resource usage across all users. The system enforces both minute-level and hour-level limits to handle different
 * types of usage patterns.
 * </p>
 * 
 * <h3>Rate Limiting Features:</h3>
 * <ul>
 * <li><strong>Per-User Buckets:</strong> Individual rate limits for each authenticated user</li>
 * <li><strong>Multi-Tier Limits:</strong> Separate limits for minute and hour time windows</li>
 * <li><strong>Automatic Refill:</strong> Token buckets refill automatically based on configured rates</li>
 * <li><strong>Violation Tracking:</strong> Records and tracks rate limit violations for security monitoring</li>
 * </ul>
 * 
 * <h3>Security Integration:</h3>
 * <p>
 * The filter integrates with the application's security monitoring system by tracking violations and maintaining user
 * security records. Rate limit violations are recorded as security events that can trigger account protection
 * mechanisms.
 * </p>
 * 
 * <h3>Configuration Control:</h3>
 * <p>
 * Rate limiting can be enabled or disabled through configuration, allowing for flexible deployment in different
 * environments (development, testing, production) with appropriate protection levels.
 * </p>
 */
public class StatelessAuthenticationFilter extends GenericFilterBean {

  /** Service for JWT token parsing and authentication object generation. */
  private final TokenAuthentication tokenAuthentication;
  private final MessageSource messages;

  /**
   * Per-user rate limiting buckets mapped by user ID.
   * 
   * <p>
   * This concurrent map maintains individual rate limiting buckets for each authenticated user. Each bucket implements
   * the token bucket algorithm with configured limits for minute and hour time windows. The map automatically creates
   * new buckets for users on their first request and provides thread-safe access in multi-user environments.
   * </p>
   * 
   * <p>
   * <strong>Key:</strong> User ID (Integer)
   * </p>
   * <p>
   * <strong>Value:</strong> Bucket4j Bucket with multi-tier rate limits
   * </p>
   */
  private final Map<Integer, Bucket> limitRateMap = new ConcurrentHashMap<>();
  /**
   * Service for user-related operations and violation tracking.
   * 
   * <p>
   * Used to increment security violation counters when users exceed rate limits, ensuring that repeated violations are
   * tracked and can trigger appropriate security responses.
   * </p>
   */
  private final UserService userService;
  /**
   * Configuration flag to enable or disable request rate limiting.
   * 
   * <p>
   * When true, the filter enforces rate limits on all authenticated requests. When false, requests pass through without
   * rate limiting, useful for development environments or specific deployment configurations.
   * </p>
   */
  private final boolean limitRequest;

  /**
   * Creates a new stateless authentication filter with the specified configuration.
   * 
   * <p>
   * Initializes the filter with all necessary dependencies for authentication validation and rate limiting. The filter
   * can be configured to enable or disable rate limiting based on deployment requirements.
   * </p>
   * 
   * @param tokenAuthentication service for JWT token processing and authentication
   * @param messages            message source for internationalized error messages
   * @param userService         service for user operations and violation tracking
   * @param limitRequest        flag to enable (true) or disable (false) rate limiting
   */
  public StatelessAuthenticationFilter(final TokenAuthentication tokenAuthentication, final MessageSource messages,
      final UserService userService, final boolean limitRequest) {
    this.tokenAuthentication = tokenAuthentication;
    this.messages = messages;
    this.userService = userService;
    this.limitRequest = limitRequest;
  }

  /**
   * Creates a new rate limiting bucket with configured minute and hour limits.
   * 
   * <p>
   * This method creates a token bucket with two bandwidth limits to enforce rate limiting at different time scales. The
   * minute limit handles short-term bursts while the hour limit manages longer-term usage patterns. Both limits must be
   * satisfied for a request to be allowed.
   * </p>
   * 
   * @return a new Bucket configured with minute and hour rate limits
   */
  private Bucket createNewBucket() {
    Bandwidth limitMinute = Bandwidth.classic(BaseConstants.BANDWITH_MINUTE_BUCKET_SIZE,
        Refill.greedy(BaseConstants.BANDWITH_MINUTE_REFILL, Duration.ofMinutes(1)));
    Bandwidth limitHour = Bandwidth.classic(BaseConstants.BANDWITH_HOOUR_BUCKET_SIZE,
        Refill.greedy(BaseConstants.BANDWITH_HOUR_REFILL, Duration.ofHours((1))));
    return Bucket.builder().addLimit(limitMinute).addLimit(limitHour).build();
  }

  @Override
  public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
      final FilterChain chain) throws IOException, ServletException {

    try {
      // System.out.println(((HttpServletRequest) servletRequest).getServletPath());

      Authentication authentication = tokenAuthentication
          .generateAuthenticationFromRequest((HttpServletRequest) servletRequest);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      if (authentication != null) {
        if (limitRequest) {
          doFilterWithlimitWatcher(servletRequest, servletResponse, chain, authentication);
        } else {
          chain.doFilter(servletRequest, servletResponse);
        }
      } else {
        chain.doFilter(servletRequest, servletResponse);
      }

//      SecurityContextHolder.getContext().setAuthentication(null);
    } catch (RequestLimitAndSecurityBreachException lee) {
      // User has to many times misused the limits of GT
      createErrorMessage(servletResponse, lee, HttpStatus.TOO_MANY_REQUESTS);
    } catch (AuthenticationException | JwtException e) {
      createErrorMessage(servletResponse, e, HttpStatus.UNAUTHORIZED);

    }
  }

  /**
   * Creates standardized error responses and cleans up security context.
   * 
   * <p>
   * This method handles error response generation for various authentication and authorization failures. It ensures
   * that the security context is properly cleared and that clients receive appropriate HTTP status codes and error
   * messages formatted for logout handling.
   * </p>
   * 
   * @param servletResponse the HTTP response for error delivery
   * @param e               the exception that triggered the error
   * @param httpStatus      the HTTP status code to return
   */
  private void createErrorMessage(final ServletResponse servletResponse, Exception e, HttpStatus httpStatus)
      throws IOException {
    SecurityContextHolder.clearContext();
    RestErrorHandler.createErrorResponseForServlet((HttpServletResponse) servletResponse, httpStatus,
        new ErrorWithLogout(e.getMessage()));
  }

  /**
   * Processes authenticated requests with rate limiting enforcement.
   * 
   * <p>
   * This method implements the rate limiting logic using the token bucket algorithm. Each authenticated user has an
   * individual bucket that tracks their request consumption against configured limits. When limits are exceeded, the
   * violation is recorded and an appropriate error response is generated.
   * </p>
   * 
   * <p>
   * <strong>Rate Limiting Process:</strong>
   * </p>
   * <ol>
   * <li>Extract user ID from the authentication object</li>
   * <li>Get or create rate limiting bucket for the user</li>
   * <li>Attempt to consume one token from the bucket</li>
   * <li>If successful: continue with request processing</li>
   * <li>If failed: record violation and return error response</li>
   * </ol>
   * 
   * <p>
   * <strong>Violation Handling:</strong>
   * </p>
   * <p>
   * When rate limits are exceeded, the method records the violation in the user's security record and generates a
   * localized error message. This tracking helps identify users who consistently abuse the API and may require
   * additional security measures.
   * </p>
   * 
   * @param servletRequest  the HTTP request being processed
   * @param servletResponse the HTTP response for potential error reporting
   * @param chain           the filter chain to continue processing if limits allow
   * @param authentication  the authenticated user's security context
   * @throws IOException      if request/response processing fails
   * @throws ServletException if servlet processing fails
   */
  private void doFilterWithlimitWatcher(final ServletRequest servletRequest, final ServletResponse servletResponse,
      final FilterChain chain, final Authentication authentication) throws IOException, ServletException {
    Integer idUser = ((User) authentication.getDetails()).getIdUser();
    Bucket bucket = limitRateMap.computeIfAbsent(idUser, k -> createNewBucket());
    if (bucket.tryConsume(1)) {
      // the limit is not exceeded
      chain.doFilter(servletRequest, servletResponse);
    } else {
      // limit is exceeded
      userService.incrementRightsLimitCount(idUser, UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA);
      String message = messages.getMessage("too.many.request", null,
          ((User) authentication.getDetails()).createAndGetJavaLocale());
      RestErrorHandler.createErrorResponseForServlet((HttpServletResponse) servletResponse,
          HttpStatus.TOO_MANY_REQUESTS, new SingleNativeMsgError(message));
    }
  }

}
