package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.icegreen.greenmail.util.GreenMailUtil;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.OAuthAuthorization;
import grafiosch.entities.Role;
import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.entities.UserPasswordReset;
import grafiosch.integration.entities.Tenant;
import grafiosch.integration.repository.TenantJpaRepository;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasswordResetJpaRepository;
import grafiosch.service.CredentialChangeService;
import grafiosch.types.AccessTokenScope;
import grafiosch.types.TenantAccessLevel;
import jakarta.persistence.EntityManagerFactory;

/** Real MariaDB transactions and HTTP security chain, with a separate account per test. */
@TestPropertySource(properties = "g.demo.account.pattern.en=^reset-demo@test[.]local$")
class PasswordResetResourceTest extends BaseIntegrationTest {
  private static final String PASSWORD = "A123abcd";
  private static final String NEW_PASSWORD = "B456defg";
  private static final String RESET = "/api/user/passwordreset";
  @Autowired
  private UserJpaRepository users;
  @Autowired
  private RoleJpaRepository roles;
  @Autowired
  private UserPasswordResetJpaRepository resets;
  @Autowired
  private UserAccessTokenJpaRepository tokens;
  @Autowired
  private OAuthAuthorizationJpaRepository authorizations;
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
  @Autowired
  private PlatformTransactionManager transactionManager;
  private User user;
  private String originalPublicUrl;
  private Integer home;
  private Integer other;
  private RestTestClient client;

