package grafioschtrader.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import grafiosch.security.JwtTokenHandler;
import grafioschtrader.test.start.GTforTest;

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

  @LocalServerPort
  protected int port;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

}