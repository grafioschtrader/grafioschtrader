package grafiosch.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.security.passkey.PasskeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public description of the sign-in methods this instance offers, read by the login page before anyone signs in. */
@RestController
@RequestMapping(RequestMappings.API + "login/methods")
@Tag(name = "login", description = "Sign-in methods of this instance")
public class LoginMethodResource {
  private final PasskeyService passkeys;

  public LoginMethodResource(PasskeyService passkeys) {
    this.passkeys = passkeys;
  }

  @Schema(description = "Sign-in methods besides e-mail and password")
  public record LoginMethods(@Schema(description = """
      Page origins on which a passkey works; empty when g.public.base.url is unset or no host name""")
  List<String> passkeyOrigins) {
  }

  @Operation(summary = "Sign-in methods", description = "The login page offers a passkey on the origins listed here.")
  @GetMapping
  public LoginMethods methods() {
    return new LoginMethods(passkeys.origins());
  }
}
