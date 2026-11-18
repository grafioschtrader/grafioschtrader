package grafiosch.security;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafiosch.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Completes a proven identity without reloading the credential snapshot used to verify it. */
@Service
public class LoginCompletionService {
  private final LoginAttemptServiceIpAddress attempts;
  private final UserService users;
  private final TokenAuthentication tokens;

  public LoginCompletionService(LoginAttemptServiceIpAddress attempts, UserService users, TokenAuthentication tokens) {
    this.attempts = attempts;
    this.users = users;
    this.tokens = tokens;
  }

  public void complete(HttpServletRequest request, HttpServletResponse response, User user, Integer timezoneOffset,
      boolean passwordRegexOk) throws IOException {
    attempts.loginSucceeded(request);
    users.checkUserLimits(user);
    if (timezoneOffset != null && !timezoneOffset.equals(user.getTimezoneOffset())) {
      // Update only the preference. A concurrently reset password/version must never be overwritten.
      users.updateTimezoneOffset(user, timezoneOffset);
    }
    UserAuthentication authentication = new UserAuthentication(user);
    tokens.addJwtTokenToHeader(response, authentication, passwordRegexOk);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
