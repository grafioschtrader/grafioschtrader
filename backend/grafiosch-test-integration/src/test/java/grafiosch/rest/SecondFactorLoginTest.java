package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.icegreen.greenmail.util.GreenMailUtil;

import grafiosch.entities.Role;
import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.entities.UserPasswordReset;
import grafiosch.integration.entities.Tenant;
import grafiosch.integration.repository.TenantJpaRepository;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasswordResetJpaRepository;
import grafiosch.security.totp.TotpGenerator;
import grafiosch.service.CredentialChangeService;
import grafiosch.service.SecondFactorService.TotpSetup;
import grafiosch.types.TenantAccessLevel;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * The authenticator-app second factor against the real HTTP security chain and MariaDB: the sign-in step, spending of
 * codes under overlapping requests, the per-user lock, enrolment and its interaction with the password reset. Every
 * test creates its own account and client address.
 *
 * <p>
 * Codes are computed here from the secret the setup returned. The enrolment spends the code of the previous time step,
 * which leaves the current and the next step for two later TOTP sign-ins without waiting.
 * </p>
 */
class SecondFactorLoginTest extends BaseIntegrationTest {
  private static final String PASSWORD = "A123abcd";
  private static final String NEW_PASSWORD = "B456defg";
  private static final ObjectMapper JSON = new ObjectMapper();
  @Autowired
  private UserJpaRepository users;
  @Autowired
  private RoleJpaRepository roles;
  @Autowired
  private UserPasswordResetJpaRepository resets;
  @Autowired
  private CredentialChangeService credentials;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private TenantAccessJpaRepository grants;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private EntityManagerFactory emf;
  private User user;
  private User admin;
  private Integer home;
  private Integer other;
  private RestTestClient client;
  /** Raw secret of the enabled factor and the step its enrolment spent. */
  private byte[] secret;
  private long enrolStep;
  private List<String> recoveryCodes;

  /** Body of a second-factor refusal. */
  record SecondFactorRefusal(String className, Detail error) {
    record Detail(String message, boolean invalidCode, boolean locked) {
    }
  }

  record RecoveryCodes(List<String> codes) {
  }

