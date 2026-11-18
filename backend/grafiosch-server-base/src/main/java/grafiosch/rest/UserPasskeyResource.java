package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.UserPasskeyDto;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.security.JwtTokenHandler;
import grafiosch.security.passkey.PasskeyJson;
import grafiosch.security.passkey.PasskeyService;
import grafiosch.service.CredentialChangeService;
import grafiosch.service.CredentialChangeService.PasskeyChange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The passkeys of the signed-in user. Adding one needs the password and, while an authenticator app is enabled, a code;
 * renaming and deleting need no proof, since deleting only removes access. Adding and deleting end the other sessions
 * of the user and return a replacement token in {@code x-auth-token}. Every access is restricted to the user's own
 * passkeys.
 */
@RestController
@RequestMapping(RequestMappings.USER_MAP + "/passkey")
@Tag(name = User.TABNAME, description = "Passkeys of the signed-in user")
public class UserPasskeyResource {
  private final PasskeyService passkeys;
  private final PasskeyJson json;
  private final CredentialChangeService credentials;
  private final JwtTokenHandler jwtTokenHandler;
  private final GlobalparametersJpaRepository parameters;

  public UserPasskeyResource(PasskeyService passkeys, PasskeyJson json, CredentialChangeService credentials,
      JwtTokenHandler jwtTokenHandler, GlobalparametersJpaRepository parameters) {
    this.passkeys = passkeys;
    this.json = json;
    this.credentials = credentials;
    this.jwtTokenHandler = jwtTokenHandler;
    this.parameters = parameters;
  }

  @Schema(description = "Proof for adding a passkey")
  public record PasskeyProof(@Schema(description = "Password of the user") String password,
      @Schema(description = "Code of the authenticator app or a recovery code, while one is enabled") String code) {
  }

  @Schema(description = "New name of a passkey")
  public record PasskeyLabel(@Schema(description = "At most 40 characters") String label) {
  }

  @Operation(summary = "List the passkeys", description = "Label, creation, last use and whether it was synchronized.")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public List<UserPasskeyDto> list() {
    return passkeys.list(currentUser().getIdUser());
  }

  @Operation(summary = "Start the registration of a passkey", description = """
      Needs the proof of a new sign-in method. Returns { challengeId, publicKey } with the
      PublicKeyCredentialCreationOptions for navigator.credentials.create; existing passkeys are excluded.""")
  @PostMapping(value = "/options", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<String> registrationOptions(@RequestBody PasskeyProof proof) {
    String options = passkeys.registrationOptions(currentUser().getIdUser(), proof.password(), proof.code());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
        .body(options);
  }

  @Operation(summary = "Complete the registration of a passkey", description = """
      Body { challengeId, label, credential } with the credential as PublicKeyCredential.toJSON() produces it.""")
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPasskeyDto> register(HttpServletRequest request) throws IOException {
    User user = currentUser();
    PasskeyChange change = passkeys.register(user.getIdUser(), json.readBody(request));
    return ResponseEntity.ok().header("x-auth-token", replacementToken(user, change.authVersion()))
        .body(UserPasskeyDto.of(change.passkey()));
  }

  @Operation(summary = "Rename a passkey")
  @PutMapping(value = "/{idUserPasskey}", produces = APPLICATION_JSON_VALUE)
  public UserPasskeyDto rename(@PathVariable Integer idUserPasskey, @RequestBody PasskeyLabel label) {
    Integer idUser = currentUser().getIdUser();
    passkeys.rename(idUser, idUserPasskey, label.label());
    return passkeys.find(idUser, idUserPasskey);
  }

  @Operation(summary = "Delete a passkey", description = "Ends the other sessions of the user and sends a notice.")
  @DeleteMapping(value = "/{idUserPasskey}")
  public ResponseEntity<Void> delete(@PathVariable Integer idUserPasskey) {
    User user = currentUser();
    int authVersion = credentials.deletePasskey(user.getIdUser(), idUserPasskey);
    return ResponseEntity.noContent().header("x-auth-token", replacementToken(user, authVersion)).build();
  }

  /** Issued after the commit from the caller's security-context user, so a switched tenant survives. */
  private String replacementToken(User user, int authVersion) {
    return jwtTokenHandler.createTokenForUser(user, parameters.getJWTExpirationMinutes(), null, authVersion);
  }

  private static User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
