package grafiosch.security.filter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.dto.UserDTO;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.User;
import grafiosch.error.ErrorWithLogout;
import grafiosch.error.ImpatientAtLoginError;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.repository.ProposeUserTaskJpaRepository;
import grafiosch.rest.helper.RestErrorHandler;
import grafiosch.security.CustomAuthenticationFailureHandler;
import grafiosch.security.TokenAuthentication;
import grafiosch.security.UserAuthentication;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafiosch.service.UserService;
import grafiosch.usertask.UserTaskType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security filter for processing user login authentication with enhanced security features.
 * 
 * <p>
 * This filter extends AbstractAuthenticationProcessingFilter to handle the complete login workflow including JSON
 * request parsing, authentication validation, IP address blocking, user limit checking, and JWT token generation. It
 * implements a stateless authentication approach suitable for REST API applications.
 * </p>
 * 
 * <h3>Security Features:</h3>
 * <ul>
 * <li><strong>IP Address Protection:</strong> Blocks login attempts from IP addresses that exceed failed login attempt
 * limits to prevent brute force attacks</li>
 * <li><strong>Password Validation:</strong> Validates passwords against configurable regex patterns for security
 * compliance</li>
 * <li><strong>User Limit Enforcement:</strong> Checks and enforces user-specific limits for security breaches and
 * request violations</li>
 * <li><strong>Account Lockout Management:</strong> Handles locked user accounts with unlock request mechanisms</li>
 * </ul>
 * 
 * <h3>Authentication Flow:</h3>
 * <ol>
 * <li>Parse JSON login request containing user credentials and preferences</li>
 * <li>Check IP address against blocked address list</li>
 * <li>Validate password format against security regex patterns</li>
 * <li>Attempt authentication through Spring Security's AuthenticationManager</li>
 * <li>On success: generate JWT token, update user preferences, establish security context</li>
 * <li>On failure: track failed attempts, apply IP blocking if necessary</li>
 * </ol>
 * 
 * <h3>User Limit Handling:</h3>
 * <p>
 * The filter handles scenarios where users have exceeded security or request limits by providing mechanisms for unlock
 * requests. Users can submit notes explaining their situation to administrators for manual review and potential account
 * unlocking.
 * </p>
 * 
 * <h3>Token Management:</h3>
 * <p>
 * Upon successful authentication, the filter generates JWT tokens containing user information and configuration data,
 * then adds them to the HTTP response headers for subsequent stateless API requests.
 * </p>
 */
public class StatelessLoginFilter extends AbstractAuthenticationProcessingFilter {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** Service for JWT token generation and configuration data preparation. */
  private final TokenAuthentication tokenAuthentication;
  /** Service for user-related operations including authentication and limit checking. */
  private final UserService userService;
  /** Temporary storage for user values during the authentication process. */
  private final Map<String, HoldUserValues> emailToTimezoneOffsetMap = new ConcurrentHashMap<>();
  private final ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;
  private final MessageSource messages;
  /** Service for IP address-based login attempt tracking and blocking. */
  private final LoginAttemptServiceIpAddress loginAttemptServiceIpAddress;

  /**
   * Creates a new stateless login filter with the specified configuration.
   * 
   * <p>
   * Initializes the filter with all necessary services and dependencies for handling the complete authentication
   * workflow. Sets up the authentication manager and failure handler for proper Spring Security integration.
   * </p>
   * 
   * @param urlMapping                   the URL pattern this filter should process
   * @param tokenAuthentication          service for JWT token generation and management
   * @param userService                  service for user operations and validation
   * @param authenticationManager        Spring Security authentication manager
   * @param proposeUserTaskJpaRepository repository for user task proposals
   * @param messages                     message source for internationalized error messages
   */
  public StatelessLoginFilter(final String urlMapping, final TokenAuthentication tokenAuthentication,
      final UserService userService, final AuthenticationManager authenticationManager,
      ProposeUserTaskJpaRepository proposeUserTaskJpaRepository, final MessageSource messages) {
    super(urlMapping);
    this.tokenAuthentication = tokenAuthentication;
    this.userService = userService;
    setAuthenticationManager(authenticationManager);
    this.setAuthenticationFailureHandler(new CustomAuthenticationFailureHandler());
    this.proposeUserTaskJpaRepository = proposeUserTaskJpaRepository;
    this.messages = messages;
    this.loginAttemptServiceIpAddress = new LoginAttemptServiceIpAddress();
  }

