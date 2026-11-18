package grafiosch.rest;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.security.passkey.PasskeyService;
import grafiosch.service.LoginAttemptServiceIpAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Public sign-in with a passkey. Both endpoints answer a blocked client address like the password sign-in does. The
 * options endpoint is bounded per address and by the size of the challenge store; a successful sign-in answers with
 * the same token header and configuration body as the password sign-in.
 */
@RestController
@RequestMapping(RequestMappings.API + "login/passkey")
@Tag(name = "login", description = "Sign-in with a passkey")
public class PasskeyLoginResource {
  private final PasskeyService passkeys;
  private final LoginAttemptServiceIpAddress attempts;

  public PasskeyLoginResource(PasskeyService passkeys, LoginAttemptServiceIpAddress attempts) {
    this.passkeys = passkeys;
    this.attempts = attempts;
  }

  @Operation(summary = "Start a passkey sign-in", description = """
      Returns { challengeId, publicKey } with PublicKeyCredentialRequestOptions for navigator.credentials.get. No
      e-mail address is needed; the browser offers the discoverable passkeys of this site.""")
  @PostMapping(value = "/options", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> options(HttpServletRequest request) {
    if (attempts.isBlocked(request)) {
      return blocked();
    }
    String options = passkeys.signInOptions(request);
    if (options == null) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
        .body(options);
  }

  @Operation(summary = "Complete a passkey sign-in", description = """
      Body { challengeId, credential, timezoneOffset } with the credential as PublicKeyCredential.toJSON() produces it.
      The answer carries the token in x-auth-token and the configuration in the body.""")
  @PostMapping
  public void signIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (attempts.isBlocked(request)) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.TEXT_PLAIN_VALUE);
      response.getWriter().append("login.ipaddress.locked");
      return;
    }
    passkeys.signIn(request, response);
  }

  private static ResponseEntity<String> blocked() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.TEXT_PLAIN)
        .body("login.ipaddress.locked");
  }
}
