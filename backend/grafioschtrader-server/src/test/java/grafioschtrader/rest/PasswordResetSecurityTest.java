package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import grafiosch.GlobalParamKeyBaseDefault;
import grafiosch.entities.Globalparameters;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.mcp.McpTestSwitch;
import jakarta.persistence.EntityManagerFactory;

/** Application security-chain checks without fixture logins or changes to portfolio data. */
class PasswordResetSecurityTest extends BaseIntegrationTest {
  @Autowired
  private LoginAttemptServiceIpAddress attempts;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private EntityManagerFactory emf;

  @Test
  @DisplayName("Both public reset endpoints honor the same address block as the GT password login")
  void publicPathsHonorAddressBlock() {
    var client = restTestClient.mutate().defaultHeader("X-Forwarded-For", "198.51.100.240").build();
    client.post().uri("/api/user/passwordreset").body(Map.of("email", "missing-reset@test.invalid")).exchange()
        .expectStatus().isOk();
    var address = new MockHttpServletRequest();
    address.setRemoteAddr("198.51.100.240");
    try {
      for (int i = 0; i < 5; i++)
        attempts.loginFailed(address);
      client.post().uri("/api/user/passwordreset").body(Map.of("email", "missing-reset@test.invalid")).exchange()
          .expectStatus().isUnauthorized().expectBody(String.class).isEqualTo("login.ipaddress.locked");
      client.post().uri("/api/user/passwordreset/confirm").body(Map.of("token", "invalid", "passwordNew", "A123abcd"))
          .exchange().expectStatus().isUnauthorized();
      client.post().uri("/api/login").body(Map.of("email", "missing-reset@test.invalid", "password", "A123abcd"))
          .exchange().expectStatus().isUnauthorized();
    } finally {
      attempts.loginSucceeded(address);
    }
  }

  @Test
  @DisplayName("MCP OAuth discovery uses the renamed library parameter with its reverse-proxy prefix")
  void oauthPublicAddress() {
    int originalUse = jdbc.queryForObject("SELECT property_int FROM globalparameters WHERE property_name = ?",
        Integer.class, GlobalParamKeyDefault.GLOB_KEY_MCP_USE);
    String originalUrl = jdbc.queryForObject("SELECT property_string FROM globalparameters WHERE property_name = ?",
        String.class, GlobalParamKeyBaseDefault.GLOB_KEY_PUBLIC_BASE_URL);
    try {
      McpTestSwitch.open(jdbc, emf, "https://host.example/gt/");
      String metadata = restTestClient.get().uri("/.well-known/oauth-authorization-server/gt").exchange().expectStatus()
          .isOk().expectBody(String.class).returnResult().getResponseBody();
      assertThat(metadata).contains("\"issuer\":\"https://host.example/gt\"",
          "https://host.example/gt/api/oauth/token");
      restTestClient.get().uri("/.well-known/oauth-protected-resource/gt/api/mcp").exchange().expectStatus().isOk();
    } finally {
      McpTestSwitch.set(jdbc, emf, originalUse, originalUrl);
      emf.getCache().evict(Globalparameters.class);
    }
  }
}