  @Override
  public Authentication attemptAuthentication(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    if (loginAttemptServiceIpAddress.isBlocked(request)) {
      throw new LockedException("No login possible for this ip-address anymore");
    } else {
      UserDTO user = toUser(request);
      boolean passwordRegexOk = true;
      try {
        passwordRegexOk = userService.isPasswordAccepted(user.getPassword());
      } catch (Exception e) {
        log.error("Could not check password against regular expression", e);
      }
      emailToTimezoneOffsetMap.put(user.getEmail().get(),
          new HoldUserValues(passwordRegexOk, user.getTimezoneOffset(), user.getNote()));
      final UsernamePasswordAuthenticationToken loginToken = user.toAuthenticationToken();
      return getAuthenticationManager().authenticate(loginToken);
    }
  }

  /**
   * Parses user credentials from the JSON request body.
   * 
   * <p>
   * Converts the HTTP request input stream into a UserDTO object using Jackson ObjectMapper. The request body should
   * contain JSON data with user credentials and preferences.
   * </p>
   * 
   * @param request the HTTP request containing JSON user data
   * @return UserDTO object parsed from the request body
   * @throws IOException if JSON parsing fails or request stream is invalid
   */
  private UserDTO toUser(final HttpServletRequest request) throws IOException {
    return new ObjectMapper().readValue(request.getInputStream(), UserDTO.class);
  }

  @Override
  protected void successfulAuthentication(final HttpServletRequest request, final HttpServletResponse response,
      final FilterChain chain, final Authentication authResult) throws IOException, ServletException {
    UserDetails authenticatedUser = userService.loadUserByUsername(authResult.getName());
    HoldUserValues holdUserValue = emailToTimezoneOffsetMap.get(authenticatedUser.getUsername());
    try {
      loginAttemptServiceIpAddress.loginSucceeded(request);
      userService.checkUserLimits((User) authenticatedUser);
      emailToTimezoneOffsetMap.remove(authenticatedUser.getUsername());
      authenticatedUser = updateTimezoneOffset((User) authenticatedUser, holdUserValue.timezoneOffset);
      final UserAuthentication userAuthentication = new UserAuthentication(authenticatedUser);
      tokenAuthentication.addJwtTokenToHeader(response, userAuthentication, holdUserValue.passwordRegexOk);
      SecurityContextHolder.getContext().setAuthentication(userAuthentication);

    } catch (RequestLimitAndSecurityBreachException lee) {
      // User has to many times misused the limits (requests/period or security breach) of this application
      if (holdUserValue.note != null) {
        processUserUnlockNoteRequest(authenticatedUser, response, lee, holdUserValue);
      } else {
        requestForUserUnlock(authenticatedUser, response, lee);
      }
    }
  }

