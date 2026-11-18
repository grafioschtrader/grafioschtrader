package grafiosch.security.passkey;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.webauthn4j.util.exception.WebAuthnException;

import grafiosch.config.LimitKeyBaseConfig;
import grafiosch.dto.UserPasskeyDto;
import grafiosch.entities.User;
import grafiosch.entities.UserPasskey;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasskeyJpaRepository;
import grafiosch.rest.helper.RestErrorHandler;
import grafiosch.security.LoginCompletionService;
import grafiosch.security.passkey.PasskeyChallengeStore.Entry;
import grafiosch.security.passkey.PasskeyChallengeStore.Purpose;
import grafiosch.security.passkey.PasskeyRelyingParty.Operations;
import grafiosch.service.CredentialChangeService;
import grafiosch.service.CredentialChangeService.PasskeyChange;
import grafiosch.service.EntityLimitService;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafiosch.service.SecondFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * Registration, management and sign-in of passkeys on top of {@link PasskeyRelyingParty}. The challenge of every
 * ceremony is kept in {@link PasskeyChallengeStore}; the answer is checked against the stored entry only.
 *
 * <p>
 * A registration needs the proof of a new sign-in method (password, and a code while an authenticator app is enabled).
 * A sign-in needs nothing but the passkey, which must have been created with user verification and must verify the
 * user again; it then completes like a password sign-in, without the second factor, which a passkey replaces.
 * </p>
 */
