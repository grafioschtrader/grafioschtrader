package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.RequestMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public, unauthenticated endpoint that exposes which Spring profile is active and which database the backend is
 * connected to. Its sole purpose is to let automated tooling — primarily the Playwright E2E globalSetup — refuse
 * to run against the wrong environment (e.g. the production database) before any test launches a browser.
 *
 * The response intentionally carries only non-sensitive identifiers. The JDBC URL itself is never returned; only
 * the final path segment (database name) is extracted. Hosts, ports, credentials and other datasource details are
 * not exposed.
 */
@RestController
@RequestMapping(RequestMappings.API + "gtinfo")
@Tag(name = "gtinfo", description = "Public diagnostic endpoint returning the active Spring profile and the "
    + "database name. Used by the E2E globalSetup to verify that the backend is connected to grafioschtrader_t.")
public class GTInfoResource {

  private final Environment environment;
  private final String datasourceUrl;

  public GTInfoResource(Environment environment, @Value("${spring.datasource.url:}") String datasourceUrl) {
    this.environment = environment;
    this.datasourceUrl = datasourceUrl;
  }

  @Operation(summary = "Get active profile and database name", description = "Returns the comma-separated list "
      + "of active Spring profiles and the database name parsed from the configured JDBC URL.")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<GTInfoDTO> getInfo() {
    String profile = String.join(",", environment.getActiveProfiles());
    return new ResponseEntity<>(new GTInfoDTO(profile, extractDatabaseName(datasourceUrl)), HttpStatus.OK);
  }

  /**
   * Extracts the database name from a JDBC URL by taking the substring after the last '/' and dropping any
   * query-string portion. Returns an empty string when the URL is null, empty, or has no path segment.
   *
   * @param url the configured 'spring.datasource.url', possibly null or empty
   * @return the database name, or an empty string if it cannot be determined
   */
  static String extractDatabaseName(String url) {
    if (url == null || url.isEmpty()) {
      return "";
    }
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash < 0 || lastSlash == url.length() - 1) {
      return "";
    }
    String tail = url.substring(lastSlash + 1);
    int q = tail.indexOf('?');
    return q < 0 ? tail : tail.substring(0, q);
  }
}