  @BeforeEach
  void createAccount() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    String suffix = UUID.randomUUID().toString().substring(0, 12);
    user = new User("reset-" + suffix + "@test.local", new BCryptPasswordEncoder().encode(PASSWORD), "reset-" + suffix,
        "en", 0);
    user.setEnabled(true);
    user.setRoles(List.of(roles.findByRolename(Role.ROLE_USER)));
    user = users.saveAndFlush(user);
    originalPublicUrl = jdbc.queryForObject(
        "SELECT property_string FROM globalparameters WHERE property_name = 'g.public.base.url'", String.class);
    client = restTestClient.mutate()
        .defaultHeader("X-Forwarded-For", "198.51.100." + (Math.floorMod(user.getIdUser(), 250) + 1)).build();
  }

  @AfterEach
  void removeAccount() {
    jdbc.update("UPDATE globalparameters SET property_string = ? WHERE property_name = 'g.public.base.url'",
        originalPublicUrl);
    emf.getCache().evict(Globalparameters.class);
    if (user != null) {
      grants.findByIdUser(user.getIdUser()).forEach(grants::delete);
      jdbc.update("UPDATE user SET id_tenant = NULL WHERE id_user = ?", user.getIdUser());
      emf.getCache().evict(User.class);
      users.deleteById(user.getIdUser());
    }
    if (other != null)
      tenants.deleteById(other);
    if (home != null)
      tenants.deleteById(home);
  }

  @Test
  @DisplayName("Mailbox reset consumes one proof, revokes bearer credentials and rejects previous passwords and JWTs")
  void resetRevokesSessionsAndTokens() throws Exception {
    String oldJwt = login(PASSWORD);
    addBearerCredentials();
    jdbc.update(
        "UPDATE globalparameters SET property_string = 'https://example.org/gt' WHERE property_name = 'g.public.base.url'");
    emf.getCache().evict(Globalparameters.class);
    requestReset(user.getUsername());
    String token = mailedToken();
    String mail = GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]);
    assertThat(mail).contains("https://example.org/gt/passwordreset#token=" + token).doesNotContain("evil.test");
    assertThat(jdbc.queryForObject("SELECT token_hash FROM user_password_reset WHERE id_user = ?", String.class,
        user.getIdUser())).isEqualTo(UserAccessToken.hashToken(token));
    confirm(token, NEW_PASSWORD).expectStatus().isOk();
    assertThat(resets.findById(user.getIdUser())).isEmpty();
    assertThat(tokens.findByIdUserAndKind(user.getIdUser(), (byte) 1)).isEmpty();
    assertThat(authorizations.findByIdUserOrderByCreatedAtDesc(user.getIdUser())).isEmpty();
    own(oldJwt).get().uri("/api/user/own").exchange().expectStatus().is4xxClientError();
    client.post().uri("/api/login").body(loginBody(PASSWORD)).exchange().expectStatus().isUnauthorized();
    assertThat(login(NEW_PASSWORD)).isNotBlank();
    confirm(token, PASSWORD).expectStatus().is4xxClientError();
    assertThat(greenMail.waitForIncomingEmail(5000, 2)).isTrue();
    assertThat(GreenMailUtil.getBody(greenMail.getReceivedMessages()[1])).doesNotContain(token);
  }

  @Test
  @DisplayName("Quiet period and policy rejection preserve the first token; ineligible addresses get the same reply")
  void requestPrivacyAndPolicy() throws Exception {
    String answer = requestReset(user.getUsername());
    String token = mailedToken();
    assertThat(requestReset(user.getUsername())).isEqualTo(answer);
    assertThat(requestReset("unknown-" + UUID.randomUUID() + "@test.local")).isEqualTo(answer);
    confirm(token, "x").expectStatus().is4xxClientError();
    assertThat(resets.findByTokenHash(UserAccessToken.hashToken(token))).isPresent();
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
    confirm(token, NEW_PASSWORD).expectStatus().isOk();
  }

  @Test
  @DisplayName("Disabled accounts neither receive a token nor consume an existing proof")
  void disabledAccount() throws Exception {
    requestReset(user.getUsername());
    String token = mailedToken();
    user = users.findById(user.getIdUser()).orElseThrow();
    user.setEnabled(false);
    users.saveAndFlush(user);
    String answer = requestReset(user.getUsername());
    assertThat(requestReset("missing@test.local")).isEqualTo(answer);
    confirm(token, NEW_PASSWORD).expectStatus().is4xxClientError();
    assertThat(resets.findByTokenHash(UserAccessToken.hashToken(token))).isPresent();
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Two overlapping confirmations of one token produce exactly one successful password change")
  void parallelConfirmation() throws Exception {
    requestReset(user.getUsername());
    String token = mailedToken();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var jobs = IntStream.range(0, 2).mapToObj(_ -> executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return confirm(token, NEW_PASSWORD).expectBody().returnResult().getStatus().value();
      })).toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var statuses = List.of(jobs.get(0).get(20, TimeUnit.SECONDS), jobs.get(1).get(20, TimeUnit.SECONDS));
      assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
      assertThat(statuses.stream().filter(status -> status >= 400 && status < 500).count()).isEqualTo(1);
    }
    assertThat(users.findById(user.getIdUser()).orElseThrow().getAuthVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("A signed-in password change replaces the token, keeps a switched tenant and preserves bearer credentials")
  void signedInChangeKeepsTenant() throws Exception {
    home = createTenant("Reset home");
    other = createTenant("Reset other");
    user.setIdTenant(home);
    user = users.saveAndFlush(user);
    grants.save(new TenantAccess(user.getIdUser(), other, TenantAccessLevel.READ));
    addBearerCredentials();
    requestReset(user.getUsername());
    String pending = mailedToken();
    String old = jwtTokenHandler.createTokenForUser(user, 60, other);
    var result = own(old).put().uri("/api/user/password")
        .body(Map.of("passwordOld", PASSWORD, "passwordNew", NEW_PASSWORD)).exchange().expectStatus().isOk()
        .expectBody().returnResult();
    String replacement = result.getResponseHeaders().getFirst("x-auth-token");
    assertThat(replacement).isNotBlank();
    User parsed = (User) jwtTokenHandler.parseUserFromToken(replacement).orElseThrow();
    assertThat(parsed.getIdTenant()).isEqualTo(other);
    assertThat(users.findById(user.getIdUser()).orElseThrow().getIdTenant()).isEqualTo(home);
    own(replacement).get().uri("/api/user/nicknames?idUsers=" + user.getIdUser()).exchange().expectStatus().isOk();
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(old)).isInstanceOf(BadCredentialsException.class);
    assertThat(authorizations.findByIdUserOrderByCreatedAtDesc(user.getIdUser())).hasSize(1);
    assertThat(tokens.findByIdUserAndKind(user.getIdUser(), (byte) 1)).hasSize(1);
    confirm(pending, PASSWORD).expectStatus().is4xxClientError();
  }

  @Test
  @DisplayName("A rollback preserves the password, session, reset proof and bearer credentials and sends no notice")
  void rollbackAndStaleLoginSnapshot() throws Exception {
    requestReset(user.getUsername());
    String token = mailedToken();
    addBearerCredentials();
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      try {
        assertThat(credentials.resetPassword(token, NEW_PASSWORD)).isEqualTo(1);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      status.setRollbackOnly();
    });
    assertThat(users.findById(user.getIdUser()).orElseThrow().getAuthVersion()).isZero();
    assertThat(resets.findByTokenHash(UserAccessToken.hashToken(token))).isPresent();
    assertThat(authorizations.findByIdUserOrderByCreatedAtDesc(user.getIdUser())).hasSize(1);
    assertThat(tokens.findByIdUserAndKind(user.getIdUser(), (byte) 1)).hasSize(1);
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
    confirm(token, NEW_PASSWORD).expectStatus().isOk();
    // This represents a login whose password was checked against the pre-reset user snapshot.
    String lateLogin = jwtTokenHandler.createTokenForUser(user, 60);
    assertThatThrownBy(() -> jwtTokenHandler.parseUserFromToken(lateLogin)).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  @DisplayName("Demo accounts receive the same answer but no reset token")
  void demoAccount() {
    user.setUsername("reset-demo@test.local");
    user = users.saveAndFlush(user);
    assertThat(requestReset(user.getUsername())).isEqualTo(requestReset("missing@test.local"));
    assertThat(resets.findById(user.getIdUser())).isEmpty();
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  @DisplayName("Expired and unknown proofs count toward the address block, including both public reset endpoints")
  void expiredProofAndBlockedAddress() {
    resets.saveAndFlush(new UserPasswordReset(user.getIdUser(), UserAccessToken.hashToken("expired-proof"),
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(31)));
    for (int i = 0; i < 5; i++) {
      confirm(i == 0 ? "expired-proof" : "unknown-proof", NEW_PASSWORD).expectStatus().isBadRequest();
    }
    confirm("unknown-proof", NEW_PASSWORD).expectStatus().isUnauthorized().expectBody(String.class)
        .isEqualTo("login.ipaddress.locked");
    client.post().uri(RESET).body(Map.of("email", user.getUsername())).exchange().expectStatus().isUnauthorized();
    client.post().uri("/api/login").body(loginBody(PASSWORD)).exchange().expectStatus().isUnauthorized();
    assertThat(users.findById(user.getIdUser()).orElseThrow().getAuthVersion()).isZero();
  }

  @Test
  @DisplayName("The public request budget permits five requests and refuses the sixth without sending more mail")
  void requestBudget() throws Exception {
    for (int i = 0; i < 5; i++)
      requestReset(user.getUsername());
    mailedToken();
    client.post().uri(RESET).body(Map.of("email", user.getUsername())).exchange().expectStatus().isEqualTo(429);
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
  }

  private String requestReset(String email) {
    return client.post().uri(RESET).header("Referer", "https://evil.test/").body(Map.of("email", email)).exchange()
        .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
  }

  private String mailedToken() throws Exception {
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    var matcher = Pattern.compile("(?m)^[A-Za-z0-9_-]{43}\\r?$")
        .matcher(GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]));
    assertThat(matcher.find()).isTrue();
    return matcher.group().trim();
  }

  private RestTestClient.ResponseSpec confirm(String token, String password) {
    return client.post().uri(RESET + "/confirm").body(Map.of("token", token, "passwordNew", password)).exchange();
  }

  private Map<String, Object> loginBody(String password) {
    return Map.of("email", user.getUsername(), "password", password, "timezoneOffset", 60);
  }

  private String login(String password) {
    return client.post().uri("/api/login").body(loginBody(password)).exchange().expectStatus().isOk().expectBody()
        .returnResult().getResponseHeaders().getFirst("x-auth-token");
  }

  private RestTestClient own(String jwt) {
    return client.mutate().defaultHeader("x-auth-token", jwt).build();
  }

  private void addBearerCredentials() {
    var now = LocalDateTime.now(ZoneOffset.UTC);
    tokens.save(new UserAccessToken(user.getIdUser(), "reset-test",
        UserAccessToken.hashToken(UUID.randomUUID().toString()), "test", AccessTokenScope.READ, now, now.plusDays(1)));
    authorizations.save(new OAuthAuthorization(user.getIdUser(), "https://client.test/client.json", "reset-client",
        AccessTokenScope.READ, "https://instance.test/api/mcp", now));
  }

  private Integer createTenant(String name) {
    Tenant tenant = new Tenant();
    tenant.setTenantName(name);
    tenant.setCreateIdUser(user.getIdUser());
    return tenants.saveAndFlush(tenant).getIdTenant();
  }
}
