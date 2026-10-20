package grafiosch.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import grafiosch.service.LoginAttemptServiceIpAddress;

/**
 * A personal access token lives up to a year, so it must never travel in clear text. The rule is checked before the
 * token is even looked at, which is why the filter needs no token service here: a request that passes the rule ends in
 * 401 for its missing token, one that fails it in 403.
 *
 * <p>
 * The cases are the deployments of the installer and of Docker. The trusted proxies are loopback, as on every
 * installation, plus Caddy's fixed address in the Docker proxy network.
 * </p>
 */
class AccessTokenAuthenticationFilterTest {

  private final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(null,
      new LoginAttemptServiceIpAddress("127.0.0.1,::1,172.30.231.2"), () -> "Bearer", () -> null);

  @ParameterizedTest(name = "{0}")
  @CsvSource(nullValues = "-", value = {
      "Client on the backend machine without proxy,      127.0.0.1,    false, -,          401",
      "nginx or Apache on the same host over HTTPS,      127.0.0.1,    false, https,      401",
      "nginx on the same host over plain LAN HTTP,       127.0.0.1,    false, http,       403",
      "Docker Caddy with a domain (HTTPS),               172.30.231.2, false, https,      401",
      "Docker Caddy in local HTTP mode,                  172.30.231.2, false, http,       403",
      "Apache over AJP serving HTTPS,                    192.0.2.10,   true,  -,          401",
      "Untrusted peer with a forged header,              192.0.2.10,   false, https,      403",
      "Plain HTTP from the network,                      192.0.2.10,   false, -,          403",
      "Proxy chain with one unencrypted hop,             127.0.0.1,    false, 'https, http', 403" })
  void transportRule(String deployment, String remoteAddress, boolean secure, String forwardedProto, int expectedStatus)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
    request.setRemoteAddr(remoteAddress);
    request.setSecure(secure);
    if (forwardedProto != null) {
      request.addHeader("X-Forwarded-Proto", forwardedProto);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).as(deployment).isEqualTo(expectedStatus);
    assertThat(chain.getRequest()).as("never reaches the endpoint without a token").isNull();
  }
}
