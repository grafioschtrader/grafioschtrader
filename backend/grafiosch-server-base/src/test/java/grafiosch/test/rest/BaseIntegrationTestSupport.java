package grafiosch.test.rest;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import grafiosch.security.JwtTokenHandler;

/**
 * Application independent infrastructure for HTTP integration tests: an in-process SMTP server, the auto-configured
 * {@link RestTestClient}, the random server port and the helper that turns a fixture nickname into an authenticated
 * client.
 *
 * <p>
 * This class carries deliberately <b>no</b> {@code @SpringBootTest} / {@code @ActiveProfiles} /
 * {@code @TestPropertySource} annotations: the Spring Boot application class and the profile are application specific.
 * Each application declares its own {@code BaseIntegrationTest} that extends this class and adds those annotations;
 * the concrete resource tests then extend that one. JUnit picks the static extension field up through the hierarchy.
 */
public abstract class BaseIntegrationTestSupport {

  /**
   * In-process SMTP server (port 3025) accepting verification mails sent during tests. Started once per test class so
   * the user-registration flow does not depend on an externally running MailHog/MailPit instance.
   */
  @RegisterExtension
  protected static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
      .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication()).withPerMethodLifecycle(false);

  @Autowired
  protected RestTestClient restTestClient;

  @LocalServerPort
  protected int port;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  /**
   * Creates a RestTestClient instance with JWT authentication header pre-configured. The returned client can be used to
   * build any HTTP request (GET, POST, PUT, DELETE, etc.).
   *
   * @param nickname the user nickname to authenticate as (e.g., "admin", "user", "limit1")
   * @return RestTestClient configured with authentication token
   */
  protected RestTestClient authenticatedClient(String nickname) {
    String token = RestTestHelperBase.getUserByNickname(nickname).authToken;
    return restTestClient.mutate().defaultHeader("x-auth-token", token).build();
  }

  /**
   * Creates base URL for API endpoints using the dynamically assigned test port.
   *
   * @param path the API path (e.g., "/api/stockexchange")
   * @return complete URL string with localhost and port
   */
  protected String getBaseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
