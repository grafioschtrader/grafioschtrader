package grafiosch.integration.rest;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public fail-fast metadata used only to guard the portable E2E suite. */
@RestController
public class IntegrationInfoResource {

  private final Environment environment;
  private final JdbcTemplate jdbcTemplate;

  public IntegrationInfoResource(Environment environment, JdbcTemplate jdbcTemplate) {
    this.environment = environment;
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping("/api/integration-info")
  public IntegrationInfo getInfo() {
    String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    return new IntegrationInfo(Arrays.asList(environment.getActiveProfiles()), databaseName);
  }

  public record IntegrationInfo(java.util.List<String> activeProfiles, String databaseName) {
  }
}
