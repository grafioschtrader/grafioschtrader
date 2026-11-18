package grafiosch.security.passkey;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.stereotype.Component;

import grafiosch.common.PublicBaseUrl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasskeyJpaRepository;

/**
 * The WebAuthn relying party of this instance, derived from {@code g.public.base.url}. Passkeys are available when that
 * address is set and its host is a DNS name; an IP literal is no valid relying party id. The relying party id is that
 * host, the allowed origins are the origin of the address plus the entries of {@code g.webauthn.additional.origins}
 * whose host equals the relying party id - the browser refuses any other combination whatever the server allows.
 *
 * <p>
 * The constructor of {@link Webauthn4JRelyingPartyOperations} fixes relying party and origins, while the parameter is
 * read per request. The operations are therefore cached by the value of the address and rebuilt when it changes. Both
 * operations demand user verification, at registration and at sign-in, so a passkey is a complete sign-in by itself.
 * </p>
 */
@Component
public class PasskeyRelyingParty {
  private static final Logger log = LoggerFactory.getLogger(PasskeyRelyingParty.class);

  /** The relying party for one value of {@code g.public.base.url}. */
  public record Operations(String publicBaseUrl, String rpId, List<String> origins,
      Webauthn4JRelyingPartyOperations registration, Webauthn4JRelyingPartyOperations signIn) {
  }

  private final GlobalparametersJpaRepository parameters;
  private final PasskeyUserEntityRepository userEntities;
  private final UserCredentialRepository registrationCredentials;
  private final UserCredentialRepository signInCredentials;
  private final String displayName;
  private final List<String> additionalOrigins;
  private volatile Operations cached;

  public PasskeyRelyingParty(GlobalparametersJpaRepository parameters, PasskeyUserEntityRepository userEntities,
      UserPasskeyJpaRepository passkeys, UserJpaRepository users, @Value("${g.auth.display.name}") String displayName,
      @Value("${g.webauthn.additional.origins:}") String additionalOrigins) {
    this.parameters = parameters;
    this.userEntities = userEntities;
    this.registrationCredentials = new PasskeyCredentialRepository.Registration(passkeys, users);
    this.signInCredentials = new PasskeyCredentialRepository.SignIn(passkeys, users);
    this.displayName = displayName;
    this.additionalOrigins = Arrays.stream(additionalOrigins.split(",")).map(String::trim)
        .filter(origin -> !origin.isEmpty()).toList();
  }

  /**
   * The relying party for the current public address.
   *
   * @return the operations, or null when passkeys are not available on this instance
   */
  public Operations current() {
    PublicBaseUrl url = parameters.getPublicBaseUrl();
    if (url == null || !url.isDnsName()) {
      return null;
    }
    Operations operations = cached;
    if (operations == null || !operations.publicBaseUrl().equals(url.toString())) {
      operations = build(url);
      cached = operations;
    }
    return operations;
  }

  /** The page origins a passkey works on, empty when passkeys are not available. */
  public List<String> origins() {
    Operations operations = current();
    return operations == null ? List.of() : operations.origins();
  }

  private Operations build(PublicBaseUrl url) {
    String rpId = url.host();
    Set<String> origins = new LinkedHashSet<>();
    origins.add(url.origin());
    for (String origin : additionalOrigins) {
      String host = hostOf(origin);
      if (rpId.equalsIgnoreCase(host)) {
        origins.add(origin.replaceAll("/+$", ""));
      } else {
        log.warn("g.webauthn.additional.origins: {} is dropped, its host is not the relying party id {}", origin,
            rpId);
      }
    }
    PublicKeyCredentialRpEntity rp = PublicKeyCredentialRpEntity.builder().id(rpId).name(displayName).build();
    return new Operations(url.toString(), rpId, List.copyOf(origins), operations(rp, origins, registrationCredentials),
        operations(rp, origins, signInCredentials));
  }

  private Webauthn4JRelyingPartyOperations operations(PublicKeyCredentialRpEntity rp, Set<String> origins,
      UserCredentialRepository credentials) {
    Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(userEntities, credentials, rp,
        Set.copyOf(origins));
    // The library default PREFERRED would let a passkey without user verification be a complete sign-in
    operations.setCustomizeCreationOptions(options -> options.authenticatorSelection(AuthenticatorSelectionCriteria
        .builder().userVerification(UserVerificationRequirement.REQUIRED).residentKey(ResidentKeyRequirement.REQUIRED)
        .build()));
    operations.setCustomizeRequestOptions(options -> options.userVerification(UserVerificationRequirement.REQUIRED));
    return operations;
  }

  private static String hostOf(String origin) {
    try {
      return URI.create(origin).getHost();
    } catch (IllegalArgumentException _) {
      return null;
    }
  }
}
