package grafioschtrader.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import grafiosch.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

/**
 * Base class for integration tests providing common test infrastructure.
 * Configures Spring Boot test environment with random port, test profile, and Flyway migrations.
 * Provides both TestRestTemplate (legacy) and WebTestClient for HTTP testing.
 */
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/test",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.clean-disabled=false"
})
public abstract class BaseIntegrationTest {

  @Autowired
  protected TestRestTemplate restTemplate = new TestRestTemplate();

  @Autowired
  protected WebTestClient webTestClient;

  @LocalServerPort
  protected int port;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  /**
   * Creates a WebTestClient instance with JWT authentication header pre-configured.
   * The returned client can be used to build any HTTP request (GET, POST, PUT, DELETE, etc.).
   *
   * @param nickname the user nickname to authenticate as (e.g., "admin", "user", "limit1")
   * @return WebTestClient configured with authentication token
   */
  protected WebTestClient authenticatedClient(String nickname) {
    String token = RestTestHelper.getUserByNickname(nickname).authToken;
    return webTestClient.mutate()
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