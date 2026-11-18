package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.entities.projection.SuccessfullyChanged;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.security.JwtTokenHandler;
import grafiosch.service.CredentialChangeService.RecoveryCodesChange;
import grafiosch.service.SecondFactorService;
import grafiosch.service.SecondFactorService.SecondFactorState;
import grafiosch.service.SecondFactorService.TotpSetup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The signed-in user's own authenticator-app second factor (TOTP) and its recovery codes. Every action that changes
 * the set of credentials ends the user's other sessions and returns a replacement token in {@code x-auth-token}, which
 * keeps a switched tenant. Answers carrying a secret or recovery codes are never cached.
 */
@RestController
@RequestMapping(RequestMappings.USER_MAP)
@Tag(name = User.TABNAME, description = "Second factor of the signed-in user")
public class UserTotpResource {
  private final SecondFactorService secondFactors;
  private final JwtTokenHandler jwtTokenHandler;
  private final GlobalparametersJpaRepository parameters;
  private final MessageSource messages;

  public UserTotpResource(SecondFactorService secondFactors, JwtTokenHandler jwtTokenHandler,
      GlobalparametersJpaRepository parameters, MessageSource messages) {
    this.secondFactors = secondFactors;
    this.jwtTokenHandler = jwtTokenHandler;
    this.parameters = parameters;
    this.messages = messages;
  }

  @Schema(description = "Proof of the user for managing the second factor")
  public record TotpProof(@Schema(description = "Password of the user") String password,
      @Schema(description = """
          Code of the authenticator app or a recovery code; required while a factor is enabled""") String code) {
  }

  @Schema(description = "A single code of the second factor")
  public record TotpCode(@Schema(description = "Six digits of the authenticator app") String code) {
  }

  @Schema(description = "Recovery codes in clear text; this answer is the only time they are readable")
  public record RecoveryCodes(@Schema(description = "Ten one-time codes in the form XXXXX-XXXXX") List<String> codes) {
  }

  @Operation(summary = "State of the second factor", description = """
      Whether an authenticator app is enabled and how many recovery codes are left.""")
  @GetMapping(value = "/secondfactor", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecondFactorState> getState() {
    return ResponseEntity.ok(secondFactors.state(currentUser().getIdUser()));
  }

  @Operation(summary = "Start the setup of an authenticator app", description = """
      Needs the password and, while a factor is enabled, a current code. Returns the new secret as text, as otpauth
      URI and as QR code. An enabled factor stays in force until the new secret is confirmed within 10 minutes.""")
  @PostMapping(value = "/totp/setup", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TotpSetup> startSetup(@RequestBody TotpProof proof) {
    TotpSetup setup = secondFactors.startSetup(currentUser().getIdUser(), proof.password(), proof.code());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup);
  }

  @Operation(summary = "Enable the authenticator app", description = """
      Confirms the pending setup with a code of the new secret. Returns ten new recovery codes once.""")
  @PostMapping(value = "/totp/enable", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<RecoveryCodes> enable(@RequestBody TotpCode code) {
    User user = currentUser();
    return withCodes(user, secondFactors.enable(user.getIdUser(), code.code()));
  }

  @Operation(summary = "Renew the recovery codes", description = """
      Needs a current code of the authenticator app. The previous recovery codes become invalid.""")
  @PostMapping(value = "/totp/recoverycodes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<RecoveryCodes> renewRecoveryCodes(@RequestBody TotpCode code) {
    User user = currentUser();
    return withCodes(user, secondFactors.renewRecoveryCodes(user.getIdUser(), code.code()));
  }

  @Operation(summary = "Disable the authenticator app", description = """
      Needs the password and a code of the authenticator app or a recovery code. Removes the factor and the recovery
      codes.""")
  @PostMapping(value = "/totp/disable", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SuccessfullyChanged> disable(@RequestBody TotpProof proof) {
    User user = currentUser();
    int authVersion = secondFactors.disable(user.getIdUser(), proof.password(), proof.code());
    return ResponseEntity.ok().header("x-auth-token", replacementToken(user, authVersion))
        .body(new SuccessfullyChanged(true,
            messages.getMessage("secondfactor.disabled.success", null, user.createAndGetJavaLocale())));
  }

  private ResponseEntity<RecoveryCodes> withCodes(User user, RecoveryCodesChange change) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header("x-auth-token", replacementToken(user, change.authVersion()))
        .body(new RecoveryCodes(change.recoveryCodes()));
  }

  /** Issued after the commit from the caller's security-context user, so a switched tenant survives. */
  private String replacementToken(User user, int authVersion) {
    return jwtTokenHandler.createTokenForUser(user, parameters.getJWTExpirationMinutes(), null, authVersion);
  }

  private static User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
