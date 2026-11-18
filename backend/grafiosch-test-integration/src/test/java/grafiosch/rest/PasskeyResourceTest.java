package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.UserJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Passkey registration and sign-in against the software authenticator of webauthn4j-test, which creates and signs real
 * credentials, through the HTTP security chain and MariaDB. The public address is set to the backend on localhost, so
 * the relying party id is {@code localhost} and the authenticator answers for the origin of that address.
 */
class PasskeyResourceTest extends BaseIntegrationTest {
  private static final String PASSWORD = "A123abcd";
  private static final String PUBLIC_BASE_URL = "http://localhost:8081";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();
  @Autowired
  private UserJpaRepository users;
  @Autowired
  private RoleJpaRepository roles;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private EntityManagerFactory emf;
  private String originalPublicUrl;
  private User user;
  private User other;
  private RestTestClient client;
  /** The software authenticator; it keeps the discoverable credentials it created. */
  private NoneAttestationAuthenticator model;
  private ClientPlatform authenticator;

  @BeforeEach
  void prepare() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    originalPublicUrl = jdbc.queryForObject(
        "SELECT property_string FROM globalparameters WHERE property_name = 'g.public.base.url'", String.class);
    setPublicBaseUrl(PUBLIC_BASE_URL);
    user = users.saveAndFlush(newUser("passkey"));
    client = restTestClient.mutate()
        .defaultHeader("X-Forwarded-For", "192.0.2." + (Math.floorMod(user.getIdUser(), 250) + 1)).build();
    model = new NoneAttestationAuthenticator();
    authenticator = new ClientPlatform(new Origin(PUBLIC_BASE_URL), new WebAuthnAuthenticatorAdaptor(model));
  }

  @AfterEach
  void restore() {
    setPublicBaseUrl(originalPublicUrl);
    for (User created : new User[] { user, other }) {
      if (created != null) {
        emf.getCache().evict(User.class);
        users.deleteById(created.getIdUser());
      }
    }
  }

  @Test
  @DisplayName("Registration and sign-in with user verification; the options demand it, the methods list the origins")
  void registerAndSignIn() throws Exception {
    client.get().uri("/api/login/methods").exchange().expectStatus().isOk().expectBody(String.class)
        .value(body -> assertThat(body).contains(PUBLIC_BASE_URL).contains("http://localhost:4201"));
    String jwt = jwtOf(user);
    JsonNode options = registrationOptions(jwt);
    assertThat(options.at("/publicKey/authenticatorSelection/userVerification").stringValue()).isEqualTo("required");
    assertThat(options.at("/publicKey/authenticatorSelection/residentKey").stringValue()).isEqualTo("required");
    assertThat(options.at("/publicKey/rp/id").stringValue()).isEqualTo("localhost");
    var registered = register(jwt, options, "Laptop").expectStatus().isOk().expectBody(String.class).returnResult();
    assertThat(registered.getResponseBody()).contains("\"label\":\"Laptop\"");
    // The registration ends previous sessions and replaces the caller's token
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(jwt)).isInstanceOf(BadCredentialsException.class);
    String replacement = registered.getResponseHeaders().getFirst("x-auth-token");
    assertThat(replacement).isNotBlank();
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

    JsonNode signInOptions = signInOptions();
    assertThat(signInOptions.at("/publicKey/userVerification").stringValue()).isEqualTo("required");
    assertThat(signInOptions.at("/publicKey/allowCredentials").isEmpty()).isTrue();
    var signedIn = signIn(signInOptions.get("challengeId").stringValue(), assertion(signInOptions, authenticator))
        .expectStatus().isOk().expectBody(String.class).returnResult();
    String token = signedIn.getResponseHeaders().getFirst("x-auth-token");
    assertThat(((User) jwtTokenHandler.parseUserFromToken(token).orElseThrow()).getIdUser())
        .isEqualTo(user.getIdUser());
    assertThat(jdbc.queryForObject("SELECT last_used_at IS NOT NULL FROM user_passkey WHERE id_user = ?",
        Boolean.class, user.getIdUser())).isTrue();
    own(token).get().uri("/api/user/secondfactor").exchange().expectStatus().isOk().expectBody(String.class)
        .value(body -> assertThat(body).contains("\"passkeyCount\":1"));
  }

  @Test
  @DisplayName("Refused: another origin, no user verification, a reused challenge and a challenge of the other purpose")
  void refusedAssertions() throws Exception {
    String jwt = jwtOf(user);
    register(jwt, registrationOptions(jwt), "Key").expectStatus().isOk();

    JsonNode wrongOrigin = signInOptions();
    ClientPlatform elsewhere = new ClientPlatform(new Origin("https://evil.example.org"),
        authenticator.getAuthenticatorAdaptor());
    signIn(wrongOrigin.get("challengeId").stringValue(), assertion(wrongOrigin, elsewhere)).expectStatus()
        .isUnauthorized();

    JsonNode noUv = signInOptions();
    ObjectNodeAssertion withoutUv = assertionWithoutUserVerification(noUv);
    assertThat(withoutUv.userVerified()).isFalse();
    signIn(noUv.get("challengeId").stringValue(), withoutUv.json()).expectStatus().isUnauthorized();

    JsonNode once = signInOptions();
    Map<String, Object> credential = assertion(once, authenticator);
    signIn(once.get("challengeId").stringValue(), credential).expectStatus().isOk();
    signIn(once.get("challengeId").stringValue(), credential).expectStatus().isBadRequest();

    // A registration challenge cannot sign in, and a sign-in challenge cannot register
    String fresh = jwtOf(users.findById(user.getIdUser()).orElseThrow());
    JsonNode registration = registrationOptions(fresh);
    signIn(registration.get("challengeId").stringValue(), assertion(signInOptions(), authenticator)).expectStatus()
        .isBadRequest();
    JsonNode signInChallenge = signInOptions();
    Map<String, Object> body = new HashMap<>(Map.of("challengeId", signInChallenge.get("challengeId").stringValue(),
        "label", "Swap", "credential", attestation(registration, authenticator)));
    own(fresh).post().uri("/api/user/passkey").contentType(MediaType.APPLICATION_JSON).body(body).exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Refused: a challenge of another user, a counter that goes backwards, a passkey deleted meanwhile")
  void counterAndOwnership() throws Exception {
    other = users.saveAndFlush(newUser("passkey-other"));
    JsonNode otherChallenge = registrationOptions(jwtOf(other));
    String jwt = jwtOf(user);
    // A separate authenticator: the refused credential stays in the one that created it, and the software
    // authenticator signs a discoverable sign-in with whichever credential its hash map yields first
    ClientPlatform thief = new ClientPlatform(new Origin(PUBLIC_BASE_URL),
        new WebAuthnAuthenticatorAdaptor(new NoneAttestationAuthenticator()));
    registerWith(jwt, otherChallenge, "Stolen", thief).expectStatus().isBadRequest();

    register(jwt, registrationOptions(jwt), "Counting").expectStatus().isOk();
    // Two assertions of a counting authenticator; the later one is presented first
    model.setCountUpEnabled(true);
    JsonNode first = signInOptions();
    Map<String, Object> earlier = assertion(first, authenticator);
    JsonNode second = signInOptions();
    Map<String, Object> later = assertion(second, authenticator);
    signIn(second.get("challengeId").stringValue(), later).expectStatus().isOk();
    signIn(first.get("challengeId").stringValue(), earlier).expectStatus().isUnauthorized();

    String current = jwtOf(users.findById(user.getIdUser()).orElseThrow());
    JsonNode beforeDelete = signInOptions();
    Map<String, Object> pending = assertion(beforeDelete, authenticator);
    Integer idUserPasskey = jdbc.queryForObject("SELECT id_user_passkey FROM user_passkey WHERE id_user = ?",
        Integer.class, user.getIdUser());
    own(current).delete().uri("/api/user/passkey/" + idUserPasskey).exchange().expectStatus().isNoContent();
    signIn(beforeDelete.get("challengeId").stringValue(), pending).expectStatus().isUnauthorized();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passkey WHERE id_user = ?", Integer.class,
        user.getIdUser())).isZero();
    // The user handle stays after the last passkey was deleted
    assertThat(users.findPasskeyUserHandle(user.getIdUser())).hasSize(32);
  }

  @Test
  @DisplayName("Two parallel registrations at the limit of ten passkeys leave ten")
  void parallelRegistrationsStayWithinLimit() throws Exception {
    for (int i = 0; i < 9; i++) {
      String jwt = jwtOf(users.findById(user.getIdUser()).orElseThrow());
      register(jwt, registrationOptions(jwt), "Key " + i).expectStatus().isOk();
    }
    String jwt = jwtOf(users.findById(user.getIdUser()).orElseThrow());
    JsonNode a = registrationOptions(jwt);
    JsonNode b = registrationOptions(jwt);
    ClientPlatform second = new ClientPlatform(new Origin(PUBLIC_BASE_URL),
        new WebAuthnAuthenticatorAdaptor(new NoneAttestationAuthenticator()));
    var statuses = parallel(() -> status(register(jwt, a, "Tenth A")),
        () -> status(registerWith(jwt, b, "Tenth B", second)));
    assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passkey WHERE id_user = ?", Integer.class,
        user.getIdUser())).isEqualTo(10);
  }

  @Test
  @DisplayName("Registration needs the password; an unavailable relying party offers no passkey")
  void proofAndAvailability() {
    String jwt = jwtOf(user);
    own(jwt).post().uri("/api/user/passkey/options").body(Map.of("password", "wrong")).exchange().expectStatus()
        .isBadRequest();
    setPublicBaseUrl("none");
    client.get().uri("/api/login/methods").exchange().expectStatus().isOk().expectBody(String.class)
        .isEqualTo("{\"passkeyOrigins\":[]}");
    client.post().uri("/api/login/passkey/options").exchange().expectStatus().isBadRequest();
  }

  // ---------------------------------------------------------------------------------------------------------------

  /** An assertion and whether the authenticator data carries the UV flag. */
  record ObjectNodeAssertion(Map<String, Object> json, boolean userVerified) {
  }

  private JsonNode registrationOptions(String jwt) {
    String body = own(jwt).post().uri("/api/user/passkey/options").body(Map.of("password", PASSWORD)).exchange()
        .expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody(String.class)
        .returnResult().getResponseBody();
    return JSON.readTree(body);
  }

  private RestTestClient.ResponseSpec register(String jwt, JsonNode options, String label) {
    return registerWith(jwt, options, label, authenticator);
  }

  private RestTestClient.ResponseSpec registerWith(String jwt, JsonNode options, String label,
      ClientPlatform platform) {
    Map<String, Object> body = Map.of("challengeId", options.get("challengeId").stringValue(), "label", label,
        "credential", attestation(options, platform));
    return own(jwt).post().uri("/api/user/passkey").contentType(MediaType.APPLICATION_JSON).body(body).exchange();
  }

  private JsonNode signInOptions() {
    String body = client.post().uri("/api/login/passkey/options").exchange().expectStatus().isOk()
        .expectBody(String.class).returnResult().getResponseBody();
    return JSON.readTree(body);
  }

  private RestTestClient.ResponseSpec signIn(String challengeId, Map<String, Object> credential) {
    return client.post().uri("/api/login/passkey").contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("challengeId", challengeId, "credential", credential, "timezoneOffset", 0)).exchange();
  }

  /** The new credential as the browser's PublicKeyCredential.toJSON() writes it. */
  private static Map<String, Object> attestation(JsonNode options, ClientPlatform platform) {
    JsonNode pk = options.get("publicKey");
    var rp = new PublicKeyCredentialRpEntity(pk.at("/rp/id").stringValue(), pk.at("/rp/name").stringValue());
    var userEntity = new PublicKeyCredentialUserEntity(B64D.decode(pk.at("/user/id").stringValue()),
        pk.at("/user/name").stringValue(), pk.at("/user/displayName").stringValue());
    var selection = new AuthenticatorSelectionCriteria(null, true, ResidentKeyRequirement.REQUIRED,
        UserVerificationRequirement.REQUIRED);
    var creation = new PublicKeyCredentialCreationOptions(rp, userEntity,
        new DefaultChallenge(B64D.decode(pk.get("challenge").stringValue())),
        List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
        300_000L, List.of(), selection, AttestationConveyancePreference.NONE, null);
    PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = platform.create(creation);
    String id = B64.encodeToString(credential.getRawId());
    return Map.of("id", id, "rawId", id, "type", "public-key", "clientExtensionResults", Map.of(), "response",
        Map.of("clientDataJSON", B64.encodeToString(credential.getResponse().getClientDataJSON()),
            "attestationObject", B64.encodeToString(credential.getResponse().getAttestationObject()), "transports",
            List.of("internal")));
  }

  private static Map<String, Object> assertion(JsonNode options, ClientPlatform platform) {
    return assertionWith(options, platform, UserVerificationRequirement.REQUIRED).json();
  }

  private ObjectNodeAssertion assertionWithoutUserVerification(JsonNode options) {
    return assertionWith(options, authenticator, UserVerificationRequirement.DISCOURAGED);
  }

  private static ObjectNodeAssertion assertionWith(JsonNode options, ClientPlatform platform,
      UserVerificationRequirement userVerification) {
    JsonNode pk = options.get("publicKey");
    var request = new PublicKeyCredentialRequestOptions(
        new DefaultChallenge(B64D.decode(pk.get("challenge").stringValue())), 300_000L, pk.get("rpId").stringValue(),
        List.of(), userVerification, null);
    PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = platform.get(request);
    AuthenticatorAssertionResponse response = credential.getResponse();
    String id = B64.encodeToString(credential.getRawId());
    Map<String, Object> responseJson = new HashMap<>(Map.of("clientDataJSON",
        B64.encodeToString(response.getClientDataJSON()), "authenticatorData",
        B64.encodeToString(response.getAuthenticatorData()), "signature",
        B64.encodeToString(response.getSignature())));
    if (response.getUserHandle() != null) {
      responseJson.put("userHandle", B64.encodeToString(response.getUserHandle()));
    }
    // Byte 32 of the authenticator data holds the flags; 0x04 is "user verified"
    boolean userVerified = (response.getAuthenticatorData()[32] & 0x04) != 0;
    return new ObjectNodeAssertion(Map.of("id", id, "rawId", id, "type", "public-key", "clientExtensionResults",
        Map.of(), "response", responseJson), userVerified);
  }

  private List<Integer> parallel(Callable<Integer> first, Callable<Integer> second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var jobs = List.of(first, second).stream().map(job -> executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return job.call();
      })).toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(jobs.get(0).get(30, TimeUnit.SECONDS), jobs.get(1).get(30, TimeUnit.SECONDS));
    }
  }

  private static int status(RestTestClient.ResponseSpec spec) {
    return spec.expectBody().returnResult().getStatus().value();
  }

  private String jwtOf(User account) {
    return jwtTokenHandler.createTokenForUser(account, 60);
  }

  private RestTestClient own(String jwt) {
    return client.mutate().defaultHeader("x-auth-token", jwt).build();
  }

  private User newUser(String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 12);
    User created = new User(prefix + "-" + suffix + "@test.local", new BCryptPasswordEncoder().encode(PASSWORD),
        prefix + "-" + suffix, "en", 0);
    created.setEnabled(true);
    created.setRoles(List.of(roles.findByRolename(Role.ROLE_USER)));
    return created;
  }

  private void setPublicBaseUrl(String value) {
    jdbc.update("UPDATE globalparameters SET property_string = ? WHERE property_name = 'g.public.base.url'", value);
    emf.getCache().evict(Globalparameters.class);
  }

}
