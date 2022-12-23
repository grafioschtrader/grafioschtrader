package grafioschtrader.security.filter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import grafioschtrader.dto.UserDTO;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.User;
import grafioschtrader.error.ErrorWithLogout;
import grafioschtrader.error.ImpatientAtLoginError;
import grafioschtrader.exceptions.RequestLimitAndSecurityBreachException;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.rest.helper.RestErrorHandler;
import grafioschtrader.security.CustomAuthenticationFailureHandler;
import grafioschtrader.security.TokenAuthenticationService;
import grafioschtrader.security.UserAuthentication;
import grafioschtrader.service.LoginAttemptServiceIpAddress;
import grafioschtrader.service.UserService;
import grafioschtrader.usertask.UserTaskType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter which is used at the login process.
 *
 */
public class StatelessLoginFilter extends AbstractAuthenticationProcessingFilter {

  private final TokenAuthenticationService tokenAuthenticationService;
  private final UserService userService;
  private final Map<String, HoldUserValues> emailToTimezoneOffsetMap = new ConcurrentHashMap<>();
  private final ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;
  private final MessageSource messages;
  private final LoginAttemptServiceIpAddress loginAttemptServiceIpAddress;

  public StatelessLoginFilter(final String urlMapping, final TokenAuthenticationService tokenAuthenticationService,
      final UserService userService, final AuthenticationManager authenticationManager,
      ProposeUserTaskJpaRepository proposeUserTaskJpaRepository, final MessageSource messages) {
    super(urlMapping);
    this.tokenAuthenticationService = tokenAuthenticationService;
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
      emailToTimezoneOffsetMap.put(user.getEmail().get(), new HoldUserValues(user.getTimezoneOffset(), user.getNote()));
      final UsernamePasswordAuthenticationToken loginToken = user.toAuthenticationToken();
      return getAuthenticationManager().authenticate(loginToken);
    }
  }

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
      tokenAuthenticationService.addJwtTokenToHeader(response, userAuthentication);
      SecurityContextHolder.getContext().setAuthentication(userAuthentication);

    } catch (RequestLimitAndSecurityBreachException lee) {
      // User has to many times misused the limits (requests/period or security
      // breach) of Grafioschtrader
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
   * User has send note
   *
   * @param authenticatedUser
   * @param response
   * @param lee
   * @param holdUserValue
   * @throws IOException
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
   * Ask user for a note to possibly unlock him.
   *
   * @param authenticatedUser
   * @param response
   * @param lee
   * @throws IOException
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

  private User updateTimezoneOffset(User user, int timezoneOffst) throws IOException {
    if (!user.getTimezoneOffset().equals(timezoneOffst)) {
      user = this.userService.updateTimezoneOffset(user, timezoneOffst);
    }
    return user;
  }

  static class HoldUserValues {
    public final Integer timezoneOffset;
    public final String note;

    public HoldUserValues(Integer timezoneOffset, String note) {
      super();
      this.timezoneOffset = timezoneOffset;
      this.note = note;
    }
  }

}