  @BeforeEach
  void createAccount() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    user = users.saveAndFlush(newUser("totp", Role.ROLE_USER));
    client = restTestClient.mutate()
        .defaultHeader("X-Forwarded-For", "203.0.113." + (Math.floorMod(user.getIdUser(), 250) + 1)).build();
  }

  @AfterEach
  void removeAccount() {
    for (User created : new User[] { user, admin }) {
      if (created != null) {
        grants.findByIdUser(created.getIdUser()).forEach(grants::delete);
        jdbc.update("UPDATE user SET id_tenant = NULL WHERE id_user = ?", created.getIdUser());
        emf.getCache().evict(User.class);
        users.deleteById(created.getIdUser());
      }
    }
    if (other != null)
      tenants.deleteById(other);
    if (home != null)
      tenants.deleteById(home);
  }

  @Test
  @DisplayName("Without code no token and no count; a valid code signs in once; overlapping sign-ins stay separate")
  void signInWithCode() throws Exception {
    enrol(login(PASSWORD, null));
    for (int i = 0; i < 6; i++) {
      SecondFactorRefusal refusal = refused(PASSWORD, null);
      assertThat(refusal.className()).isEqualTo("SecondFactorRequiredError");
      assertThat(refusal.error().invalidCode()).isFalse();
      assertThat(refusal.error().locked()).isFalse();
    }
    // One request with the code, one without: neither sees the other's code
    var statuses = parallel(() -> status(PASSWORD, code(enrolStep + 1)), () -> status(PASSWORD, null));
    assertThat(statuses).containsExactlyInAnyOrder(200, 401);
    SecondFactorRefusal again = refused(PASSWORD, code(enrolStep + 1));
    assertThat(again.error().invalidCode()).isTrue();
    // An earlier step than the last accepted one is spent as well
    assertThat(refused(PASSWORD, code(enrolStep)).error().invalidCode()).isTrue();
  }

  @Test
  @DisplayName("Two parallel sign-ins with one code give one success")
  void parallelCode() throws Exception {
    enrol(login(PASSWORD, null));
    String code = code(enrolStep + 1);
    assertThat(parallel(() -> status(PASSWORD, code), () -> status(PASSWORD, code))).containsExactlyInAnyOrder(200,
        401);
  }

  @Test
  @DisplayName("A recovery code works once and the notice names the number left")
  void recoveryCodeOnce() throws Exception {
    enrol(login(PASSWORD, null));
    // The notice of the enrolment is sent asynchronously; it must have arrived before the mailbox is emptied
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    greenMail.purgeEmailFromAllMailboxes();
    String recovery = recoveryCodes.get(3).toLowerCase().replace("-", " ");
    assertThat(login(PASSWORD, recovery)).isNotBlank();
    assertThat(refused(PASSWORD, recovery).error().invalidCode()).isTrue();
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    assertThat(GreenMailUtil.getBody(greenMail.getReceivedMessages()[0])).contains("9 recovery codes left");
  }

  @Test
  @DisplayName("Five wrong codes lock the factor of the user, not the client address")
  void wrongCodesLock() throws Exception {
    enrol(login(PASSWORD, null));
    for (int i = 0; i < 5; i++) {
      assertThat(refused(PASSWORD, i % 2 == 0 ? "1234567" : "AAAAA-AAAAA").error().invalidCode()).isTrue();
    }
    SecondFactorRefusal locked = refused(PASSWORD, code(enrolStep + 1));
    assertThat(locked.error().locked()).isTrue();
    // The address is not blocked: a second account signs in from the same address
    User second = users.saveAndFlush(newUser("totp-second", Role.ROLE_USER));
    try {
      client.post().uri("/api/login").body(loginBody(second, PASSWORD, null)).exchange().expectStatus().isOk();
    } finally {
      users.deleteById(second.getIdUser());
    }
    // A management endpoint refuses proofs of the locked user as well
    own(jwtTokenHandler.createTokenForUser(users.findById(user.getIdUser()).orElseThrow(), 60)).post()
        .uri("/api/user/totp/disable").body(Map.of("password", PASSWORD, "code", recoveryCodes.get(0))).exchange()
        .expectStatus().isBadRequest();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE id_user = ? AND used_at IS NULL",
        Integer.class, user.getIdUser())).isEqualTo(10);
  }

  @Test
  @DisplayName("A second setup leaves the enabled factor in force; an expired setup cannot be enabled")
  void setupReplacementAndExpiry() throws Exception {
    String jwt = enrol(login(PASSWORD, null));
    // A new setup needs a current code while the factor is enabled
    own(jwt).post().uri("/api/user/totp/setup").body(Map.of("password", PASSWORD)).exchange().expectStatus()
        .isBadRequest();
    TotpSetup second = own(jwt).post().uri("/api/user/totp/setup")
        .body(Map.of("password", PASSWORD, "code", code(enrolStep + 1))).exchange().expectStatus().isOk().expectBody(
            TotpSetup.class)
        .returnResult().getResponseBody();
    assertThat(second.secretBase32()).isNotEqualTo(TotpGenerator.base32Encode(secret));
    // The old secret still guards the sign-in
    assertThat(login(PASSWORD, recoveryCodes.get(0))).isNotBlank();
    jdbc.update("UPDATE user_totp SET pending_created_at = ? WHERE id_user = ?",
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11), user.getIdUser());
    byte[] pending = TotpGenerator.base32Decode(second.secretBase32());
    own(jwt).post().uri("/api/user/totp/enable")
        .body(Map.of("code", TotpGenerator.code(pending, new TotpGenerator().currentStep()))).exchange().expectStatus()
        .isBadRequest();
    assertThat(jdbc.queryForObject("SELECT pending_secret IS NULL AND enabled = 1 FROM user_totp WHERE id_user = ?",
        Boolean.class, user.getIdUser())).isTrue();
  }

  @Test
  @DisplayName("Enabling ends previous JWTs, keeps a switched tenant, voids a reset proof and notifies after commit")
  void enrolmentReplacesToken() throws Exception {
    home = createTenant("TOTP home");
    other = createTenant("TOTP other");
    user.setIdTenant(home);
    user = users.saveAndFlush(user);
    grants.save(new TenantAccess(user.getIdUser(), other, TenantAccessLevel.READ));
    resets.saveAndFlush(new UserPasswordReset(user.getIdUser(), UserAccessToken.hashToken("pending-proof"),
        LocalDateTime.now(ZoneOffset.UTC)));
    String old = jwtTokenHandler.createTokenForUser(user, 60, other);
    String replacement = enrol(old);
    User parsed = (User) jwtTokenHandler.parseUserFromToken(replacement).orElseThrow();
    assertThat(parsed.getIdTenant()).isEqualTo(other);
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(old)).isInstanceOf(BadCredentialsException.class);
    assertThat(resets.findById(user.getIdUser())).isEmpty();
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    String notice = GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]);
    assertThat(notice).contains("Authenticator app (TOTP) enabled").doesNotContain(TotpGenerator.base32Encode(secret));
    own(replacement).get().uri("/api/user/secondfactor").exchange().expectStatus().isOk().expectBody(String.class)
        .value(body -> assertThat(body).contains("\"totpEnabled\":true").contains("\"recoveryCodesLeft\":10"));
  }

  @Test
  @DisplayName("A reset by mail leaves TOTP and recovery codes as they are")
  void resetKeepsTotp() throws Exception {
    enrol(login(PASSWORD, null));
    resets.saveAndFlush(new UserPasswordReset(user.getIdUser(), UserAccessToken.hashToken("reset-proof"),
        LocalDateTime.now(ZoneOffset.UTC)));
    assertThat(credentials.resetPassword("reset-proof", NEW_PASSWORD)).isNotNull();
    assertThat(refused(NEW_PASSWORD, null).className()).isEqualTo("SecondFactorRequiredError");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE id_user = ? AND used_at IS NULL",
        Integer.class, user.getIdUser())).isEqualTo(10);
    assertThat(login(NEW_PASSWORD, code(enrolStep + 1))).isNotBlank();
  }

  @Test
  @DisplayName("Renewing recovery codes needs an app code; disabling needs password and code and ends sessions")
  void renewAndDisable() throws Exception {
    String jwt = enrol(login(PASSWORD, null));
    own(jwt).post().uri("/api/user/totp/recoverycodes").body(Map.of("code", recoveryCodes.get(0))).exchange()
        .expectStatus().isBadRequest();
    var renewed = own(jwt).post().uri("/api/user/totp/recoverycodes").body(Map.of("code", code(enrolStep + 1)))
        .exchange().expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody(
            RecoveryCodes.class)
        .returnResult();
    String afterRenew = renewed.getResponseHeaders().getFirst("x-auth-token");
    assertThat(renewed.getResponseBody().codes()).hasSize(10).doesNotContainAnyElementsOf(recoveryCodes);
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(jwt)).isInstanceOf(BadCredentialsException.class);
    own(afterRenew).post().uri("/api/user/totp/disable").body(Map.of("password", "wrong-password", "code",
        renewed.getResponseBody().codes().get(0))).exchange().expectStatus().isBadRequest();
    own(afterRenew).post().uri("/api/user/totp/disable")
        .body(Map.of("password", PASSWORD, "code", renewed.getResponseBody().codes().get(0))).exchange()
        .expectStatus().isOk();
    assertThat(login(PASSWORD, null)).isNotBlank();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE id_user = ?", Integer.class,
        user.getIdUser())).isZero();
  }

  @Test
  @DisplayName("An administrator removes another user's factor without receiving a token, never the own one")
  void administratorRemoval() throws Exception {
    String userJwt = enrol(login(PASSWORD, null));
    admin = users.saveAndFlush(newUser("totp-admin", Role.ROLE_ADMIN));
    RestTestClient adminClient = own(jwtTokenHandler.createTokenForUser(admin, 60));
    adminClient.delete().uri("/api/useradmin/" + admin.getIdUser() + "/secondfactor").exchange().expectStatus()
        .isBadRequest();
    adminClient.delete().uri("/api/useradmin/" + user.getIdUser() + "/secondfactor").exchange().expectStatus().isOk()
        .expectHeader().doesNotExist("x-auth-token");
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(userJwt)).isInstanceOf(BadCredentialsException.class);
    assertThat(login(PASSWORD, null)).isNotBlank();
    own(jwtTokenHandler.createTokenForUser(users.findById(user.getIdUser()).orElseThrow(), 60)).delete()
        .uri("/api/useradmin/" + admin.getIdUser() + "/secondfactor").exchange().expectStatus().isForbidden();
  }

  /** Sets up and enables the factor; returns the replacement token. */
  private String enrol(String jwt) throws Exception {
    TotpSetup setup = own(jwt).post().uri("/api/user/totp/setup").body(Map.of("password", PASSWORD)).exchange()
        .expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody(TotpSetup.class)
        .returnResult().getResponseBody();
    assertThat(setup.otpauthUri()).startsWith("otpauth://totp/Grafiosch:").contains("issuer=Grafiosch");
    assertThat(setup.qrCodePng()).isNotBlank();
    secret = TotpGenerator.base32Decode(setup.secretBase32());
    awaitFreshStep();
    enrolStep = new TotpGenerator().currentStep() - 1;
    var result = own(jwt).post().uri("/api/user/totp/enable").body(Map.of("code", code(enrolStep))).exchange()
        .expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody(RecoveryCodes.class)
        .returnResult();
    recoveryCodes = result.getResponseBody().codes();
    assertThat(recoveryCodes).hasSize(10).allMatch(code -> code.matches("[A-Z2-7]{5}-[A-Z2-7]{5}"));
    return result.getResponseHeaders().getFirst("x-auth-token");
  }

  /** Leaves enough of the current step that the codes of this test stay inside the accepted window. */
  private static void awaitFreshStep() throws InterruptedException {
    long intoStep = Instant.now().getEpochSecond() % TotpGenerator.STEP_SECONDS;
    if (intoStep > TotpGenerator.STEP_SECONDS - 10) {
      Thread.sleep((TotpGenerator.STEP_SECONDS - intoStep + 1) * 1000);
    }
  }

  private String code(long step) {
    return TotpGenerator.code(secret, step);
  }

  private User newUser(String prefix, String role) {
    String suffix = UUID.randomUUID().toString().substring(0, 12);
    User created = new User(prefix + "-" + suffix + "@test.local", new BCryptPasswordEncoder().encode(PASSWORD),
        prefix + "-" + suffix, "en", 0);
    created.setEnabled(true);
    created.setRoles(List.of(roles.findByRolename(role)));
    return created;
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
      return List.of(jobs.get(0).get(20, TimeUnit.SECONDS), jobs.get(1).get(20, TimeUnit.SECONDS));
    }
  }

  private Map<String, Object> loginBody(User account, String password, String code) {
    Map<String, Object> body = new HashMap<>(
        Map.of("email", account.getUsername(), "password", password, "timezoneOffset", 0));
    if (code != null) {
      body.put("secondFactorCode", code);
    }
    return body;
  }

  private int status(String password, String code) {
    return client.post().uri("/api/login").body(loginBody(user, password, code)).exchange().expectBody().returnResult()
        .getStatus().value();
  }

  private String login(String password, String code) {
    return client.post().uri("/api/login").body(loginBody(user, password, code)).exchange().expectStatus().isOk()
        .expectBody().returnResult().getResponseHeaders().getFirst("x-auth-token");
  }

  private SecondFactorRefusal refused(String password, String code) {
    var result = client.post().uri("/api/login").body(loginBody(user, password, code)).exchange().expectStatus()
        .isUnauthorized().expectHeader().doesNotExist("x-auth-token").expectBody(String.class).returnResult();
    return JSON.readValue(result.getResponseBody(), SecondFactorRefusal.class);
  }

  private RestTestClient own(String jwt) {
    return client.mutate().defaultHeader("x-auth-token", jwt).build();
  }

  private Integer createTenant(String name) {
    Tenant tenant = new Tenant();
    tenant.setTenantName(name);
    tenant.setCreateIdUser(user.getIdUser());
    return tenants.saveAndFlush(tenant).getIdTenant();
  }
}