@Service
public class PasskeyService {
  private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);

  private final PasskeyRelyingParty relyingParty;
  private final PasskeyChallengeStore challenges;
  private final PasskeyJson json;
  private final PasskeyOptionsLimiter limiter;
  private final UserPasskeyJpaRepository passkeys;
  private final UserJpaRepository users;
  private final CredentialChangeService credentials;
  private final SecondFactorService secondFactors;
  private final EntityLimitService entityLimits;
  private final LoginCompletionService completion;
  private final LoginAttemptServiceIpAddress attempts;
  private final MessageSource messages;

  public PasskeyService(PasskeyRelyingParty relyingParty, PasskeyChallengeStore challenges, PasskeyJson json,
      PasskeyOptionsLimiter limiter, UserPasskeyJpaRepository passkeys, UserJpaRepository users,
      CredentialChangeService credentials, SecondFactorService secondFactors, EntityLimitService entityLimits,
      LoginCompletionService completion, LoginAttemptServiceIpAddress attempts, MessageSource messages) {
    this.relyingParty = relyingParty;
    this.challenges = challenges;
    this.json = json;
    this.limiter = limiter;
    this.passkeys = passkeys;
    this.users = users;
    this.credentials = credentials;
    this.secondFactors = secondFactors;
    this.entityLimits = entityLimits;
    this.completion = completion;
    this.attempts = attempts;
    this.messages = messages;
  }

  /** The page origins on which the login page offers a passkey sign-in; empty when passkeys are not available. */
  public List<String> origins() {
    return relyingParty.origins();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Registration and management by the signed-in user
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Issues the options of a registration after the proof of a new sign-in method.
   *
   * @return the JSON answer {@code { challengeId, publicKey }}
   */
  public String registrationOptions(Integer idUser, String password, String code) {
    Operations operations = requireAvailable();
    User user = users.findById(idUser).orElseThrow();
    credentials.checkNotDemo(user);
    if (!entityLimits.fitsWithinLimit(user, LimitKeyBaseConfig.KEY_USER_PASSKEY, null, 1)) {
      throw new GeneralNotTranslatedWithArgumentsException("MAX_USER_PASSKEY", null);
    }
    secondFactors.requireProofForNewMethod(idUser, password, code);
    var authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
        user.getAuthorities());
    PublicKeyCredentialCreationOptions options = operations.registration()
        .createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));
    String challengeId = challenges.putRegistration(options, operations.publicBaseUrl(), idUser,
        user.getAuthVersion());
    if (challengeId == null) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    }
    return json.options(challengeId, options);
  }

  /**
   * Verifies and stores a new passkey. The challenge must be a registration challenge of this user, built for the
   * current public address, and no credential change may have happened since the proof.
   *
   * @param idUser the signed-in user
   * @param body   {@code { challengeId, label, credential }}
   */
  public PasskeyChange register(Integer idUser, JsonNode body) {
    Entry entry = challenges.take(text(body, "challengeId"));
    Operations operations = requireAvailable();
    if (entry == null || entry.purpose() != Purpose.REGISTRATION || !idUser.equals(entry.idUser())
        || !operations.publicBaseUrl().equals(entry.publicBaseUrl())
        || users.findById(idUser).orElseThrow().getAuthVersion() != entry.authVersion()) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.challenge.invalid", null);
    }
    String label = validLabel(text(body, "label"));
    RelyingPartyPublicKey publicKey = json.registration(body.get("credential"), label);
    return credentials.addPasskey(idUser, () -> verified(() -> operations.registration()
        .registerCredential(new ImmutableRelyingPartyRegistrationRequest(entry.creationOptions(), publicKey))));
  }

  public List<UserPasskeyDto> list(Integer idUser) {
    return passkeys.findByIdUserOrderByCreatedAt(idUser).stream().map(UserPasskeyDto::of).toList();
  }

  public void rename(Integer idUser, Integer idUserPasskey, String label) {
    if (passkeys.rename(idUserPasskey, idUser, validLabel(label)) != 1) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.not.found", null);
    }
  }

  public UserPasskeyDto find(Integer idUser, Integer idUserPasskey) {
    return passkeys.findById(idUserPasskey).filter(passkey -> passkey.getIdUser().equals(idUser))
        .map(UserPasskeyDto::of)
        .orElseThrow(() -> new GeneralNotTranslatedWithArgumentsException("passkey.not.found", null));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Public sign-in
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Issues the options of an anonymous sign-in; {@code allowCredentials} stays empty, so the browser offers the
   * discoverable passkeys of this site.
   *
   * @return the JSON answer, or null when the address used up its budget or the challenge store is full
   */
  public String signInOptions(HttpServletRequest request) {
    Operations operations = requireAvailable();
    if (!limiter.tryConsume(attempts.clientAddress(request))) {
      return null;
    }
    PublicKeyCredentialRequestOptions options = operations.signIn()
        .createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
    String challengeId = challenges.putSignIn(options, operations.publicBaseUrl());
    return challengeId == null ? null : json.options(challengeId, options);
  }

  /**
   * Verifies an assertion and completes the sign-in, writing token and configuration into the response. An unknown or
   * expired challenge is refused without counting; an assertion that fails verification counts toward the block of the
   * client address. The owner of the credential is read before the assertion is verified, and the sign-in completes
   * with exactly that snapshot.
   */
  public void signIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
    JsonNode body = json.readBody(request);
    Entry entry = challenges.take(text(body, "challengeId"));
    Operations operations = requireAvailable();
    if (entry == null || entry.purpose() != Purpose.SIGN_IN
        || !operations.publicBaseUrl().equals(entry.publicBaseUrl())) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.challenge.invalid", null);
    }
    User user = verifyAssertion(operations, entry, body.get("credential"));
    if (user == null) {
      attempts.loginFailed(request);
      writeFailure(response, "login.failure");
      return;
    }
    JsonNode offset = body.get("timezoneOffset");
    try {
      completion.complete(request, response, user, offset != null && offset.isNumber() ? offset.intValue() : null,
          true);
    } catch (RequestLimitAndSecurityBreachException e) {
      // Unlock request and administrator self-release live on the password sign-in
      Locale locale = user.createAndGetJavaLocale();
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.TOO_MANY_REQUESTS,
          new SingleNativeMsgError(messages.getMessage("passkey.signin.limit", null, locale)));
    }
  }

  /** The verified owner of the assertion, or null when it does not verify. */
  private User verifyAssertion(Operations operations, Entry entry, JsonNode credentialNode) {
    try {
      PublicKeyCredential<AuthenticatorAssertionResponse> credential = json.assertion(credentialNode);
      UserPasskey passkey = passkeys.findByCredentialId(credential.getRawId().getBytes()).orElse(null);
      if (passkey == null) {
        return null;
      }
      User user = users.findById(passkey.getIdUser()).orElse(null);
      if (user == null) {
        return null;
      }
      new AccountStatusUserDetailsChecker().check(user);
      byte[] handle = users.findPasskeyUserHandle(user.getIdUser());
      Bytes userHandle = credential.getResponse().getUserHandle();
      if (handle == null || userHandle != null && !Arrays.equals(handle, userHandle.getBytes())) {
        return null;
      }
      PublicKeyCredentialUserEntity owner = operations.signIn()
          .authenticate(new RelyingPartyAuthenticationRequest(entry.requestOptions(), credential));
      return Arrays.equals(owner.getId().getBytes(), handle) ? user : null;
    } catch (GeneralNotTranslatedWithArgumentsException | WebAuthnException | IllegalArgumentException
        | BadCredentialsException | AccountStatusException e) {
      log.debug("Passkey assertion refused", e);
      return null;
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  private Operations requireAvailable() {
    Operations operations = relyingParty.current();
    if (operations == null) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.not.available", null);
    }
    return operations;
  }

  /** Runs the verification of a new credential; a refused credential becomes a translated message. */
  private static CredentialRecord verified(Supplier<CredentialRecord> registration) {
    try {
      return registration.get();
    } catch (WebAuthnException | IllegalArgumentException e) {
      log.debug("Passkey registration refused", e);
      throw new GeneralNotTranslatedWithArgumentsException("passkey.registration.invalid", null);
    }
  }

  private static String validLabel(String label) {
    String trimmed = label == null ? "" : label.strip();
    if (trimmed.isEmpty() || trimmed.length() > UserPasskey.LABEL_MAX_LENGTH) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.label.invalid", null);
    }
    return trimmed;
  }

  private static String text(JsonNode body, String field) {
    JsonNode node = body == null ? null : body.get(field);
    return node == null || !node.isString() ? null : node.stringValue();
  }

  private static void writeFailure(HttpServletResponse response, String key) throws IOException {
    response.setContentType("text/plain");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.getWriter().append(key);
  }
}
