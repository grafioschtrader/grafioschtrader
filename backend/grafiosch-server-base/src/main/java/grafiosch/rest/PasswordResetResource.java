package grafiosch.rest;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.projection.SuccessfullyChanged;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.service.CredentialChangeService;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafiosch.service.PasswordResetRequestLimiter;
import grafiosch.service.PasswordResetService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;

/** Public mailbox-based recovery. All request replies deliberately reveal the same information. */
@RestController
@RequestMapping(RequestMappings.USER_MAP + "/passwordreset")
public class PasswordResetResource {
  private final PasswordResetService resets;
  private final CredentialChangeService credentials;
  private final LoginAttemptServiceIpAddress attempts;
  private final PasswordResetRequestLimiter limiter;
  private final MessageSource messages;

  public PasswordResetResource(PasswordResetService resets, CredentialChangeService credentials,
      LoginAttemptServiceIpAddress attempts, PasswordResetRequestLimiter limiter, MessageSource messages) {
    this.resets = resets;
    this.credentials = credentials;
    this.attempts = attempts;
    this.limiter = limiter;
    this.messages = messages;
  }

  @Schema(description = "Address to receive a password reset token, when eligible")
  public record ResetRequest(String email) {
  }

  @Schema(description = "Mailbox proof and replacement password; the proof is consumed on success only")
  public record ResetConfirmation(String token, String passwordNew) {
  }

  @PostMapping
  public ResponseEntity<?> request(@RequestBody ResetRequest body, HttpServletRequest request, Locale locale) {
    if (attempts.isBlocked(request)) {
      return blocked();
    }
    if (!limiter.tryConsume(attempts.clientAddress(request))) {
      return ResponseEntity.status(429).build();
    }
    resets.request(body.email());
    return ResponseEntity
        .ok(new SuccessfullyChanged(false, messages.getMessage("password.reset.requested", null, locale)));
  }

  @PostMapping("/confirm")
  public ResponseEntity<?> confirm(@RequestBody ResetConfirmation body, HttpServletRequest request, Locale locale)
      throws Exception {
    if (attempts.isBlocked(request)) {
      return blocked();
    }
    if (credentials.resetPassword(body.token(), body.passwordNew()) == null) {
      attempts.loginFailed(request);
      throw new GeneralNotTranslatedWithArgumentsException("password.reset.invalid", null);
    }
    return ResponseEntity
        .ok(new SuccessfullyChanged(true, messages.getMessage("password.reset.success", null, locale)));
  }

  private ResponseEntity<String> blocked() {
    return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("login.ipaddress.locked");
  }
}