  @Override
  protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException failed) throws IOException, ServletException {
    loginAttemptServiceIpAddress.loginFailed(request);
    super.unsuccessfulAuthentication(request, response, failed);
  }

  /**
   * Processes unlock requests when users provide explanatory notes.
   * 
   * <p>
   * When a user exceeds limits but provides a note explaining their situation, this method creates an unlock request
   * for administrative review. The request includes the user's explanation and the specific limit that was exceeded.
   * </p>
   * 
   * <p>
   * <strong>Error Handling:</strong>
   * </p>
   * <p>
   * If the unlock request creation fails (e.g., due to too frequent requests), the method clears the security context
   * and returns an appropriate error response to prevent user impatience and request flooding.
   * </p>
   * 
   * @param authenticatedUser the user requesting unlock
   * @param response          the HTTP response for result delivery
   * @param lee               the limit exception containing violation details
   * @param holdUserValue     the user values including the explanatory note
   */
  private void processUserUnlockNoteRequest(final UserDetails authenticatedUser, final HttpServletResponse response,
      RequestLimitAndSecurityBreachException lee, HoldUserValues holdUserValue) throws IOException {
    try {
      proposeUserTaskJpaRepository.createReleaseLougout(((User) authenticatedUser).getIdUser(),
          lee.getFieldToBeChangedInUser(), holdUserValue.note);
    } catch (Exception e) {
      SecurityContextHolder.clearContext();
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.TOO_MANY_REQUESTS,
          new ImpatientAtLoginError(lee.getMessage()));
    }
  }

  /**
   * Requests user to provide explanatory note for account unlocking.
   * 
   * <p>When a user exceeds limits without providing a note, this method guides them
   * through the unlock request process. It checks for existing unlock requests to
   * avoid duplicate submissions and provides appropriate feedback messages.</p>
   * 
   * <p><strong>Response Logic:</strong></p>
   * <ul>
   *   <li>If no existing unlock requests: prompts user to submit unlock request with note</li>
   *   <li>If unlock request already exists: informs user that request is pending review</li>
   *   <li>All responses include localized messages based on user preferences</li>
   * </ul>
   * 
   * @param authenticatedUser the user needing unlock guidance
   * @param response the HTTP response for message delivery
   * @param lee the limit exception containing violation details
   */
  private void requestForUserUnlock(final UserDetails authenticatedUser, final HttpServletResponse response,
      RequestLimitAndSecurityBreachException lee) throws IOException {
    SecurityContextHolder.clearContext();
    List<ProposeUserTask> existingProposeUserTasks = proposeUserTaskJpaRepository.findByIdTargetUserAndUserTaskType(
        ((User) authenticatedUser).getIdUser(), UserTaskType.RELEASE_LOGOUT.getValue());
    if (existingProposeUserTasks.isEmpty()) {
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.TOO_MANY_REQUESTS,
          new ErrorWithLogout(lee.getMessage()));
    } else {
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.TOO_MANY_REQUESTS,
          new ImpatientAtLoginError(messages.getMessage("rights.limit.send", null,
              Locale.forLanguageTag(((User) authenticatedUser).getLocaleStr()))));
    }
  }

  /**
   * Updates user timezone offset if it has changed since last login.
   * 
   * <p>Compares the current timezone offset with the user's stored preference
   * and updates the database if they differ. This ensures that user timezone
   * preferences are kept current with their client configuration.</p>
   * 
   * @param user the user whose timezone offset may need updating
   * @param timezoneOffset the new timezone offset from the login request
   * @return the user object, potentially with updated timezone offset
   */
  private User updateTimezoneOffset(User user, int timezoneOffst) throws IOException {
    if (!user.getTimezoneOffset().equals(timezoneOffst)) {
      user = this.userService.updateTimezoneOffset(user, timezoneOffst);
    }
    return user;
  }

  /**
   * Data holder class for preserving user values during authentication process.
   * 
   * <p>
   * This static inner class temporarily stores user preferences and validation results that need to be preserved
   * between the authentication attempt phase and the successful authentication completion phase. The data is stored in
   * the emailToTimezoneOffsetMap during processing.
   * </p>
   * 
   * <h3>Stored Information:</h3>
   * <ul>
   * <li><strong>Password Validation:</strong> Whether the password meets regex requirements</li>
   * <li><strong>Timezone Preference:</strong> User's current timezone offset setting</li>
   * <li><strong>Unlock Note:</strong> Optional explanatory note for limit violation appeals</li>
   * </ul>
   */
  static class HoldUserValues {
    /** Flag indicating whether the password meets configured regex requirements. */
    public final boolean passwordRegexOk;
    /** User's timezone offset in minutes from UTC. */
    public final Integer timezoneOffset;
    /** Optional explanatory note for unlock requests when limits are exceeded. */
    public final String note;

    public HoldUserValues(boolean passwordRegexOk, Integer timezoneOffset, String note) {
      this.passwordRegexOk = passwordRegexOk;
      this.timezoneOffset = timezoneOffset;
      this.note = note;
    }
  }

}