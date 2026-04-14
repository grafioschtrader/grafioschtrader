package grafioschtrader.rest;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import grafiosch.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

/**
 * Base class for integration tests providing common test infrastructure.
 * Configures Spring Boot test environment with random port, test profile, and Flyway migrations.
 * Provides {@link RestTestClient} auto-wired against the local test server for HTTP testing.
 */
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/test",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.clean-disabled=false"
})
public abstract class BaseIntegrationTest {

  /**
   * In-process SMTP server (port 3025) accepting verification mails sent during tests.
   * Started once per test class so the user-registration flow does not depend on an
   * externally running MailHog/MailPit instance.
   */
  @RegisterExtension
  protected static GreenMailExtension greenMail =
      new GreenMailExtension(ServerSetupTest.SMTP)
          .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
          .withPerMethodLifecycle(false);

  @Autowired
  protected RestTestClient restTestClient;

  @LocalServerPort
  protected int port;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  /**
   * Creates a RestTestClient instance with JWT authentication header pre-configured.
   * The returned client can be used to build any HTTP request (GET, POST, PUT, DELETE, etc.).
   *
   * @param nickname the user nickname to authenticate as (e.g., "admin", "user", "limit1")
   * @return RestTestClient configured with authentication token
   */
  protected RestTestClient authenticatedClient(String nickname) {
    String token = RestTestHelper.getUserByNickname(nickname).authToken;
    return restTestClient.mutate()
        .defaultHeader("x-auth-token", token)
        .build();
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
